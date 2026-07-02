package core_tile

import chisel3._
import uopc._

//Start code
class IDBarrier extends Module {
  val io = IO(new Bundle {
    val inUOP         = Input(uopc())
    val inRD          = Input(UInt(5.W))
    val inOperandA    = Input(UInt(32.W))
    val inOperandB    = Input(UInt(32.W))
    val inXcptInvalid = Input(Bool())

    val inRS1 = Input(UInt(5.W))
    val inRS2 = Input(UInt(5.W))

    val inPC  = Input(UInt(32.W))
    val inImm = Input(UInt(32.W))

    // << NEW in A06 >>
    //Why: EX needs the IF prediction to check if it was correct
    val inPredictedTaken  = Input(Bool())
    val inPredictedTarget = Input(UInt(32.W))

    // << CHANGED in A06 >>
    //Why: We flush only when the prediction was wrong
    val flush = Input(Bool())

    val outUOP         = Output(uopc())
    val outRD          = Output(UInt(5.W))
    val outOperandA    = Output(UInt(32.W))
    val outOperandB    = Output(UInt(32.W))
    val outXcptInvalid = Output(Bool())

    val outRS1 = Output(UInt(5.W))
    val outRS2 = Output(UInt(5.W))

    val outPC  = Output(UInt(32.W))
    val outImm = Output(UInt(32.W))

    // << NEW in A06 >>
    //Why: These values are used by EX to detect misprediction
    val outPredictedTaken  = Output(Bool())
    val outPredictedTarget = Output(UInt(32.W))
  })

  //We define the pipeline registers
  val uopReg         = RegInit(NOP)
  val rdReg          = RegInit(0.U(5.W))
  val operandAReg    = RegInit(0.U(32.W))
  val operandBReg    = RegInit(0.U(32.W))
  val xcptInvalidReg = RegInit(false.B)

  val rs1Reg = RegInit(0.U(5.W))
  val rs2Reg = RegInit(0.U(5.W))

  val pcReg  = RegInit(0.U(32.W))
  val immReg = RegInit(0.U(32.W))

  // << NEW in A06 >>
  //Why: The prediction must be stored with the instruction
  val predictedTakenReg  = RegInit(false.B)
  val predictedTargetReg = RegInit(0.U(32.W))

  //We clear the barrier when EX detects a wrong prediction
  when(io.flush) {
    uopReg         := NOP
    rdReg          := 0.U
    operandAReg    := 0.U
    operandBReg    := 0.U
    xcptInvalidReg := false.B

    rs1Reg := 0.U
    rs2Reg := 0.U

    pcReg  := 0.U
    immReg := 0.U

    predictedTakenReg  := false.B
    predictedTargetReg := 0.U
  }.otherwise {
    uopReg         := io.inUOP
    rdReg          := io.inRD
    operandAReg    := io.inOperandA
    operandBReg    := io.inOperandB
    xcptInvalidReg := io.inXcptInvalid

    rs1Reg := io.inRS1
    rs2Reg := io.inRS2

    pcReg  := io.inPC
    immReg := io.inImm

    predictedTakenReg  := io.inPredictedTaken
    predictedTargetReg := io.inPredictedTarget
  }

  //We drive the outputs
  io.outUOP         := uopReg
  io.outRD          := rdReg
  io.outOperandA    := operandAReg
  io.outOperandB    := operandBReg
  io.outXcptInvalid := xcptInvalidReg

  io.outRS1 := rs1Reg
  io.outRS2 := rs2Reg

  io.outPC  := pcReg
  io.outImm := immReg

  io.outPredictedTaken  := predictedTakenReg
  io.outPredictedTarget := predictedTargetReg
}