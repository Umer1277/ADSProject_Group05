package PipelinedRV32I

import chisel3._
import chisel3.util._
import core_tile._

//Start code
class PipelinedRV32I (BinaryFile: String) extends Module {
  val io = IO(new Bundle {
    val result               = Output(UInt(32.W))
    val exception            = Output(Bool())
    val totalCycles          = Output(UInt(32.W))
    val totalBranches        = Output(UInt(32.W))
    val mispredictedBranches = Output(UInt(32.W))
    val useBTB               = Input(Bool())
  })

  //We define the processor core
  val core = Module(new PipelinedRV32Icore(BinaryFile))

  //We connect the visible outputs and inputs
  core.io.useBTB := io.useBTB
  io.result    := core.io.check_res
  io.exception := core.io.exception
  io.totalCycles := core.io.totalCycles
  io.totalBranches := core.io.totalBranches
  io.mispredictedBranches := core.io.mispredictedBranches
}