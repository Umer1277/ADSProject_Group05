package core_tile

import chisel3._

//Start code
class IFBarrier extends Module {
  val io = IO(new Bundle {
    val inInstr = Input(UInt(32.W))
    val inPC    = Input(UInt(32.W))

    // << NEW in A06 >>
    //Why: The prediction must stay with the fetched instruction
    val inPredictedTaken  = Input(Bool())
    val inPredictedTarget = Input(UInt(32.W))

    // << CHANGED in A06 >>
    //Why: We flush only when EX detects a wrong prediction
    val flush = Input(Bool())

    val outInstr = Output(UInt(32.W))
    val outPC    = Output(UInt(32.W))

    // << NEW in A06 >>
    //Why: ID must pass this prediction to EX
    val outPredictedTaken  = Output(Bool())
    val outPredictedTarget = Output(UInt(32.W))
  })

  //We define the pipeline registers
  val instrReg = RegInit("h00000013".U(32.W))
  val pcReg    = RegInit(0.U(32.W))

  // << NEW in A06 >>
  //Why: We must remember what IF predicted
  val predictedTakenReg  = RegInit(false.B)
  val predictedTargetReg = RegInit(0.U(32.W))

  //We store a NOP when the prediction was wrong
  when(io.flush) {
    instrReg := "h00000013".U
    pcReg    := 0.U

    predictedTakenReg  := false.B
    predictedTargetReg := 0.U
  }.otherwise {
    instrReg := io.inInstr
    pcReg    := io.inPC

    predictedTakenReg  := io.inPredictedTaken
    predictedTargetReg := io.inPredictedTarget
  }

  //We drive the outputs
  io.outInstr := instrReg
  io.outPC    := pcReg

  io.outPredictedTaken  := predictedTakenReg
  io.outPredictedTarget := predictedTargetReg
}