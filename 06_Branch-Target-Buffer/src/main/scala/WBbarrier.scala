package core_tile

import chisel3._

//Start code
class WBBarrier extends Module {
  val io = IO(new Bundle {
    val inCheckRes    = Input(UInt(32.W))
    val inXcptInvalid = Input(Bool())

    val outCheckRes    = Output(UInt(32.W))
    val outXcptInvalid = Output(Bool())
  })

  //We define the final output registers
  val checkResReg    = RegInit(0.U(32.W))
  val xcptInvalidReg = RegInit(false.B)

  //We store the WB outputs
  checkResReg    := io.inCheckRes
  xcptInvalidReg := io.inXcptInvalid

  //We drive the top-level outputs
  io.outCheckRes    := checkResReg
  io.outXcptInvalid := xcptInvalidReg
}