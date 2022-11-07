package set

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._

import vexriscv._
import vexriscv.plugin._
import spinal.lib.fsm._

class SetInstPlugin(size: Int) extends Plugin[VexRiscv] {

  var sBus: Axi4Shared = null

  def SET_LOAD = M"0000100----------111-----0001011"
  def SET_FREE = M"0000101----------100-----0001011"
  def SET_COUNT = M"0000010----------110-----0001011"
  def SET_DIFF = M"0000001----------111-----0001011"
  def SET_INTER = M"0000000----------111-----0001011"

  object SetOp extends SpinalEnum {
    val NOP, LOAD, FREE, COUNT, DIFF, INTER, UNION = newElement()
  }
  object SET_OP extends Stageable(SetOp())
  object SET1_ENTRY extends Stageable(SetTableEntry())
  object SET2_ENTRY extends Stageable(SetTableEntry())
  object SETD_ENTRY extends Stageable(SetTableEntry())
  object SETD_WRITE extends Stageable(Bool)

  override def setup(pipeline: VexRiscv): Unit = {
    import pipeline.config._

    val decoderService = pipeline.service(classOf[DecoderService])
    decoderService.addDefault(SET_OP, SetOp.NOP)
    decoderService.addDefault(SETD_WRITE, False)


    val useAll = List[(Stageable[_ <: BaseType], Any)](
      RS1_USE -> True,
      RS2_USE -> True,
      RD_USE -> True,
      SETD_WRITE -> True
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
          RD_USE -> True,
          SETD_WRITE -> True
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
    val addr, count = UInt(32 bits)
    val use = Bool
  }

  override def build(pipeline: VexRiscv): Unit = {
    import pipeline._
    import pipeline.config._

    sBus = master(
      Axi4Shared(
        Axi4Config(
          addressWidth = 32,
          dataWidth = 32,
          useId = false,
          useRegion = false,
          useBurst = false,
          useLock = false,
          useQos = false,
          useLen = false,
          useResp = true,
          useSize = false,
          useStrb = false
        )
      )
    )

    val setTable = new Area {
      val table = Mem(SetTableEntry(), size) addAttribute (Verilator.public)
    }

    pipeline plug setTable

    val perfUnit = pipeline.service(classOf[SetPerfUnitService])

    execute plug new Area {
      import execute._
      import setTable._

      val setOp = input(SET_OP)
      val set1 = table.readAsync(input(RS1).asUInt.resize(table.addressWidth))
      val set2 = table.readAsync(input(RS2).asUInt.resize(table.addressWidth))
      val setd = table.readAsync(input(RD).asUInt.resize(table.addressWidth))

      // Halt Itself when required set is not ready
      def requireSet(sets: SetTableEntry*) = {
        sets.foreach(arbitration.haltItself setWhen !_.use)
      }
      def requireFreeSet(sets: SetTableEntry*) = {
        sets.foreach(arbitration.haltItself setWhen _.use)
      }
      when(arbitration.isValid) {
        when(setOp === SetOp.DIFF || setOp === SetOp.INTER) {
          requireSet(set1, set2, setd)
        }.elsewhen(setOp === SetOp.COUNT) {
          requireSet(set1)
        }.elsewhen(setOp === SetOp.FREE) {
          requireSet(setd)
        }.elsewhen(setOp === SetOp.LOAD) {
          requireFreeSet(setd)
        }
      }

      insert(SET1_ENTRY) := set1
      insert(SET2_ENTRY) := set2
      insert(SETD_ENTRY) := setd

      when(setOp === SetOp.LOAD) {
        insert(SETD_ENTRY).addr := input(RS1).asUInt
        insert(SETD_ENTRY).count := input(RS2).asUInt
        insert(SETD_ENTRY).use := True
      }.elsewhen(setOp === SetOp.FREE) {
        insert(SETD_ENTRY).addr := U"32'h0"
        insert(SETD_ENTRY).count := U"32'h0"
        insert(SETD_ENTRY).use := False
      }
      when(setOp === SetOp.COUNT) {
        output(REGFILE_WRITE_DATA) := setd.count.asBits
      }
    }

    memory plug new Area {
      import memory._
      import setTable._

      // AXI4 Helper
      sBus.arw.valid := False
      sBus.arw.write := False
      sBus.arw.addr := U(0)

      sBus.r.ready := False

      sBus.w.valid := False
      sBus.w.last := True
      sBus.w.data := B(0)

      sBus.b.ready := False

      case class AxiReq() extends Bundle {
        val addr, data = UInt(32 bits)
        val write = Bool
      }
      case class AxiResp() extends Bundle {
        val data = UInt(32 bits)
      }

      val req = Stream(AxiReq())
      val resp = Stream(AxiResp())
      req.valid := False
      req.ready := False
      req.addr := U(0)
      req.data := U(0)
      req.write := False
      resp.valid := False
      resp.ready := False
      resp.data := U(0)

      val axiFsm = new StateMachine {
        val idle: State = new State with EntryPoint {
          whenIsActive {
            when(req.valid) {
              sBus.arw.valid := True
              sBus.arw.write := req.write
              sBus.arw.addr := req.addr
              when(sBus.arw.fire) {
                when(req.write) {
                  goto(write)
                }.otherwise {
                  goto(read)
                }
              }
            }
          }
        }
        val read = new State {
          onEntry {
            req.ready := True
          }
          whenIsActive {
            when(sBus.r.valid) {
              resp.valid := True
              resp.data := sBus.r.data.asUInt
              when(resp.ready) {
                sBus.r.ready := True
                goto(idle)
              }
            }
          }
        }
        val write = new State {
          whenIsActive {
            sBus.w.valid := True
            sBus.w.data := req.data.asBits
            when(sBus.w.fire) {
              goto(write2)
            }
          }
        }
        val write2 = new State {
          onEntry {
            req.ready := True
          }
          whenIsActive {
            when(sBus.b.valid) {
              resp.valid := True
              when(resp.ready) {
                sBus.b.ready := True
                goto(idle)
              }
            }
          }
        }
      }

      def axi4Read(addr: UInt, data: UInt)(block: => Unit) {
        val fired = RegInit(False)
        fired setWhen req.ready
        when(!fired) {
          req.valid := True
          req.addr := addr
        }
        when(resp.valid) {
          resp.ready := True
          data := resp.data
          fired := False
          block
        }
      }

      def axi4Write(addr: UInt, data: UInt)(block: => Unit) {
        val fired = RegInit(False)
        fired setWhen req.ready
        when(!fired) {
          req.valid := True
          req.addr := addr
          req.write := True
          req.data := data
        }
        when(resp.valid) {
          resp.ready := True
          fired := False
          block
        }
      }

      val inited = RegInit(False)
      val aValid, bValid = RegInit(False)
      val aValue, bValue = RegInit(U(0, 32 bits))
      val aCount, bCount, cCount = RegInit(U(0, 32 bits))
      val setA = input(SET1_ENTRY)
      val setB = input(SET2_ENTRY)
      val setC = input(SETD_ENTRY)
      val aAddr, bAddr, cAddr = RegInit(U(0, 32 bits))
      val aEnd = aCount === setA.count
      val bEnd = bCount === setB.count

      def nextA() = {
        when(!aEnd) {
          aValid := False
          aAddr := aAddr + 4
          aCount := aCount + 1
        }
      }
      def nextB() = {
        when(!bEnd) {
          bValid := False
          bAddr := bAddr + 4
          bCount := bCount + 1
        }
      }
      def nextC() = {
        cAddr := cAddr + 4
        cCount := cCount + 1
      }

      val setOp = input(SET_OP)
      val opDiff = setOp === SetOp.DIFF
      val opInter = setOp === SetOp.INTER
      val opUnion = setOp === SetOp.UNION

      when((inited || arbitration.isValid) && (opDiff || opInter || opUnion)) {
        arbitration.haltItself := True

        when(!inited) {
          aAddr := setA.addr
          bAddr := setB.addr
          cAddr := setC.addr
          inited := True
        }.otherwise {
          when(
            (opDiff && aEnd) ||
              (opInter && (aEnd || bEnd)) ||
              (opUnion && (aEnd && bEnd))
          ) {
            arbitration.haltItself := False
            inited := False
            output(SETD_ENTRY).count := cCount

            // Update perf unit
            perfUnit.trigger("setdiff_instexec", Mux(opDiff, U(1), U(0)))
            perfUnit.trigger("setinter_instexec", Mux(opInter, U(1), U(0)))
            perfUnit.trigger("setunion_instexec", Mux(opInter, U(1), U(0)))
          }.elsewhen(!aValid && !aEnd) {
            axi4Read(aAddr, aValue) {
              aValid := True
            }
          }.elsewhen(!bValid && !bEnd) {
            axi4Read(bAddr, bValue) {
              bValid := True
            }
          }.elsewhen((opDiff && bEnd) || (opUnion && bEnd)) {
            axi4Write(cAddr, aValue) {
              nextA()
              nextC()
            }
          }.elsewhen(opUnion && aEnd) {
            axi4Write(cAddr, bValue) {
              nextB()
              nextC()
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
    }

    writeBack plug new Area {
      import writeBack._
      import setTable._

      val initCount = RegInit(U(0, (table.addressWidth + 1) bits))
      val inited = initCount === U(size, (table.addressWidth + 1) bits)
      when(!inited) {
        initCount := initCount + 1
      }

      val emptyEntry = SetTableEntry()
      emptyEntry.addr := U"32'h0"
      emptyEntry.count := U"32'h0"
      emptyEntry.use := False

      val writeAddr =
        (inited ? input(RD).asUInt | initCount).resize(table.addressWidth)
      val writeEntry = inited ? input(SETD_ENTRY) | emptyEntry
      val writeEnable = !inited || (arbitration.isValid && input(SETD_WRITE))

      table.write(
        address = writeAddr,
        data = writeEntry,
        enable = writeEnable
      )
    }
  }
}
