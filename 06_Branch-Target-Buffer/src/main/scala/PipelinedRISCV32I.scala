package PipelinedRV32I

import chisel3._
import chisel3.util._
import core_tile._

//Start code
class PipelinedRV32I (BinaryFile: String) extends Module {
  val io = IO(new Bundle {
    val result    = Output(UInt(32.W))
    val exception = Output(Bool())
  })

  //We define the processor core
  val core = Module(new PipelinedRV32Icore(BinaryFile))

  //We connect the visible outputs
  io.result    := core.io.check_res
  io.exception := core.io.exception
}