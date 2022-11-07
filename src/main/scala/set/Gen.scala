package set

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.amba4.axi._
import spinal.lib.misc.HexTools
import vexriscv._
import vexriscv.plugin._
import spinal.core
import spinal.lib.com.uart.sim.{UartDecoder, UartEncoder}
import spinal.lib.com.uart.{
  Apb3UartCtrl,
  Uart,
  UartCtrlGenerics,
  UartCtrlMemoryMappedConfig
}
import spinal.lib.bus.simple.PipelinedMemoryBus

class SetChip extends Component {
  val io = new Bundle {
    val asyncReset = in Bool ()
    val mainClk = in Bool ()
    val uart = master(Uart())
  }

  val resetCtrlClockDomain = ClockDomain(
    clock = io.mainClk,
    config = ClockDomainConfig(
      resetKind = BOOT
    )
  )

  val resetCtrl = new ClockingArea(resetCtrlClockDomain) {
    val systemResetUnbuffered = False
    //    val coreResetUnbuffered = False

    // Implement an counter to keep the reset axiResetOrder high 64 cycles
    // Also this counter will automaticly do a reset when the system boot.
    val systemResetCounter = Reg(UInt(6 bits)) init (0)
    when(systemResetCounter =/= U(systemResetCounter.range -> true)) {
      systemResetCounter := systemResetCounter + 1
      systemResetUnbuffered := True
    }
    when(BufferCC(io.asyncReset)) {
      systemResetCounter := 0
    }

    // Create all reset used later in the design
    val systemReset = RegNext(systemResetUnbuffered)
    val axiReset = RegNext(systemResetUnbuffered)
  }

  val axiClockDomain = ClockDomain(
    clock = io.mainClk,
    reset = resetCtrl.axiReset,
    frequency = FixedFrequency(50 MHz)
  )

  val systemClockDomain = ClockDomain(
    clock = io.mainClk,
    reset = resetCtrl.systemReset,
    frequency = FixedFrequency(50 MHz)
  )

  val soc = new ClockingArea(axiClockDomain) {
    val cpu = new VexRiscv(
      config = VexRiscvConfig(
        plugins = List(
          new StaticMemoryTranslatorPlugin(
            ioRange = _(31 to 28) === 0xF
          ),
          new IBusSimplePlugin(
            resetVector = 0x80000000L,
            cmdForkOnSecondStage = false,
            cmdForkPersistence = true,
            prediction = NONE,
            catchAccessFault = false,
            compressedGen = false
          ),
          new DBusSimplePlugin(
            catchAddressMisaligned = false,
            catchAccessFault = false
          ),
          new CsrPlugin(CsrPluginConfig.smallest),
          new DecoderSimplePlugin(
            catchIllegalInstruction = false
          ),
          new RegFilePlugin(
            regFileReadyKind = plugin.SYNC,
            zeroBoot = false
          ),
          new IntAluPlugin,
          new SrcPlugin(
            separatedAddSub = false,
            executeInsertion = false
          ),
          new LightShifterPlugin,
          new HazardSimplePlugin(
            bypassExecute = false,
            bypassMemory = false,
            bypassWriteBack = false,
            bypassWriteBackBuffer = false,
            pessimisticUseSrc = false,
            pessimisticWriteRegFile = false,
            pessimisticAddressMatch = false
          ),
          new BranchPlugin(
            earlyBranch = false,
            catchAddressMisaligned = false
          ),
          new SetPerfUnitPlugin(0xF1000000),
          new SetInstPlugin(size = 16),
          new YamlPlugin("cpu0.yaml")
        )
      )
    )

    // val timerInterrupt = False
    // val externalInterrupt = False
    // for (plugin <- cpu.plugins) plugin match {
    //   case plugin: CsrPlugin =>
    //     plugin.externalInterrupt := externalInterrupt
    //     plugin.timerInterrupt := timerInterrupt
    //   case _ =>
    // }

    val apbBridge = Axi4SharedToApb3Bridge(
      addressWidth = 28, // Drop most significant half (always F)
      dataWidth = 32,
      idWidth = 4
    )
    val uartCtrl = Apb3UartCtrl(
      UartCtrlMemoryMappedConfig(
        uartCtrlConfig = UartCtrlGenerics(),
        txFifoDepth = 32,
        rxFifoDepth = 32
      )
    )
    uartCtrl.io.apb.addAttribute(Verilator.public)

    var iBus: Axi4ReadOnly = null
    var dBus: Axi4Shared = null
    var sBus: Axi4Shared = null
    for (plugin <- cpu.plugins) plugin match {
      case plugin: IBusSimplePlugin => iBus = plugin.iBus.toAxi4ReadOnly()
      case plugin: DBusSimplePlugin => dBus = plugin.dBus.toAxi4Shared()
      case plugin: SetInstPlugin    => sBus = plugin.sBus
      case plugin: CsrPlugin  => {
        plugin.timerInterrupt := False
        plugin.externalInterrupt := False
      }
      case _                        =>
    }

    val ram = Mem(Bits(32 bits), 1 kB)
    HexTools.initRam(ram, "sim/main.hex", 0x80000000L)
    val ramConfig = Axi4Config(32, 32, 2)
    val ramAxi = Axi4SharedOnChipRamPort(ramConfig, ram)

    val axiCrossbar = Axi4CrossbarFactory()
    axiCrossbar.addSlaves(
      ramAxi.axi -> (0x80000000L, 4 kB),
      apbBridge.io.axi -> (0xf0000000L, 256 MB) // 0xF0000000 - 0xFFFFFFFF
    )
    axiCrossbar.addConnections(
      iBus -> List(ramAxi.axi),
      dBus -> List(ramAxi.axi, apbBridge.io.axi),
      sBus -> List(ramAxi.axi)
    )
    axiCrossbar.addPipelining(apbBridge.io.axi)((crossbar, bridge) => {
      crossbar.sharedCmd.halfPipe() >> bridge.sharedCmd
      crossbar.writeData.halfPipe() >> bridge.writeData
      crossbar.writeRsp << bridge.writeRsp
      crossbar.readRsp << bridge.readRsp
    })
    axiCrossbar.addPipelining(ramAxi.axi)((crossbar, ctrl) => {
      crossbar.sharedCmd.halfPipe() >> ctrl.sharedCmd
      crossbar.writeData >/-> ctrl.writeData
      crossbar.writeRsp << ctrl.writeRsp
      crossbar.readRsp << ctrl.readRsp
    })
    axiCrossbar.addPipelining(dBus)((cpu, crossbar) => {
      cpu.sharedCmd >> crossbar.sharedCmd
      cpu.writeData >> crossbar.writeData
      cpu.writeRsp << crossbar.writeRsp
      cpu.readRsp <-< crossbar.readRsp // Data cache directly use read responses without buffering, so pipeline it for FMax
    })
    axiCrossbar.build()

    val puBus: Apb3 = cpu.plugins.collect({ case e: SetPerfUnitPlugin => e }).head.bus
    val apbDecoder = Apb3Decoder(
      master = apbBridge.io.apb,
      slaves = List(
        uartCtrl.io.apb -> (0x00000, 4 kB),
        puBus -> (0x1000000, 0x2000.toBigInt)
      )
    )
  }

  io.uart <> soc.uartCtrl.io.uart
}

object Gen extends App {
  SpinalVerilog(new SetChip)
}

object SetSimulation extends App {
  SimConfig.allOptimisation.withVcdWave.compile(new SetChip).doSimUntilVoid { dut =>
    {
      val mainClkPeriod = (1e12/(50 MHz).toDouble).toLong
      val uartBaudRate = 115200
      val uartBaudPeriod = (1e12/uartBaudRate).toLong

      val clockConfig = ClockDomainConfig(resetKind = core.SYNC)
      ClockDomain(dut.io.mainClk, dut.io.asyncReset, config = clockConfig)
        .forkStimulus(mainClkPeriod)

      val uartTx = UartDecoder(
        uartPin = dut.io.uart.txd,
        baudPeriod = uartBaudPeriod
      )
      val uartRx = UartEncoder(
        uartPin = dut.io.uart.rxd,
        baudPeriod = uartBaudPeriod
      )
    }
  }
}
