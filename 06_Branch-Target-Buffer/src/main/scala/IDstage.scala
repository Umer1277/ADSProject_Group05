package core_tile

import chisel3._
import chisel3.util._
import uopc._

//Start code
class ID extends Module {
  val io = IO(new Bundle {
    val instr = Input(UInt(32.W))
    val pcIn  = Input(UInt(32.W))

    val uop         = Output(uopc())
    val rd          = Output(UInt(5.W))
    val operandA    = Output(UInt(32.W))
    val operandB    = Output(UInt(32.W))
    val xcptInvalid = Output(Bool())

    val rs1Out = Output(UInt(5.W))
    val rs2Out = Output(UInt(5.W))

    val pcOut  = Output(UInt(32.W))
    val immOut = Output(UInt(32.W))

    val req_3 = Input(new regFileWriteReq)
  })

  //We define the Register File
  val rf = Module(new regFile())

  //We extract the instruction fields
  val opcode = io.instr(6, 0)
  val rd     = io.instr(11, 7)
  val funct3 = io.instr(14, 12)
  val rs1    = io.instr(19, 15)
  val rs2    = io.instr(24, 20)
  val funct7 = io.instr(31, 25)

  //We build the I-type immediate
  val immI = Cat(Fill(20, io.instr(31)), io.instr(31, 20))

  // << NEW in A06 >>
  //Why: Branches need this immediate to calculate target = PC + imm
  val immB = Cat(
    Fill(19, io.instr(31)),
    io.instr(31),
    io.instr(7),
    io.instr(30, 25),
    io.instr(11, 8),
    0.U(1.W)
  )

  //We build the J-type immediate
  val immJ = Cat(
    Fill(11, io.instr(31)),
    io.instr(31),
    io.instr(19, 12),
    io.instr(20),
    io.instr(30, 21),
    0.U(1.W)
  )

  //We connect the Register File read ports
  rf.io.req_1.addr := rs1
  rf.io.req_2.addr := rs2

  //We connect the Register File write port
  rf.io.req_3 := io.req_3

  //We set safe default outputs
  io.uop         := NOP
  io.rd          := rd
  io.operandA    := rf.io.resp_1.data
  io.operandB    := rf.io.resp_2.data
  io.xcptInvalid := false.B

  io.rs1Out := 0.U
  io.rs2Out := 0.U

  io.pcOut  := io.pcIn
  io.immOut := 0.U

  //We decode the instruction family
  switch(opcode) {

    //We decode R-type instructions
    is("b0110011".U) {
      io.operandA := rf.io.resp_1.data
      io.operandB := rf.io.resp_2.data

      io.rs1Out := rs1
      io.rs2Out := rs2

      switch(funct3) {
        is("b000".U) {
          switch(funct7) {
            is("b0000000".U) { io.uop := ADD }
            is("b0100000".U) { io.uop := SUB }
          }

          when((funct7 =/= "b0000000".U) && (funct7 =/= "b0100000".U)) {
            io.uop         := NOP
            io.xcptInvalid := true.B
          }
        }

        is("b001".U) {
          io.uop := SLL
          when(funct7 =/= "b0000000".U) {
            io.uop         := NOP
            io.xcptInvalid := true.B
          }
        }

        is("b010".U) {
          io.uop := SLT
          when(funct7 =/= "b0000000".U) {
            io.uop         := NOP
            io.xcptInvalid := true.B
          }
        }

        is("b011".U) {
          io.uop := SLTU
          when(funct7 =/= "b0000000".U) {
            io.uop         := NOP
            io.xcptInvalid := true.B
          }
        }

        is("b100".U) {
          io.uop := XOR
          when(funct7 =/= "b0000000".U) {
            io.uop         := NOP
            io.xcptInvalid := true.B
          }
        }

        is("b101".U) {
          switch(funct7) {
            is("b0000000".U) { io.uop := SRL }
            is("b0100000".U) { io.uop := SRA }
          }

          when((funct7 =/= "b0000000".U) && (funct7 =/= "b0100000".U)) {
            io.uop         := NOP
            io.xcptInvalid := true.B
          }
        }

        is("b110".U) {
          io.uop := OR
          when(funct7 =/= "b0000000".U) {
            io.uop         := NOP
            io.xcptInvalid := true.B
          }
        }

        is("b111".U) {
          io.uop := AND
          when(funct7 =/= "b0000000".U) {
            io.uop         := NOP
            io.xcptInvalid := true.B
          }
        }
      }
    }

    //We decode I-type instructions
    is("b0010011".U) {
      io.operandA := rf.io.resp_1.data
      io.operandB := immI
      io.immOut   := immI

      io.rs1Out := rs1
      io.rs2Out := 0.U

      switch(funct3) {
        is("b000".U) { io.uop := ADDI  }
        is("b010".U) { io.uop := SLTI  }
        is("b011".U) { io.uop := SLTIU }
        is("b100".U) { io.uop := XORI  }
        is("b110".U) { io.uop := ORI   }
        is("b111".U) { io.uop := ANDI  }

        is("b001".U) {
          io.uop := SLLI
          when(funct7 =/= "b0000000".U) {
            io.uop         := NOP
            io.xcptInvalid := true.B
          }
        }

        is("b101".U) {
          switch(funct7) {
            is("b0000000".U) { io.uop := SRLI }
            is("b0100000".U) { io.uop := SRAI }
          }

          when((funct7 =/= "b0000000".U) && (funct7 =/= "b0100000".U)) {
            io.uop         := NOP
            io.xcptInvalid := true.B
          }
        }
      }
    }

    // << NEW in A06 >>
    //Why: The BTB works only with conditional branches
    is("b1100011".U) {
      io.operandA := rf.io.resp_1.data
      io.operandB := rf.io.resp_2.data
      io.immOut   := immB

      io.rs1Out := rs1
      io.rs2Out := rs2

      switch(funct3) {
        is("b000".U) { io.uop := BEQ  }
        is("b001".U) { io.uop := BNE  }
        is("b100".U) { io.uop := BLT  }
        is("b101".U) { io.uop := BGE  }
        is("b110".U) { io.uop := BLTU }
        is("b111".U) { io.uop := BGEU }
      }

      when((funct3 === "b010".U) || (funct3 === "b011".U)) {
        io.uop         := NOP
        io.xcptInvalid := true.B
      }
    }

    //We decode JAL
    is("b1101111".U) {
      io.uop    := JAL
      io.immOut := immJ

      io.rs1Out := 0.U
      io.rs2Out := 0.U
    }

    //We decode JALR
    is("b1100111".U) {
      io.uop      := JALR
      io.operandA := rf.io.resp_1.data
      io.immOut   := immI

      io.rs1Out := rs1
      io.rs2Out := 0.U

      when(funct3 =/= "b000".U) {
        io.uop         := NOP
        io.xcptInvalid := true.B
      }
    }
  }

  //We check unknown opcodes
  when((opcode =/= "b0110011".U) &&
       (opcode =/= "b0010011".U) &&
       (opcode =/= "b1100011".U) &&
       (opcode =/= "b1101111".U) &&
       (opcode =/= "b1100111".U)) {
    io.uop         := NOP
    io.xcptInvalid := true.B
  }
}