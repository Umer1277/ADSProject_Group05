package core_tile

import chisel3._
import chisel3.util._

import uopc._

//Start code
class PipelinedRV32Icore (BinaryFile: String) extends Module {
  val io = IO(new Bundle {
    val check_res = Output(UInt(32.W))
    val exception = Output(Bool())

    //We keep this output name for older wrappers or tests
    val isInvalid = Output(Bool())

    val totalCycles          = Output(UInt(32.W))
    val totalBranches        = Output(UInt(32.W))
    val mispredictedBranches = Output(UInt(32.W))
    val useBTB               = Input(Bool())
  })

  // Performance counters
  val totalCyclesCount          = RegInit(0.U(32.W))
  val totalBranchesCount        = RegInit(0.U(32.W))
  val mispredictedBranchesCount = RegInit(0.U(32.W))

  totalCyclesCount := totalCyclesCount + 1.U

  //We define the pipeline stages
  val if_stage    = Module(new IF(BinaryFile))
  val if_barrier  = Module(new IFBarrier)
  val id_stage    = Module(new ID)
  val id_barrier  = Module(new IDBarrier)
  val ex_stage    = Module(new EX)
  val ex_barrier  = Module(new EXBarrier)
  val mem_stage   = Module(new MEM)
  val mem_barrier = Module(new MEMBarrier)
  val wb_stage    = Module(new WB)
  val wb_barrier  = Module(new WBBarrier)

  //We define the hazard and prediction units
  val fwd_unit = Module(new ForwardingUnit)

  // << NEW in A06 >>
  //Why: Assignment 06 needs a Branch Target Buffer
  val btb = Module(new BTB)

  // << NEW in A06 >>
  //Why: The BTB checks the PC currently used by IF
  btb.io.PC := if_stage.io.pcOut

  // << NEW in A06 >>
  //Why: EX updates the BTB after it resolves a conditional branch
  btb.io.update       := ex_stage.io.btbUpdate
  btb.io.updatePC     := ex_stage.io.btbUpdatePC
  btb.io.updateTarget := ex_stage.io.btbUpdateTarget
  btb.io.mispredicted := ex_stage.io.btbMispredicted

  // Performance counters update
  when(ex_stage.io.btbUpdate) {
    totalBranchesCount := totalBranchesCount + 1.U
    when(ex_stage.io.btbMispredicted) {
      mispredictedBranchesCount := mispredictedBranchesCount + 1.U
    }
  }

  io.totalCycles          := totalCyclesCount
  io.totalBranches        := totalBranchesCount
  io.mispredictedBranches := mispredictedBranchesCount

  // << CHANGED in A06 >>
  //Why: IF now uses EX correction and BTB prediction
  if_stage.io.flush        := ex_stage.io.flush
  if_stage.io.branchTarget := ex_stage.io.branchTarget

  // << NEW in A06 >>
  //Why: IF needs the BTB prediction to choose the next PC early
  if_stage.io.btbValid        := btb.io.valid
  if_stage.io.btbTarget       := btb.io.target
  if_stage.io.btbPredictTaken := btb.io.predictTaken
  if_stage.io.useBTB          := io.useBTB

  //We connect IF to IF barrier
  if_barrier.io.inInstr := if_stage.io.instr
  if_barrier.io.inPC    := if_stage.io.pcOut

  // << NEW in A06 >>
  //Why: The prediction must travel with the fetched instruction
  if_barrier.io.inPredictedTaken  := if_stage.io.predictedTaken
  if_barrier.io.inPredictedTarget := if_stage.io.predictedTarget

  // << CHANGED in A06 >>
  //Why: We flush only when EX detects a wrong prediction
  if_barrier.io.flush := ex_stage.io.flush

  //We connect IF barrier to ID
  id_stage.io.instr := if_barrier.io.outInstr
  id_stage.io.pcIn  := if_barrier.io.outPC

  //We connect ID to ID barrier
  id_barrier.io.inUOP         := id_stage.io.uop
  id_barrier.io.inRD          := id_stage.io.rd
  id_barrier.io.inOperandA    := id_stage.io.operandA
  id_barrier.io.inOperandB    := id_stage.io.operandB
  id_barrier.io.inXcptInvalid := id_stage.io.xcptInvalid

  //We connect register indexes for forwarding
  id_barrier.io.inRS1 := id_stage.io.rs1Out
  id_barrier.io.inRS2 := id_stage.io.rs2Out

  //We connect PC and immediate for branch and jump target calculation
  id_barrier.io.inPC  := id_stage.io.pcOut
  id_barrier.io.inImm := id_stage.io.immOut

  // << NEW in A06 >>
  //Why: EX needs the original IF prediction
  id_barrier.io.inPredictedTaken  := if_barrier.io.outPredictedTaken
  id_barrier.io.inPredictedTarget := if_barrier.io.outPredictedTarget

  // << CHANGED in A06 >>
  //Why: Wrong-path instructions must be killed after a wrong prediction
  id_barrier.io.flush := ex_stage.io.flush

  //We connect ID barrier to EX
  ex_stage.io.uop           := id_barrier.io.outUOP
  ex_stage.io.inRD          := id_barrier.io.outRD
  ex_stage.io.operandA      := id_barrier.io.outOperandA
  ex_stage.io.operandB      := id_barrier.io.outOperandB
  ex_stage.io.inXcptInvalid := id_barrier.io.outXcptInvalid

  //We connect source register indexes to EX
  ex_stage.io.rs1 := id_barrier.io.outRS1
  ex_stage.io.rs2 := id_barrier.io.outRS2

  //We connect PC and immediate to EX
  ex_stage.io.pcIn := id_barrier.io.outPC
  ex_stage.io.imm  := id_barrier.io.outImm

  // << NEW in A06 >>
  //Why: EX compares prediction vs real branch result
  ex_stage.io.predictedTaken  := id_barrier.io.outPredictedTaken
  ex_stage.io.predictedTarget := id_barrier.io.outPredictedTarget

  //We connect the Forwarding Unit inputs
  fwd_unit.io.rs1_EX := id_barrier.io.outRS1
  fwd_unit.io.rs2_EX := id_barrier.io.outRS2

  fwd_unit.io.rd_MEM   := ex_barrier.io.outRD
  fwd_unit.io.wrEn_MEM := ex_barrier.io.outRegWrite

  fwd_unit.io.rd_WB   := mem_barrier.io.outRD
  fwd_unit.io.wrEn_WB := mem_barrier.io.outRegWrite

  //We connect the Forwarding Unit outputs to EX
  ex_stage.io.forwardA := fwd_unit.io.forwardA
  ex_stage.io.forwardB := fwd_unit.io.forwardB

  //We connect forwarding data sources
  ex_stage.io.memData := ex_barrier.io.outAluResult
  ex_stage.io.wbData  := mem_barrier.io.outAluResult

  //We connect EX to EX barrier
  ex_barrier.io.inAluResult   := ex_stage.io.aluResult
  ex_barrier.io.inRD          := ex_stage.io.outRD
  ex_barrier.io.inXcptInvalid := ex_stage.io.outXcptInvalid
  ex_barrier.io.inRegWrite    := ex_stage.io.outRegWrite

  //We connect EX barrier to MEM
  mem_stage.io.aluResult   := ex_barrier.io.outAluResult
  mem_stage.io.rd          := ex_barrier.io.outRD
  mem_stage.io.xcptInvalid := ex_barrier.io.outXcptInvalid
  mem_stage.io.inRegWrite  := ex_barrier.io.outRegWrite

  //We connect MEM to MEM barrier
  mem_barrier.io.inAluResult := mem_stage.io.aluResultOut
  mem_barrier.io.inRD        := mem_stage.io.rdOut
  mem_barrier.io.inException := mem_stage.io.outXcptInvalid
  mem_barrier.io.inRegWrite  := mem_stage.io.outRegWrite

  //We connect MEM barrier to WB
  wb_stage.io.aluResult  := mem_barrier.io.outAluResult
  wb_stage.io.rd         := mem_barrier.io.outRD
  wb_stage.io.exception  := mem_barrier.io.outException
  wb_stage.io.inRegWrite := mem_barrier.io.outRegWrite

  //We connect WB to WB barrier
  wb_barrier.io.inCheckRes    := wb_stage.io.check_res
  wb_barrier.io.inXcptInvalid := wb_stage.io.outException

  //We connect WB to the Register File inside ID
  id_stage.io.req_3 := wb_stage.io.regFileReq

  //We connect the top-level outputs
  io.check_res := wb_barrier.io.outCheckRes
  io.exception := wb_barrier.io.outXcptInvalid
  io.isInvalid := wb_barrier.io.outXcptInvalid
}