package core_tile

import chisel3._

//We define the bundle for a read request
class regFileReadReq extends Bundle {
  val addr = UInt(5.W)
}

//We define the bundle for a read response
class regFileReadResp extends Bundle {
  val data = UInt(32.W)
}

//We define the bundle for a write request
class regFileWriteReq extends Bundle {
  val addr  = UInt(5.W)
  val data  = UInt(32.W)
  val wr_en = Bool()
}

//Start code
class regFile extends Module {
  val io = IO(new Bundle {
    val req_1  = Input(new regFileReadReq)
    val resp_1 = Output(new regFileReadResp)

    val req_2  = Input(new regFileReadReq)
    val resp_2 = Output(new regFileReadResp)

    val req_3  = Input(new regFileWriteReq)
  })

  //We define the 32 registers
  val registers = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  //We check read port 1
  when(io.req_1.addr === 0.U) {
    io.resp_1.data := 0.U
  }.elsewhen(io.req_3.wr_en && (io.req_3.addr === io.req_1.addr) && (io.req_3.addr =/= 0.U)) {
    io.resp_1.data := io.req_3.data
  }.otherwise {
    io.resp_1.data := registers(io.req_1.addr)
  }

  //We check read port 2
  when(io.req_2.addr === 0.U) {
    io.resp_2.data := 0.U
  }.elsewhen(io.req_3.wr_en && (io.req_3.addr === io.req_2.addr) && (io.req_3.addr =/= 0.U)) {
    io.resp_2.data := io.req_3.data
  }.otherwise {
    io.resp_2.data := registers(io.req_2.addr)
  }

  //We write only when write enable is active and the address is not x0
  when(io.req_3.wr_en && (io.req_3.addr =/= 0.U)) {
    registers(io.req_3.addr) := io.req_3.data
  }
}