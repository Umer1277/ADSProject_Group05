package core_tile

import chisel3._

//Start code
class MEM extends Module {
  val io = IO(new Bundle {
    val aluResult   = Input(UInt(32.W))
    val rd          = Input(UInt(5.W))
    val xcptInvalid = Input(Bool())

    // << CHANGED in A06 >>
    //Why: WB needs to know if this instruction writes a register
    val inRegWrite = Input(Bool())

    val aluResultOut   = Output(UInt(32.W))
    val rdOut          = Output(UInt(5.W))
    val outXcptInvalid = Output(Bool())

    // << CHANGED in A06 >>
    //Why: We pass the write enable to the next stage
    val outRegWrite = Output(Bool())
  })

  //We pass the values through because memory operations are not implemented
  io.aluResultOut   := io.aluResult
  io.rdOut          := io.rd
  io.outXcptInvalid := io.xcptInvalid
  io.outRegWrite    := io.inRegWrite
}