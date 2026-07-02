package core_tile

import chisel3._

//Start code
class WB extends Module {
  val io = IO(new Bundle {
    val aluResult = Input(UInt(32.W))
    val rd        = Input(UInt(5.W))
    val exception = Input(Bool())

    // << CHANGED in A06 >>
    //Why: Branches and NOP must not write registers
    val inRegWrite = Input(Bool())

    val regFileReq = Output(new regFileWriteReq)

    val check_res    = Output(UInt(32.W))
    val outException = Output(Bool())
  })

  //We build the writeback request
  io.regFileReq.addr  := io.rd
  io.regFileReq.data  := io.aluResult
  io.regFileReq.wr_en := io.inRegWrite && !io.exception

  //We output the current result for the testbench
  io.check_res    := io.aluResult
  io.outException := io.exception
}