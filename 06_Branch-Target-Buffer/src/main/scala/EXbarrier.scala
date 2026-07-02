package core_tile

import chisel3._

//Start code
class EXBarrier extends Module {
  val io = IO(new Bundle {
    val inAluResult   = Input(UInt(32.W))
    val inRD          = Input(UInt(5.W))
    val inXcptInvalid = Input(Bool())

    // << CHANGED in A06 >>
    //Why: WB needs the real write enable
    val inRegWrite = Input(Bool())

    val outAluResult   = Output(UInt(32.W))
    val outRD          = Output(UInt(5.W))
    val outXcptInvalid = Output(Bool())

    // << CHANGED in A06 >>
    //Why: Forwarding and WB need this signal later
    val outRegWrite = Output(Bool())
  })

  //We define the pipeline registers
  val aluResultReg   = RegInit(0.U(32.W))
  val rdReg          = RegInit(0.U(5.W))
  val xcptInvalidReg = RegInit(false.B)
  val regWriteReg    = RegInit(false.B)

  //We store the EX outputs
  aluResultReg   := io.inAluResult
  rdReg          := io.inRD
  xcptInvalidReg := io.inXcptInvalid
  regWriteReg    := io.inRegWrite

  //We drive the MEM inputs
  io.outAluResult   := aluResultReg
  io.outRD          := rdReg
  io.outXcptInvalid := xcptInvalidReg
  io.outRegWrite    := regWriteReg
}