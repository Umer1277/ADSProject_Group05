package Assignment02

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

object ALUOp extends ChiselEnum {
  val ADD, SUB, AND, OR, XOR, SLL, SRL, SRA, SLT, SLTU, PASSB = Value
}

class ALU extends Module {
  val io = IO(new Bundle {
    val operandA  = Input(UInt(32.W))
    val operandB  = Input(UInt(32.W))
    val operation = Input(ALUOp())
    val aluResult = Output(UInt(32.W))
  })

  // We set a safe default result.
  io.aluResult := 0.U

  // We use only the lower 5 bits for RV32I shift operations.
  val shiftAmount = io.operandB(4, 0)

  // We select the ALU operation.
  switch(io.operation) {
    is(ALUOp.ADD) {
      io.aluResult := io.operandA + io.operandB
    }

    is(ALUOp.SUB) {
      io.aluResult := io.operandA - io.operandB
    }

    is(ALUOp.AND) {
      io.aluResult := io.operandA & io.operandB
    }

    is(ALUOp.OR) {
      io.aluResult := io.operandA | io.operandB
    }

    is(ALUOp.XOR) {
      io.aluResult := io.operandA ^ io.operandB
    }

    is(ALUOp.SLL) {
      io.aluResult := (io.operandA << shiftAmount)(31, 0)
    }

    is(ALUOp.SRL) {
      io.aluResult := io.operandA >> shiftAmount
    }

    is(ALUOp.SRA) {
      io.aluResult := (io.operandA.asSInt >> shiftAmount).asUInt
    }

    is(ALUOp.SLT) {
      io.aluResult := Mux(io.operandA.asSInt < io.operandB.asSInt, 1.U, 0.U)
    }

    is(ALUOp.SLTU) {
      io.aluResult := Mux(io.operandA < io.operandB, 1.U, 0.U)
    }

    is(ALUOp.PASSB) {
      io.aluResult := io.operandB
    }
  }
}