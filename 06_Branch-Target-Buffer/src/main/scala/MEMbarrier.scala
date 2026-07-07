package core_tile

import chisel3._

//Start code
class MEMBarrier extends Module {
  val io = IO(new Bundle {
    val inAluResult = Input(UInt(32.W))
    val inRD        = Input(UInt(5.W))
    val inException = Input(Bool())

    // << CHANGED in A06 >>
    //Why: WB needs the real write enable
    val inRegWrite = Input(Bool())

    val outAluResult = Output(UInt(32.W))
    val outRD        = Output(UInt(5.W))
    val outException = Output(Bool())

    // << CHANGED in A06 >>
    //Why: Forwarding and WB need this value
    val outRegWrite = Output(Bool())
  })

  //We define the pipeline registers
  val aluResultReg = RegInit(0.U(32.W))
  val rdReg        = RegInit(0.U(5.W))
  val exceptionReg = RegInit(false.B)
  val regWriteReg  = RegInit(false.B)

  //We store the MEM outputs
  aluResultReg := io.inAluResult
  rdReg        := io.inRD
  exceptionReg := io.inException
  regWriteReg  := io.inRegWrite

  //We drive the WB inputs
  io.outAluResult := aluResultReg
  io.outRD        := rdReg
  io.outException := exceptionReg
  io.outRegWrite  := regWriteReg
}