package set

import spinal.core._
import spinal.lib.bus.simple.PipelinedMemoryBus
import spinal.lib.bus.simple.PipelinedMemoryBusConfig
import spinal.lib.slave
import spinal.lib.MuxOH
import vexriscv.plugin.Plugin
import vexriscv.VexRiscv

object SetPerfUnit {
  // 64-bit
  val counters: Seq[String] = Seq(
    "setinter-instexec",
    "setdiff-instexec"
  )

  val addrLen = log2Up(0x2000)
}

trait SetPerfUnitService {
  def trigger(name: String, data: UInt = U(1))
}

/**
  * Performance Unit
  * 
  * Memory mapping (Starting from base)
  * base + 0x0: RW, write 0 to stop, write any other value to start. Reading will get 0 or 1
  * base + 0x8: RW, write anything to clear PMU, any read will get 0
  * 
  * base + 0x1000: RW, First counter
  * base + 0x1008: RW, Second counter
  * ...
  * 
  * All read write will be aligned to 4-byte boundary. Any read/write to other memory location will result in undefined behavior
  * All write masks are **ignored**
  * 
  * Memory region size = 0x2000
  *
  */
class SetPerfUnitPlugin(base: BigInt, membusCfg: PipelinedMemoryBusConfig) extends SetPerfUnitService with Plugin[VexRiscv] {
  var bus: PipelinedMemoryBus = null

  var storage: Map[String, UInt] = null
  var incremented: Map[String, UInt] = null

  // Dummy build
  override def build(pipeline: VexRiscv): Unit = {
    bus = slave(PipelinedMemoryBus(membusCfg))

    val enabled = RegInit(False)
    val clear = False

    // Create all mapping
    storage = SetPerfUnit.counters.map((counter) => counter -> RegInit(U(0, 64 bits)).setName(counter)).toMap
    incremented = storage.map({ case (n, s) => n -> UInt(64 bits).setName(s"${n}+incremented") })
    val written: Map[String, UInt] = incremented.map({ case (n, i) => n -> UInt(64 bits).setName(s"${n}+written") })

    for(n <- SetPerfUnit.counters) {
      incremented(n) := storage(n)
      written(n) := Mux(enabled, incremented(n), storage(n))
    }
    val addr = bus.cmd.payload.address.take(SetPerfUnit.addrLen)
    val sel = addr.drop(3)
    val hi = addr(2)

    // Handling read
    bus.rsp.valid := RegNext(bus.cmd.valid && !bus.cmd.payload.write)
    bus.cmd.ready := True
    val selected = sel.muxList(U(0, 64 bits), Seq(
      0 -> Mux(enabled, U(1, 64 bits), U(0, 64 bits))
    ) ++ storage.zipWithIndex.map({ case ((name, value), idx) => (
      ((0x1000 >> 3) + idx) -> value
    )}))
    bus.rsp.payload.data := RegNext(Mux(hi, selected.drop(32), selected.take(32)))

    // Handling write
    when(sel === 0 && bus.cmd.payload.write) {
      enabled := bus.cmd.payload.data =/= 0
    }

    when(sel === 1 && bus.cmd.payload.write) {
      clear := True
    }

    for(((name, cnt), idx) <- written.zipWithIndex) {
      when(clear) {
        cnt := 0
      }.elsewhen(sel === ((0x1000 >> 3) + idx) && bus.cmd.payload.write) {
        when(hi) {
          cnt := U(bus.cmd.payload.data.take(32) ## incremented(name).take(32))
        }.otherwise {
          cnt := U(incremented(name).drop(32) ## bus.cmd.payload.data.take(32))
        }
      }
    }

    // Commit write
    for(c <- SetPerfUnit.counters) {
      storage(c) := written(c)
    }
  }

  // Attach trigger
  def trigger(name: String, data: UInt = U(1)) = {
    incremented(name) := storage(name) + 1
  }
}