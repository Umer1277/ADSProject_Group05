package core_tile

import chisel3._
import chisel3.util._
import uopc._

//Start code
class ForwardingUnit extends Module {
  val io = IO(new Bundle {
    val rs1_EX = Input(UInt(5.W))
    val rs2_EX = Input(UInt(5.W))

    val rd_MEM   = Input(UInt(5.W))
    val wrEn_MEM = Input(Bool())

    val rd_WB   = Input(UInt(5.W))
    val wrEn_WB = Input(Bool())

    val forwardA = Output(UInt(2.W))
    val forwardB = Output(UInt(2.W))
  })

  //We set no forwarding as default
  io.forwardA := 0.U
  io.forwardB := 0.U

  //We check forwarding from MEM first because it has the newest value
  when(io.wrEn_MEM && (io.rd_MEM =/= 0.U)) {
    when(io.rd_MEM === io.rs1_EX) {
      io.forwardA := 2.U
    }

    when(io.rd_MEM === io.rs2_EX) {
      io.forwardB := 2.U
    }
  }

  //We check forwarding from WB only if MEM did not already match
  when(io.wrEn_WB && (io.rd_WB =/= 0.U)) {
    when((io.rd_WB === io.rs1_EX) &&
         !(io.wrEn_MEM && (io.rd_MEM =/= 0.U) && (io.rd_MEM === io.rs1_EX))) {
      io.forwardA := 1.U
    }

    when((io.rd_WB === io.rs2_EX) &&
         !(io.wrEn_MEM && (io.rd_MEM =/= 0.U) && (io.rd_MEM === io.rs2_EX))) {
      io.forwardB := 1.U
    }
  }
}