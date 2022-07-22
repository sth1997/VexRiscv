package set

import vexriscv._
import vexriscv.plugin.{DBusSimplePlugin, Plugin}
import spinal.core._

class SetCountPlugin extends Plugin[VexRiscv] {

  object IS_SETCOUNT extends Stageable(Bool)

  object State extends SpinalEnum {
    val IDLE, P, L, S, X_REQUESTED, C, F = newElement()
  }

  override def setup(pipeline: VexRiscv): Unit = {
    import pipeline.config._

    val decoderService = pipeline.service(classOf[DecoderService])
    decoderService.addDefault(IS_SETCOUNT, False)
    decoderService.add(
      key = M"0000010----------110-----0001011",
      List(
        IS_SETCOUNT -> True,
        REGFILE_WRITE_VALID -> True
      )
    )
  }

  override def build(pipeline: VexRiscv): Unit = {
    import pipeline._
    import pipeline.config._

    val dBus = pipeline.plugins.filter(_.isInstanceOf[DBusSimplePlugin]).head.asInstanceOf[DBusSimplePlugin].dBus

    execute.plug(new Area {

      import execute._

      val state = Reg(State).init(State.IDLE)

      when(state === State.IDLE) {
        when(input(IS_SETCOUNT)) {
          decode.arbitration.haltByOther := True
          execute.arbitration.haltItself := True
          state := State.P
        }
      }

      when(state =/= State.IDLE) {
        decode.arbitration.haltByOther := True
        execute.arbitration.haltItself := True
      }

      when(state === State.P) {
        when(!memory.arbitration.isValid && !writeBack.arbitration.isValid) {
          state := State.L
        }
      }

      val addr = Reg(UInt(32 bits))

      when(state === State.L) {
        addr := input(RS1_E).asUInt
        state := State.S
      }

      when(state === State.S) {
        dBus.cmd.valid := True
        dBus.cmd.wr := False
        dBus.cmd.address := addr

        state := State.X_REQUESTED
      }

      val xVal = Reg(SInt(32 bits))

      when(state === State.X_REQUESTED) {
        dBus.cmd.valid := False
        when(dBus.rsp.ready) {
          xVal := dBus.rsp.data.asSInt
          state := State.C
        }
      }

      val cnt = Reg(UInt(32 bits)).init(0)

      when(state === State.C) {
        when(xVal === -1) {
          state := State.F
        } otherwise {
          cnt := cnt + 1
          addr := addr + 4
          state := State.S
        }
      }

      when(state === State.F) {
        insert(REGFILE_WRITE_DATA) := cnt.asBits
        state := State.IDLE
        decode.arbitration.haltByOther := False
        execute.arbitration.haltItself := False
      }
    })
  }
}
