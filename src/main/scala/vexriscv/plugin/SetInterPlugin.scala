package vexriscv.plugin

import spinal.core._
import vexriscv._

class SetInterPlugin extends Plugin[VexRiscv] {

  object IS_SETINTER_D extends Stageable(Bool)
  object IS_SETINTER_E extends Stageable(Bool)

  object State extends SpinalEnum {
    val IDLE, P, L, S, X_REQUESTED, X_LOADED, Y_REQUESTED, Y_LOADED, C, W, W_REQUESTED, F_W, F  = newElement()
  }

  override def setup(pipeline: VexRiscv): Unit = {
    import pipeline.config._

    val decoderService = pipeline.service(classOf[DecoderService])
    decoderService.addDefault(IS_SETINTER_D, False)
    decoderService.addDefault(IS_SETINTER_E, False)
    decoderService.add(
      key = M"0000000----------111-----0001011",
      List(
        IS_SETINTER_D -> True
        // RS1_USE -> True,
        // RS2_USE -> True,
        // RD_USE -> True
      )
    )
  }

  def build(pipeline: VexRiscv): Unit = {
    import pipeline._
    import pipeline.config._

    val dBus = pipeline.plugins.filter(_.isInstanceOf[DBusSimplePlugin]).head.asInstanceOf[DBusSimplePlugin].dBus
    // val regFile = pipeline.plugins.filter(_.isInstanceOf[RegFilePlugin]).head.asInstanceOf[RegFilePlugin]

    // execute.arbitration.haltItself := True

    decode plug new Area {
      import decode._

      // val test = RegInit(False)
      // when (input(IS_SETINTER_D)) {
      //   test := True
      //   decode.arbitration.haltItself := True
      //   when (!execute.arbitration.isValid && !memory.arbitration.isValid && !writeBack.arbitration.isValid) {
      //     insert(IS_SETINTER_E) := True
      //     decode.arbitration.haltItself := False
      //     decode.arbitration.haltByOther := True
      //   }
      // }
    }

    execute plug new Area {
      import execute._

      val state = Reg(State)

      val addrSrc1 = Reg(UInt(32 bits))
      val addrSrc2 = Reg(UInt(32 bits))
      val addrDest = Reg(UInt(32 bits))

      when (input(IS_SETINTER_D)) {
      // when (input(IS_SETINTER_E)) {
        when (state === State.IDLE) {
          state := State.P
          decode.arbitration.haltByOther := True
          execute.arbitration.haltItself := True
        }
      }

      when (state === State.P) {
        when (!memory.arbitration.isValid && !writeBack.arbitration.isValid) {
          state := State.L
        }
      }

      when (state === State.L) {
        addrSrc1 := input(RS1_E).asUInt
        addrSrc2 := input(RS2_E).asUInt
        addrDest := input(RD_E).asUInt

        state := State.S
      }

      when (state =/= State.IDLE) {
        decode.arbitration.haltByOther := True
        execute.arbitration.haltItself := True
      }

      val xVal = Reg(SInt(32 bits))
      val yVal = Reg(SInt(32 bits))

      // val xReady = RegInit(False)
      // val yReady = RegInit(False)


      val dBusCmdValid = RegInit(False)
      val dBusCmdAddr = Reg(UInt(32 bits))
      val dBusCmdWR = Reg(Bool)
      val dBusCmdData = Reg(Bits(32 bits))
      

      when (state =/= State.IDLE) {
        dBus.cmd.valid := dBusCmdValid
        dBus.cmd.payload.wr := dBusCmdWR
        dBus.cmd.payload.address := dBusCmdAddr
        dBus.cmd.payload.data := dBusCmdData
      }

      when (state === State.S) {
        dBusCmdValid := True
        dBusCmdWR := False
        dBusCmdAddr := addrSrc1

        state := State.X_REQUESTED

      }

      when (state === State.X_REQUESTED) {
        dBusCmdValid := False

        when (dBus.rsp.ready) {
          xVal := dBus.rsp.data.asSInt

          state := State.X_LOADED
        }
      }

      when (state === State.X_LOADED) {
        dBusCmdValid := True
        dBusCmdWR := False
        dBusCmdAddr := addrSrc2

        state := State.Y_REQUESTED
      }

      when (state === State.Y_REQUESTED) {
        dBusCmdValid := False

        when (dBus.rsp.ready) {
          yVal := dBus.rsp.data.asSInt

          state := State.Y_LOADED
        }
      }

      when (state === State.Y_LOADED) {
        when ((xVal === -1) || (yVal === -1)) {
          state := State.F_W
        } otherwise {
          state := State.C
        }
      }

      when (state === State.C) {
        when (xVal === yVal) {
          state := State.W
        }

        when (xVal < yVal) {
          addrSrc1 := addrSrc1 + 4
          state := State.S
        }

        when (xVal > yVal) {
          addrSrc2 := addrSrc2 + 4
          state := State.S
        }
      }

      when (state === State.W) {
        dBusCmdValid := True
        dBusCmdWR := True
        dBusCmdAddr := addrDest
        dBusCmdData := xVal.asBits

        state := State.W_REQUESTED
      }

      when (state === State.W_REQUESTED) {
        dBusCmdValid := False

        when (dBus.cmd.ready) {
          addrSrc1 := addrSrc1 + 4
          addrSrc2 := addrSrc2 + 4
          addrDest := addrDest + 4

          state := State.S
        }
      }

      when (state === State.F_W) {
        dBusCmdValid := True
        dBusCmdWR := True
        dBusCmdAddr := addrDest
        dBusCmdData := S(-1, 32 bits).asBits

        state := State.F
      }

      when (state === State.F) {
        dBusCmdValid := False

        when (dBus.cmd.ready) {
          state := State.IDLE
          decode.arbitration.haltByOther := False
          execute.arbitration.haltItself := False
        }
      }

    }
  }
}

