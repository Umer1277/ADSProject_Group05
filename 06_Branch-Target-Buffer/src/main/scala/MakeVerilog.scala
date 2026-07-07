package makeverilog

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import PipelinedRV32I._

//Start code
object Verilog_Gen extends App {
  emitVerilog(
    new PipelinedRV32I("src/test/programs/BinaryFile"),
    Array("--target-dir", "generated-src")
  )
}