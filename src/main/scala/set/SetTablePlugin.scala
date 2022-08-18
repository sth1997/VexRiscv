package set

import spinal.core._
import spinal.lib._

import vexriscv._
import vexriscv.plugin._

/*
set id   |   start addr   |   size
*/

class SetTablePlugin(size: Int) extends Plugin[VexRiscv] {

  object IS_SETLOAD extends Stageable(Bool)
  object IS_SETFREE extends Stageable(Bool)

  override def setup(pipeline: VexRiscv): Unit = {
    import pipeline.config._
    val decoderService = pipeline.service(classOf[DecoderService])

    decoderService.addDefault(IS_SETLOAD, False)
    decoderService.add(
      key = M"0000100----------111-----0001011",
      List(
        IS_SETLOAD -> True,
        RD_USE -> True,
        RS1_USE -> True,
        RS2_USE -> True
      )
    )

    decoderService.addDefault(IS_SETFREE, False)
    decoderService.add(
      key = M"0000101----------100-----0001011",
      List(
        IS_SETFREE -> True,
        RD_USE -> True
      )
    )

    decoderService.addDefault(SR0_NEW_CNT, U(0, 32 bits))
    decoderService.addDefault(SR0_REWRITE_VALID, False)
  }

  override def build(pipeline: VexRiscv): Unit = {
    import pipeline._
    import pipeline.config._

    val global = pipeline plug new Area {
      val ids   = Mem(UInt(32 bits), size) addAttribute(Verilator.public)
      val addrs = Mem(UInt(32 bits), size) addAttribute(Verilator.public)
      val cnts  = Mem(UInt(32 bits), size) addAttribute(Verilator.public)
      val uses = Mem(Bool, size) addAttribute(Verilator.public)
    }
    
    // Read
    execute plug new Area {
      import execute._

      //val raw_id1 = Reg(UInt(32 bits))

/*
      switch (input(RS1).asUInt) {
        for (i <- 0 until size) 
          is(global.ids.readSync(U(i, 4 bits))) {
            when (global.uses.readSync(U(i, 4 bits))) {
              insert(SR1_ADDR) := global.addrs.readSync(U(i, 4 bits))
              insert(SR1_CNT) := global.cnts.readSync(U(i, 4 bits))
            } otherwise {
              insert(SR1_ADDR) := 0
              insert(SR1_CNT) := 0
            }
          }

        default {
          insert(SR1_ADDR) := 0
          insert(SR1_CNT) := 0
        }
      }
*/
/*
      switch (input(RS2).asUInt) {
        for (i <- 0 until size) 
          is(global.ids.readSync(U(i, 4 bits))) {
            when (global.uses.readSync(U(i, 4 bits))) {
              insert(SR2_ADDR) := global.addrs.readSync(U(i, 4 bits))
              insert(SR2_CNT) := global.cnts.readSync(U(i, 4 bits))
            } otherwise {
              insert(SR2_ADDR) := 0
              insert(SR2_CNT) := 0
            }

          }

        default {
          insert(SR2_ADDR) := 0
          insert(SR2_CNT) := 0
        }
      }
      switch (input(RD).asUInt) {
        for (i <- 0 until size) 
          is(global.ids.readSync(U(i, 4 bits))) {
            when (global.uses.readSync(U(i, 4 bits))) {
              insert(SR0_ADDR) := global.addrs.readSync(U(i, 4 bits))
              insert(SR0_RAW_ID) := U(i, 4 bits)
            } otherwise {
              insert(SR0_ADDR) := 0
              insert(SR0_RAW_ID) := 0
            }

          }

        default {
          insert(SR0_ADDR) := 0
          insert(SR0_RAW_ID) := 0
        }
      }
*/
      def gen1(i: Int): Unit = {
        when (global.uses.readSync(U(i, 4 bits)) && (global.ids.readSync(U(i, 4 bits)) === input(RS1).asUInt)) {
          insert(SR1_ADDR) := global.addrs.readSync(U(i, 4 bits))
          insert(SR1_CNT) := global.cnts.readSync(U(i, 4 bits))
        } otherwise {
          if (i > 0) gen1(i - 1) else {
            insert(SR1_ADDR) := 0
            insert(SR1_CNT) := 0
          }
        }
      }
      gen1(size - 1)

      def gen2(i: Int): Unit = {
        when (global.uses.readSync(U(i, 4 bits)) && (global.ids.readSync(U(i, 4 bits)) === input(RS2).asUInt)) {
          insert(SR2_ADDR) := global.addrs.readSync(U(i, 4 bits))
          insert(SR2_CNT) := global.cnts.readSync(U(i, 4 bits))
        } otherwise {
          if (i > 0) gen2(i - 1) else {
            insert(SR2_ADDR) := 0
            insert(SR2_CNT) := 0
          }
        }
      }
      gen2(size - 1)

      def gen0(i: Int): Unit = {
        when (global.uses.readSync(U(i, 4 bits)) && (global.ids.readSync(U(i, 4 bits)) === input(RD).asUInt)) {
          insert(SR0_ADDR) := global.addrs.readSync(U(i, 4 bits))
          insert(SR0_RAW_ID) := U(i, 4 bits)
        } otherwise {
          if (i > 0) gen0(i - 1) else {
            insert(SR0_ADDR) := 0
            insert(SR0_RAW_ID) := 0
          }
        }
      }
      gen0(size - 1)
    }

    // Write, for setload and setfree
    writeBack plug new Area {
      import writeBack._

//      regFileWrite.valid := output(REGFILE_WRITE_VALID) && arbitration.isFiring
//      regFileWrite.address := U(shadowPrefix(output(INSTRUCTION)(clipRange(rdRange))))
//      regFileWrite.data := output(REGFILE_WRITE_DATA)


      when (input(IS_SETLOAD)) {
        val selected = Reg(Bool) init(False)
        val idx = Reg(UInt(4 bits)) init(0)

        def gen(i: Int): Unit = {
          when (!global.uses.readSync(U(i, 4 bits))) {
            selected := True
            idx := U(i, 4 bits)
          } otherwise {
            if (i > 0) gen(i - 1)
          }
        }

        when (!selected) {
          gen(size - 1)
        }

        val tc = input(RS2).asUInt
        val ta = input(RS1).asUInt
        val ti = input(RD).asUInt

        when (selected) {
            global.uses(idx) := True
            global.cnts(idx) := tc
            global.addrs(idx) := ta
            global.ids(idx) := ti
        }

        when (arbitration.isFiring) {
          selected := False
        }
      }

      when (input(IS_SETFREE)) {
        val id = input(RD).asUInt
        for (i <- 0 until size)
          when ((id === global.ids.readSync(U(i, 4 bits))) && (global.uses.readSync(U(i, 4 bits)))) {
            global.uses(U(i, 4 bits)) := False
          }
      }

      when (input(SR0_REWRITE_VALID) && arbitration.isFiring) {
        global.cnts(input(SR0_RAW_ID)) := input(SR0_NEW_CNT)
      }

    }
  }

}

