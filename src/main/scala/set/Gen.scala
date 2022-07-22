package set

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.bus.simple.{PipelinedMemoryBus, PipelinedMemoryBusConfig}
import spinal.lib.misc.HexTools
import vexriscv._
import vexriscv.plugin._

import scala.collection.mutable.ArrayBuffer

class SetChipMasterArbiter(pipelinedMemoryBusConfig : PipelinedMemoryBusConfig) extends Component {
  val io = new Bundle {
    val iBus = slave(IBusSimpleBus(null))
    val dBus = slave(DBusSimpleBus(false))
    val masterBus = master(PipelinedMemoryBus(pipelinedMemoryBusConfig))
  }

  io.masterBus.cmd.valid := io.iBus.cmd.valid || io.dBus.cmd.valid
  io.masterBus.cmd.write := io.dBus.cmd.valid && io.dBus.cmd.wr
  io.masterBus.cmd.address := io.dBus.cmd.valid ? io.dBus.cmd.address | io.iBus.cmd.pc
  io.masterBus.cmd.data := io.dBus.cmd.data
  io.masterBus.cmd.mask := io.dBus.genMask(io.dBus.cmd)
  io.iBus.cmd.ready := io.masterBus.cmd.ready && !io.dBus.cmd.valid
  io.dBus.cmd.ready := io.masterBus.cmd.ready

  val rspPending = RegInit(False) clearWhen(io.masterBus.rsp.valid)
  val rspTarget = RegInit(False)
  when (io.masterBus.cmd.fire && !io.masterBus.cmd.write) {
    rspTarget := io.dBus.cmd.valid
    rspPending := True
  }

  when (rspPending && !io.masterBus.rsp.valid) {
    io.iBus.cmd.ready := False
    io.dBus.cmd.ready := False
    io.masterBus.cmd.valid := False
  }

  io.iBus.rsp.valid := io.masterBus.rsp.valid && !rspTarget
  io.iBus.rsp.inst  := io.masterBus.rsp.data
  io.iBus.rsp.error := False

  io.dBus.rsp.ready := io.masterBus.rsp.valid && rspTarget
  io.dBus.rsp.data  := io.masterBus.rsp.data
  io.dBus.rsp.error := False
}

case class SetChipPipelinedMemoryBusRam(onChipRamSize : BigInt, onChipRamHexFile : String, pipelinedMemoryBusConfig : PipelinedMemoryBusConfig) extends Component{
  val io = new Bundle{
    val bus = slave(PipelinedMemoryBus(pipelinedMemoryBusConfig))
  }

  val ram = Mem(Bits(32 bits), onChipRamSize / 4)
  io.bus.rsp.valid := RegNext(io.bus.cmd.fire && !io.bus.cmd.write) init(False)
  io.bus.rsp.data := ram.readWriteSync(
    address = (io.bus.cmd.address >> 2).resized,
    data  = io.bus.cmd.data,
    enable  = io.bus.cmd.valid,
    write  = io.bus.cmd.write,
    mask  = io.bus.cmd.mask
  )
  io.bus.cmd.ready := True

  if(onChipRamHexFile != null){
    HexTools.initRam(ram, onChipRamHexFile, 0x80000000l)
  }
}

class SetChipPipelinedMemoryBusDecoder(master : PipelinedMemoryBus, val specification : Seq[(PipelinedMemoryBus,SizeMapping)], pipelineMaster : Boolean) extends Area{
  val masterPipelined = PipelinedMemoryBus(master.config)
  if(!pipelineMaster) {
    masterPipelined.cmd << master.cmd
    masterPipelined.rsp >> master.rsp
  } else {
    masterPipelined.cmd <-< master.cmd
    masterPipelined.rsp >> master.rsp
  }

  val slaveBuses = specification.map(_._1)
  val memorySpaces = specification.map(_._2)

  val hits = for((slaveBus, memorySpace) <- specification) yield {
    val hit = memorySpace.hit(masterPipelined.cmd.address)
    slaveBus.cmd.valid   := masterPipelined.cmd.valid && hit
    slaveBus.cmd.payload := masterPipelined.cmd.payload.resized
    hit
  }
  val noHit = !hits.orR
  masterPipelined.cmd.ready := (hits,slaveBuses).zipped.map(_ && _.cmd.ready).orR || noHit

  val rspPending  = RegInit(False) clearWhen(masterPipelined.rsp.valid) setWhen(masterPipelined.cmd.fire && !masterPipelined.cmd.write)
  val rspNoHit    = RegNext(False) init(False) setWhen(noHit)
  val rspSourceId = RegNextWhen(OHToUInt(hits), masterPipelined.cmd.fire)
  masterPipelined.rsp.valid   := slaveBuses.map(_.rsp.valid).orR || (rspPending && rspNoHit)
  masterPipelined.rsp.payload := slaveBuses.map(_.rsp.payload).read(rspSourceId)

  when(rspPending && !masterPipelined.rsp.valid) { //Only one pending read request is allowed
    masterPipelined.cmd.ready := False
    slaveBuses.foreach(_.cmd.valid := False)
  }
}

class SetChip extends Component {
  val io = new Bundle {
    val asyncReset = in Bool()
    val mainClk = in Bool()
  }

  val resetCtrlClockDomain = ClockDomain(
    clock = io.mainClk,
    config = ClockDomainConfig(
      resetKind = BOOT
    )
  )
  val resetCtrl = new ClockingArea(resetCtrlClockDomain) {
    val mainClkResetUnbuffered = False

    val systemClkResetCounter = Reg(UInt(6 bits)) init(0)
    when(systemClkResetCounter =/= U(systemClkResetCounter.range -> true)){
      systemClkResetCounter := systemClkResetCounter + 1
      mainClkResetUnbuffered := True
    }
    when(BufferCC(io.asyncReset)){
      systemClkResetCounter := 0
    }

    val mainClkReset = RegNext(mainClkResetUnbuffered)
    val systemReset  = RegNext(mainClkResetUnbuffered)
  }

  val systemClockDomain = ClockDomain(
    clock = io.mainClk,
    reset = resetCtrl.systemReset,
    frequency = FixedFrequency(50 MHz)
  )

  // val system = new ClockingArea(systemClockDomain) {
    val pipelinedMemoryBusConfig = PipelinedMemoryBusConfig(
      addressWidth = 32,
      dataWidth = 32
    )

    val mainBusArbiter = new SetChipMasterArbiter(pipelinedMemoryBusConfig)

    val cpu = new VexRiscv(
      config = VexRiscvConfig(
        plugins = List(
          new IBusSimplePlugin(
            resetVector = 0x80000000l,
            cmdForkOnSecondStage = false,
            cmdForkPersistence = false,
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
            bypassExecute           = false,
            bypassMemory            = false,
            bypassWriteBack         = false,
            bypassWriteBackBuffer   = false,
            pessimisticUseSrc       = false,
            pessimisticWriteRegFile = false,
            pessimisticAddressMatch = false
          ),
          new BranchPlugin(
            earlyBranch = false,
            catchAddressMisaligned = false
          ),
          new SetInterPlugin,
          new SetCountPlugin,
          new SetDiffPlugin,
          new YamlPlugin("cpu0.yaml")
      )
    ))

    for (plugin <- cpu.plugins) plugin match {
      case plugin: IBusSimplePlugin =>
        mainBusArbiter.io.iBus.cmd <> plugin.iBus.cmd
        mainBusArbiter.io.iBus.rsp <> plugin.iBus.rsp
      case plugin: DBusSimplePlugin =>
        mainBusArbiter.io.dBus.cmd << plugin.dBus.cmd.halfPipe()
        mainBusArbiter.io.dBus.rsp <> plugin.dBus.rsp
      case _ =>
    }

    // val timerInterrupt = False
    // val externalInterrupt = False
    // for (plugin <- cpu.plugins) plugin match {
    //   case plugin: CsrPlugin =>
    //     plugin.externalInterrupt := externalInterrupt
    //     plugin.timerInterrupt := timerInterrupt
    //   case _ =>
    // }

    val mainBusMapping = ArrayBuffer[(PipelinedMemoryBus, SizeMapping)]()
    val ram = new SetChipPipelinedMemoryBusRam(
      onChipRamSize = 4 kB,
      onChipRamHexFile = "sim/baz.hex",
      pipelinedMemoryBusConfig = pipelinedMemoryBusConfig
    )
    mainBusMapping += ram.io.bus -> (0x80000000l, 4 kB)

    val mainBusDecoder = new Area {
      val logic = new SetChipPipelinedMemoryBusDecoder(
        master = mainBusArbiter.io.masterBus,
        specification = mainBusMapping.toSeq,
        pipelineMaster = false
      )
    }
  // }
}


object Gen extends App {
  SpinalVerilog(new SetChip)
  // SpinalVerilog(new VexRiscv(
  //     config = VexRiscvConfig(
  //       plugins = List(
  //         new IBusSimplePlugin(
  //           resetVector = 0x00000000l,
  //           cmdForkOnSecondStage = false,
  //           cmdForkPersistence = false,
  //           prediction = NONE,
  //           catchAccessFault = false,
  //           compressedGen = false
  //         ),
  //         new DBusSimplePlugin(
  //           catchAddressMisaligned = false,
  //           catchAccessFault = false
  //         ),
  //         new CsrPlugin(CsrPluginConfig.smallest),
  //         new DecoderSimplePlugin(
  //           catchIllegalInstruction = false
  //         ),
  //         new RegFilePlugin(
  //           regFileReadyKind = plugin.SYNC,
  //           zeroBoot = false
  //         ),
  //         new IntAluPlugin,
  //         new SrcPlugin(
  //           separatedAddSub = false,
  //           executeInsertion = false
  //         ),
  //         new LightShifterPlugin,
  //         new HazardSimplePlugin(
  //           bypassExecute           = false,
  //           bypassMemory            = false,
  //           bypassWriteBack         = false,
  //           bypassWriteBackBuffer   = false,
  //           pessimisticUseSrc       = false,
  //           pessimisticWriteRegFile = false,
  //           pessimisticAddressMatch = false
  //         ),
  //         new BranchPlugin(
  //           earlyBranch = false,
  //           catchAddressMisaligned = false
  //         ),
  //         new SetInterPlugin,
  //         new YamlPlugin("cpu0.yaml")
  //     )
  // )))
}
