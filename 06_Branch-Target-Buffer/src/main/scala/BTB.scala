package core_tile

import chisel3._
import chisel3.util._

//Start code
class BTB extends Module {
  val io = IO(new Bundle {
    val PC           = Input(UInt(32.W))
    val update       = Input(Bool())
    val updatePC     = Input(UInt(32.W))
    val updateTarget = Input(UInt(32.W))
    val mispredicted = Input(Bool())

    val valid        = Output(Bool())
    val target       = Output(UInt(32.W))
    val predictTaken = Output(Bool())
  })

  // << CHANGED in A06 >>
  //Why: Spec requires 8 sets, 2-way set-associative BTB
  val numSets   = 8
  val numWays   = 2
  val indexBits = log2Ceil(numSets)
  val tagWidth  = 32 - indexBits - 2

  //We store one array per field, indexed [set][way]
  val validRegs  = RegInit(VecInit(Seq.fill(numSets)(VecInit(Seq.fill(numWays)(false.B)))))
  val tagRegs    = RegInit(VecInit(Seq.fill(numSets)(VecInit(Seq.fill(numWays)(0.U(tagWidth.W))))))
  val targetRegs = RegInit(VecInit(Seq.fill(numSets)(VecInit(Seq.fill(numWays)(0.U(32.W))))))
  val stateRegs  = RegInit(VecInit(Seq.fill(numSets)(VecInit(Seq.fill(numWays)(1.U(2.W))))))

  // << NEW in A06 >>
  //Why: With 2 ways, 1 bit per set marks which way to evict next
  val lruWay = RegInit(VecInit(Seq.fill(numSets)(0.U(1.W))))

  //We split PC into index and tag, bits [1:0] are always zero
  val lookupIndex = io.PC(indexBits + 1, 2)
  val lookupTag   = io.PC(31, indexBits + 2)

  //We check both ways for a tag match
  val hit0 = validRegs(lookupIndex)(0) && (tagRegs(lookupIndex)(0) === lookupTag)
  val hit1 = validRegs(lookupIndex)(1) && (tagRegs(lookupIndex)(1) === lookupTag)
  val hit  = hit0 || hit1

  io.valid        := hit
  io.target       := Mux(hit1, targetRegs(lookupIndex)(1), targetRegs(lookupIndex)(0))
  io.predictTaken := hit && Mux(hit1, stateRegs(lookupIndex)(1)(1), stateRegs(lookupIndex)(0)(1))

  // << NEW in A06 >>
  //Why: A lookup hit counts as an access for LRU tracking
  when(hit0) {
    lruWay(lookupIndex) := 1.U
  }.elsewhen(hit1) {
    lruWay(lookupIndex) := 0.U
  }

  //We split updatePC the same way
  val updateIndex = io.updatePC(indexBits + 1, 2)
  val updateTag   = io.updatePC(31, indexBits + 2)

  val updHit0 = validRegs(updateIndex)(0) && (tagRegs(updateIndex)(0) === updateTag)
  val updHit1 = validRegs(updateIndex)(1) && (tagRegs(updateIndex)(1) === updateTag)
  val updHit  = updHit0 || updHit1

  // << NEW in A06 >>
  //Why: On a miss we allocate an invalid way first, otherwise evict the LRU way
  val allocWay = Mux(!validRegs(updateIndex)(0), 0.U,
                  Mux(!validRegs(updateIndex)(1), 1.U, lruWay(updateIndex)))

  val selectedWay = Mux(updHit, Mux(updHit1, 1.U, 0.U), allocWay)

  val oldState = stateRegs(updateIndex)(selectedWay)

  // << NEW in A06 >>
  //Why: Recover the real branch direction from the mispredicted signal
  val oldPredictTaken = updHit && oldState(1)
  val actualTaken     = Mux(updHit, oldPredictTaken =/= io.mispredicted, io.mispredicted)

  //We saturate the 2-bit predictor at 00 and 11
  val nextState = WireDefault(oldState)

  when(actualTaken && (oldState =/= 3.U)) {
    nextState := oldState + 1.U
  }

  when(!actualTaken && (oldState =/= 0.U)) {
    nextState := oldState - 1.U
  }

  //We write the selected way on update
  when(io.update) {
    validRegs(updateIndex)(selectedWay)  := true.B
    tagRegs(updateIndex)(selectedWay)    := updateTag
    targetRegs(updateIndex)(selectedWay) := io.updateTarget

    when(updHit) {
      stateRegs(updateIndex)(selectedWay) := nextState
    }.otherwise {
      //Why: New entries get a fixed weak-taken/weak-not-taken start
      stateRegs(updateIndex)(selectedWay) := Mux(io.mispredicted, 2.U, 1.U)
    }

    //We mark the written way as most recently used
    lruWay(updateIndex) := Mux(selectedWay === 0.U, 1.U, 0.U)
  }
}