package core_tile

import chisel3._
import chisel3.experimental.ChiselEnum

//We define the micro operations used by the pipeline
object uopc extends ChiselEnum {

  //R-type operations
  val ADD  = Value
  val SUB  = Value
  val XOR  = Value
  val OR   = Value
  val AND  = Value
  val SLL  = Value
  val SRL  = Value
  val SRA  = Value
  val SLT  = Value
  val SLTU = Value

  //I-type operations
  val ADDI  = Value
  val XORI  = Value
  val ORI   = Value
  val ANDI  = Value
  val SLLI  = Value
  val SRLI  = Value
  val SRAI  = Value
  val SLTI  = Value
  val SLTIU = Value

  // << NEW in A06 >>
  //We keep the branch uops because the BTB works only with conditional branches
  val BEQ  = Value
  val BNE  = Value
  val BLT  = Value
  val BGE  = Value
  val BLTU = Value
  val BGEU = Value

  // << NEW in A06 >>
  //We keep jumps separate because they do not use the BTB
  val JAL  = Value
  val JALR = Value

  //Default operation
  val NOP = Value
}