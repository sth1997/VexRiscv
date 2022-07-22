package set

import spinal.core._
import vexriscv._
import vexriscv.plugin.{DBusSimplePlugin, Plugin}

class SetDiffPlugin extends Plugin[VexRiscv] {

  object IS_SETDIFF extends Stageable(Bool)

  object State extends SpinalEnum {
    val IDLE, P, L, S, X_REQUESTED, X_LOADED, Y_REQUESTED, Y_LOADED, C_X, C_Y, C, W, W_REQUESTED, R, RR, RRR, F_W, F = newElement()
  }

  override def setup(pipeline: VexRiscv): Unit = {

    val decoderService = pipeline.service(classOf[DecoderService])
    decoderService.addDefault(IS_SETDIFF, False)
    decoderService.add(
      key = M"0000001----------111-----0001011",
      List(
        IS_SETDIFF -> True
      )
    )
  }

  def build(pipeline: VexRiscv): Unit = {
    import pipeline._
    import pipeline.config._

    val dBus = pipeline.plugins.filter(_.isInstanceOf[DBusSimplePlugin]).head.asInstanceOf[DBusSimplePlugin].dBus

    execute plug new Area {

      import execute._

      val state = Reg(State)

      val addrSrc1 = Reg(UInt(32 bits))
      val addrSrc2 = Reg(UInt(32 bits))
      val addrDest = Reg(UInt(32 bits))

      when(input(IS_SETDIFF)) {
        when(state === State.IDLE) {
          state := State.P
          decode.arbitration.haltByOther := True
          execute.arbitration.haltItself := True
        }
      }

      when(state === State.P) {
        when(!memory.arbitration.isValid && !writeBack.arbitration.isValid) {
          state := State.L
        }
      }

      when(state === State.L) {
        addrSrc1 := input(RS1_E).asUInt
        addrSrc2 := input(RS2_E).asUInt
        addrDest := input(RD_E).asUInt

        state := State.S
      }

      when(state =/= State.IDLE) {
        decode.arbitration.haltByOther := True
        execute.arbitration.haltItself := True
      }

      val xVal = Reg(SInt(32 bits))
      val yVal = Reg(SInt(32 bits))

      val dBusCmdValid = RegInit(False)
      val dBusCmdAddr = Reg(UInt(32 bits))
      val dBusCmdWR = Reg(Bool)
      val dBusCmdData = Reg(Bits(32 bits))

      when(state =/= State.IDLE) {
        dBus.cmd.valid := dBusCmdValid
        dBus.cmd.wr := dBusCmdWR
        dBus.cmd.address := dBusCmdAddr
        dBus.cmd.data := dBusCmdData
      }

      when(state === State.S) {
        dBusCmdValid := True
        dBusCmdWR := False
        dBusCmdAddr := addrSrc1

        state := State.X_REQUESTED
      }

      when(state === State.X_REQUESTED) {
        dBusCmdValid := False

        when(dBus.rsp.ready) {
          xVal := dBus.rsp.data.asSInt

          state := State.X_LOADED
        }
      }

      when(state === State.X_LOADED) {
        dBusCmdValid := True
        dBusCmdWR := False
        dBusCmdAddr := addrSrc2

        state := State.Y_REQUESTED
      }

      when(state === State.Y_REQUESTED) {
        dBusCmdValid := False

        when(dBus.rsp.ready) {
          yVal := dBus.rsp.data.asSInt

          state := State.Y_LOADED
        }
      }

      when(state === State.Y_LOADED) {
        when((xVal === -1) || (yVal === -1)) {
          state := State.F_W
        } otherwise {
          state := State.C_X
        }
      }

      when(state === State.C_X) {
        when(xVal === -1) {
          state := State.F_W
        } otherwise {
          state := State.C_Y
        }
      }

      when(state === State.C_Y) {
        when(yVal === -1) {
          state := State.R
        } otherwise {
          state := State.C
        }
      }

      when(state === State.C) {
        when(xVal === yVal) {
          addrSrc1 := addrSrc1 + 4
          addrSrc2 := addrSrc2 + 4
          state := State.S
        }

        when(xVal < yVal) {
          state := State.W
        }

        when(xVal > yVal) {
          addrSrc2 := addrSrc2 + 4
          state := State.S
        }
      }

      when(state === State.W) {
        dBusCmdValid := True
        dBusCmdWR := True
        dBusCmdAddr := addrDest
        dBusCmdData := xVal.asBits

        state := State.W_REQUESTED
      }

      when(state === State.W_REQUESTED) {
        dBusCmdValid := False

        when(dBus.cmd.ready) {
          addrSrc1 := addrSrc1 + 4
          addrDest := addrDest + 4

          state := State.S
        }
      }

      when(state === State.R) {
        when(xVal =/= -1) {
          dBusCmdValid := True
          dBusCmdWR := True
          dBusCmdAddr := addrDest
          dBusCmdData := xVal.asBits

          state := State.RR
        } otherwise {
          state := State.F_W
        }
      }

      when(state === State.RR) {
        dBusCmdValid := False
        when(dBus.cmd.ready) {
          dBusCmdValid := True
          dBusCmdWR := False
          dBusCmdAddr := addrSrc1

          state := State.RRR
        }
      }

      when (state === State.RRR) {
        dBusCmdValid := False
        when (dBus.rsp.ready) {
          xVal := dBus.rsp.data.asSInt
          addrSrc1 := addrSrc1 + 4
          state := State.R
        }
      }

      when(state === State.F_W) {
        dBusCmdValid := True
        dBusCmdWR := True
        dBusCmdAddr := addrDest
        dBusCmdData := S(-1, 32 bits).asBits

        state := State.F
      }

      when(state === State.F) {
        dBusCmdValid := False

        when(dBus.cmd.ready) {
          state := State.IDLE
          decode.arbitration.haltByOther := False
          execute.arbitration.haltItself := False
        }
      }

    }
  }
}

