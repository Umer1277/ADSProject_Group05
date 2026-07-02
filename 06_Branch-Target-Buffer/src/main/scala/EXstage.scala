package core_tile

import chisel3._
import chisel3.util._

import Assignment02.{ALU, ALUOp}
import uopc._

//Start code
class EX extends Module {
  val io = IO(new Bundle {
    val uop           = Input(uopc())
    val inRD          = Input(UInt(5.W))
    val operandA      = Input(UInt(32.W))
    val operandB      = Input(UInt(32.W))
    val inXcptInvalid = Input(Bool())

    val forwardA = Input(UInt(2.W))
    val forwardB = Input(UInt(2.W))

    val memData = Input(UInt(32.W))
    val wbData  = Input(UInt(32.W))

    val rs1 = Input(UInt(5.W))
    val rs2 = Input(UInt(5.W))

    val pcIn = Input(UInt(32.W))
    val imm  = Input(UInt(32.W))

    // << NEW in A06 >>
    //Why: EX compares the IF prediction with the real branch result
    val predictedTaken  = Input(Bool())
    val predictedTarget = Input(UInt(32.W))

    val aluResult      = Output(UInt(32.W))
    val outRD          = Output(UInt(5.W))
    val outXcptInvalid = Output(Bool())
    val outRegWrite    = Output(Bool())

    // << CHANGED in A06 >>
    //Why: Flush now means wrong prediction, not every taken branch
    val flush        = Output(Bool())
    val branchTarget = Output(UInt(32.W))

    // << NEW in A06 >>
    //Why: EX updates the BTB after resolving the branch
    val btbUpdate       = Output(Bool())
    val btbUpdatePC     = Output(UInt(32.W))
    val btbUpdateTarget = Output(UInt(32.W))
    val btbMispredicted = Output(Bool())
  })

  //We define the ALU
  val alu = Module(new ALU())

  //We map uop to ALU operation
  val aluOp = WireDefault(ALUOp.ADD)

  switch(io.uop) {
    is(ADD)  { aluOp := ALUOp.ADD  }
    is(SUB)  { aluOp := ALUOp.SUB  }
    is(AND)  { aluOp := ALUOp.AND  }
    is(OR)   { aluOp := ALUOp.OR   }
    is(XOR)  { aluOp := ALUOp.XOR  }
    is(SLL)  { aluOp := ALUOp.SLL  }
    is(SRL)  { aluOp := ALUOp.SRL  }
    is(SRA)  { aluOp := ALUOp.SRA  }
    is(SLT)  { aluOp := ALUOp.SLT  }
    is(SLTU) { aluOp := ALUOp.SLTU }

    is(ADDI)  { aluOp := ALUOp.ADD  }
    is(ANDI)  { aluOp := ALUOp.AND  }
    is(ORI)   { aluOp := ALUOp.OR   }
    is(XORI)  { aluOp := ALUOp.XOR  }
    is(SLLI)  { aluOp := ALUOp.SLL  }
    is(SRLI)  { aluOp := ALUOp.SRL  }
    is(SRAI)  { aluOp := ALUOp.SRA  }
    is(SLTI)  { aluOp := ALUOp.SLT  }
    is(SLTIU) { aluOp := ALUOp.SLTU }

    is(JAL)  { aluOp := ALUOp.ADD }
    is(JALR) { aluOp := ALUOp.ADD }

    is(NOP)  { aluOp := ALUOp.PASSB }
  }

  //We select forwarded operand A
  val muxA = MuxLookup(io.forwardA, io.operandA, Seq(
    0.U(2.W) -> io.operandA,
    1.U(2.W) -> io.wbData,
    2.U(2.W) -> io.memData
  ))

  //We select forwarded operand B
  val muxB = MuxLookup(io.forwardB, io.operandB, Seq(
    0.U(2.W) -> io.operandB,
    1.U(2.W) -> io.wbData,
    2.U(2.W) -> io.memData
  ))

  //We check if the instruction is a conditional branch
  val isBranch = (io.uop === BEQ)  || (io.uop === BNE)  ||
                 (io.uop === BLT)  || (io.uop === BGE)  ||
                 (io.uop === BLTU) || (io.uop === BGEU)

  //We check if the instruction is an unconditional jump
  val isJump = (io.uop === JAL) || (io.uop === JALR)

  //We use PC + 4 for the JAL and JALR link value
  alu.io.operandA  := Mux(isJump, io.pcIn, muxA)
  alu.io.operandB  := Mux(isJump, 4.U(32.W), muxB)
  alu.io.operation := aluOp

  //We check the real branch condition
  val branchCond = WireDefault(false.B)

  switch(io.uop) {
    is(BEQ)  { branchCond := muxA === muxB }
    is(BNE)  { branchCond := muxA =/= muxB }
    is(BLT)  { branchCond := muxA.asSInt <  muxB.asSInt }
    is(BGE)  { branchCond := muxA.asSInt >= muxB.asSInt }
    is(BLTU) { branchCond := muxA <  muxB }
    is(BGEU) { branchCond := muxA >= muxB }
  }

  //We calculate the real branch target
  val branchRealTarget = io.pcIn + io.imm

  //We calculate the real JALR target
  val jalrRealTarget = (muxA + io.imm) & "hFFFFFFFE".U(32.W)

  //We select the real jump target
  val jumpRealTarget = Mux(io.uop === JALR, jalrRealTarget, branchRealTarget)

  //We calculate the correct next PC for a branch
  val branchCorrectPC = Mux(branchCond, branchRealTarget, io.pcIn + 4.U)

  // << NEW in A06 >>
  //Why: We must detect if the BTB predicted the wrong direction
  val directionMispredicted = io.predictedTaken =/= branchCond

  // << NEW in A06 >>
  //Why: We also check target mismatch for safety
  val targetMispredicted = branchCond && io.predictedTaken && (io.predictedTarget =/= branchRealTarget)

  // << NEW in A06 >>
  //Why: Any wrong direction or wrong target must flush the pipeline
  val branchMispredicted = directionMispredicted || targetMispredicted

  // << CHANGED in A06 >>
  //Why: Correctly predicted conditional branches should not flush
  io.flush := (isBranch && branchMispredicted) || isJump

  // << CHANGED in A06 >>
  //Why: On misprediction, IF needs the correct PC
  io.branchTarget := Mux(isJump, jumpRealTarget, branchCorrectPC)

  // << NEW in A06 >>
  //Why: The BTB learns only from conditional branches
  io.btbUpdate       := isBranch && !io.inXcptInvalid
  io.btbUpdatePC     := io.pcIn
  io.btbUpdateTarget := branchRealTarget

  // << NEW in A06 >>
  //Why: The 2-bit predictor learns taken/not taken direction
  io.btbMispredicted := isBranch && directionMispredicted

  //We drive the normal EX outputs
  io.aluResult      := alu.io.aluResult
  io.outRD          := io.inRD
  io.outXcptInvalid := io.inXcptInvalid

  // << CHANGED in A06 >>
  //Why: Branches and NOP must not write into the Register File
  io.outRegWrite := !io.inXcptInvalid && (io.uop =/= NOP) && !isBranch
}