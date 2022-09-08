package set

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._

import vexriscv._
import vexriscv.plugin._

class SetInstPlugin(size: Int) extends Plugin[VexRiscv] {

  var sBus: Axi4Shared = null

  object SetOp extends SpinalEnum {
    val NOP, LOAD, FREE, COUNT, DIFF, INTER, UNION = newElement()
  }
  object SET_OP extends Stageable(SetOp())

  def SET_LOAD = M"0000100----------111-----0001011"
  def SET_FREE = M"0000101----------100-----0001011"
  def SET_COUNT = M"0000010----------110-----0001011"
  def SET_DIFF = M"0000001----------111-----0001011"
  def SET_INTER = M"0000000----------111-----0001011"

  override def setup(pipeline: VexRiscv): Unit = {
    import pipeline.config._

    val decoderService = pipeline.service(classOf[DecoderService])
    decoderService.addDefault(SET_OP, SetOp.NOP)

    val useAll = List[(Stageable[_ <: BaseType], Any)](
      RS1_USE -> True,
      RS2_USE -> True,
      RD_USE -> True
    )
    decoderService.add(
      List(
        SET_DIFF -> (useAll ++ List(SET_OP -> SetOp.DIFF)),
        SET_INTER -> (useAll ++ List(SET_OP -> SetOp.INTER)),
        SET_LOAD -> (useAll ++ List(SET_OP -> SetOp.LOAD))
      )
    )

    decoderService.add(
      List(
        SET_FREE -> List(
          SET_OP -> SetOp.FREE,
          RD_USE -> True
        ),
        SET_COUNT -> List(
          SET_OP -> SetOp.COUNT,
          REGFILE_WRITE_VALID -> True,
          RS1_USE -> True
        )
      )
    )
  }

  case class SetTableEntry() extends Bundle {
    val id, addr, count = UInt(32 bits)
    val use = Bool
  }

  override def build(pipeline: VexRiscv): Unit = {
    import pipeline._
    import pipeline.config._

    sBus = Axi4Shared(Axi4Config(32, 32, 2))

    val setTable = new Area {
      // val table = Mem(SetTableEntry(), size) addAttribute(Verilator.public)
      val table = Vec(SetTableEntry(), size) addAttribute (Verilator.public)
    }

    pipeline plug setTable

    execute plug new Area {
      import execute._
      import setTable._

      val setOp = input(SET_OP)
      val setRs1 = table.sFindFirst(s => s.id === input(RS1).asUInt && s.use)
      val setRs2 = table.sFindFirst(s => s.id === input(RS2).asUInt && s.use)
      val setRd = table.sFindFirst(s => s.id === input(RD).asUInt && s.use)
      val setFree = table.sFindFirst(!_.use)

      // Halt Itself when required set is not ready
      def requireSet(findRes: (Bool, UInt)*) = {
        findRes.foreach(arbitration.haltItself setWhen !_._1)
      }
      def requireFreeSet(findRes: (Bool, UInt)*) = {
        findRes.foreach(arbitration.haltItself setWhen _._1)
      }
      when(setOp === SetOp.DIFF || setOp === SetOp.INTER) {
        requireSet(setRs1, setRs2, setRd)
      }
      when(setOp === SetOp.COUNT) {
        requireSet(setRs1)
      }
      when(setOp === SetOp.FREE) {
        requireSet(setRd)
      }
      when(setOp === SetOp.LOAD) {
        requireFreeSet(setRd)
        arbitration.haltItself setWhen !setFree._1
      }

      when(setRs1._1) {
        insert(SR1_ADDR) := table(setRs1._2).addr
        insert(SR1_CNT) := table(setRs1._2).count
        when(setOp === SetOp.COUNT) {
          output(REGFILE_WRITE_DATA) := table(setRs1._2).count.asBits
        }
      }
      when(setRs2._1) {
        insert(SR2_ADDR) := table(setRs2._2).addr
        insert(SR2_CNT) := table(setRs2._2).count
      }
      when(setRd._1) {
        insert(SR0_ADDR) := table(setRd._2).addr
        insert(SR0_IDX) := setRd._2
      }
      when(setOp === SetOp.LOAD) {
        insert(SR0_ID) := input(RD).asUInt
        insert(SR0_IDX) := setFree._2
        insert(SR0_ADDR) := input(RS1).asUInt
        insert(SR0_NEW_CNT) := input(RS2).asUInt
      }
    }

    memory plug new Area {
      import memory._

      // AXI4 Helper
      def axi4Read(addr: UInt, data: UInt)(block: => Unit) = {
        val fired = RegInit(False)
        sBus.arw.valid := !fired
        sBus.arw.write := False
        sBus.arw.addr := addr
        sBus.arw.size := U(4)
        sBus.arw.len := U(0)
        fired := sBus.arw.fire

        sBus.r.ready := False
        when(sBus.r.valid) {
          // RRESP is ignored
          sBus.r.ready := True
          data := sBus.r.data.asUInt
          block
        }
      }
      val writing = RegInit(False)
      def axi4Write(addr: UInt, data: UInt)(block: => Unit) = {
        writing := True
        val fired = RegInit(False)
        sBus.arw.valid := !fired
        sBus.arw.write := True
        sBus.arw.addr := addr
        sBus.arw.size := U(4)
        sBus.arw.len := U(0)
        fired := sBus.arw.fire

        val dataFired = RegInit(False)
        when(fired) {
          sBus.w.valid := !dataFired
          sBus.w.data := data.asBits
          sBus.w.last := True
          dataFired := sBus.w.ready
        }

        when(sBus.b.valid) {
          sBus.b.ready := True
          writing := False
          block
        }
      }

      val aValid, bValid = RegInit(False)
      val aValue, bValue = RegInit(U(0, 32 bits))
      val aCount, bCount, cCount = RegInit(U(0, 32 bits))
      val aAddr = RegInit(input(SR1_ADDR))
      val bAddr = RegInit(input(SR2_ADDR))
      val cAddr = RegInit(input(SR0_ADDR))
      val aEnd = aCount === input(SR1_CNT)
      val bEnd = bCount === input(SR2_CNT)

      def nextA() = {
        aValid := False
        aAddr := aAddr + 4
        aCount := aCount + 1
      }
      def nextB() = {
        bValid := False
        bAddr := bAddr + 4
        bCount := bCount + 1
      }
      def nextC() = {
        cAddr := cAddr + 4
        cCount := cCount + 1
      }

      val setOp = input(SET_OP)
      val opDiff = setOp === SetOp.DIFF
      val opInter = setOp === SetOp.INTER
      val opUnion = setOp === SetOp.UNION

      when(setOp === SetOp.DIFF || setOp === SetOp.INTER) {
        arbitration.haltItself := True

        when(
          (opDiff && aEnd) ||
            (opInter && (aEnd || bEnd)) ||
            (opUnion && (aEnd && bEnd))
        ) {
          arbitration.haltItself := writing
          output(SR0_NEW_CNT) := cCount
        }.elsewhen(!aValid && !aEnd) {
          axi4Read(aAddr, aValue) {
            aValid := True
          }
        }.elsewhen(!bValid && !bEnd) {
          axi4Read(bAddr, bValue) {
            bValid := True
          }
        }.otherwise {
          when(aValue < bValue) {
            when(opDiff || opUnion) {
              axi4Write(cAddr, aValue) {
                nextA()
                nextC()
              }
            }.otherwise {
              nextA()
            }
          }.elsewhen(aValue > bValue) {
            when(opUnion) {
              axi4Write(cAddr, bValue) {
                nextB()
                nextC()
              }
            }.otherwise {
              nextB()
            }
          }.otherwise {
            when(opDiff) {
              nextA()
              nextB()
            }.otherwise {
              axi4Write(cAddr, aValue) {
                nextA()
                nextB()
                nextC()
              }
            }
          }
        }
      }
    }

    writeBack plug new Area {
      import writeBack._
      import setTable._

      val setOp = input(SET_OP)
      val idx = input(SR0_IDX)
      when(setOp === SetOp.LOAD) {
        table(idx).use := True
        table(idx).id := input(SR0_ID)
        table(idx).addr := input(SR0_ADDR)
        table(idx).count := input(SR0_NEW_CNT)
      }.elsewhen(setOp === SetOp.FREE) {
        table(idx).use := False
      }.elsewhen(setOp =/= SetOp.NOP && setOp =/= SetOp.COUNT) {
        table(idx).count := input(SR0_NEW_CNT)
      }
    }
  }
}
