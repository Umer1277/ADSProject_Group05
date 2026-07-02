package core_tile

import chisel3._
import chisel3.util.experimental.loadMemoryFromFile

//Start code
class IF (BinaryFile: String) extends Module {
  val io = IO(new Bundle {
    // << CHANGED in A06 >>
    //Why: EX redirects the PC only when the prediction was wrong
    val flush        = Input(Bool())
    val branchTarget = Input(UInt(32.W))

    // << NEW in A06 >>
    //Why: IF needs the BTB result to predict conditional branches
    val btbValid        = Input(Bool())
    val btbTarget       = Input(UInt(32.W))
    val btbPredictTaken = Input(Bool())

    val instr = Output(UInt(32.W))
    val pcOut = Output(UInt(32.W))

    // << NEW in A06 >>
    //Why: EX needs to know what IF predicted for this instruction
    val predictedTaken  = Output(Bool())
    val predictedTarget = Output(UInt(32.W))
  })

  //We define the instruction memory
  val IMem = Mem(4096, UInt(32.W))
  loadMemoryFromFile(IMem, BinaryFile)

  //We define the program counter
  val PC = RegInit(0.U(32.W))

  //We fetch the instruction using word address
  val fetchedInstr = IMem(PC >> 2)

  // << NEW in A06 >>
  //Why: The BTB must be used only for conditional branches
  val opcode = fetchedInstr(6, 0)
  val isConditionalBranch = opcode === "b1100011".U

  // << NEW in A06 >>
  //Why: We use the BTB target only when the BTB predicts taken
  val useBTB = isConditionalBranch && io.btbValid && io.btbPredictTaken

  //We output the fetched instruction and its PC
  io.instr := fetchedInstr
  io.pcOut := PC

  // << NEW in A06 >>
  //Why: This prediction must travel with the instruction to EX
  io.predictedTaken  := useBTB
  io.predictedTarget := Mux(useBTB, io.btbTarget, PC + 4.U)

  // << CHANGED in A06 >>
  //Why: Wrong prediction correction has priority over BTB prediction
  when(io.flush) {
    PC := io.branchTarget
  }.elsewhen(useBTB) {
    PC := io.btbTarget
  }.otherwise {
    PC := PC + 4.U
  }
}