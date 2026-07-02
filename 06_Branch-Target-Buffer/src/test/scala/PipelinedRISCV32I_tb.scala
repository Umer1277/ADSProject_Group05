package PipelinedRV32I

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

//Start code
class PipelinedRISCV32I_tb extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "PipelinedRV32I with BTB"

  // << NEW in A06 >>
  //Why: BTB prediction changes timing, so we do not use fixed cycle counts
  def stepUntil(dut: PipelinedRV32I, expected: BigInt, maxCycles: Int, label: String): Unit = {
    var cycles = 0
    var found  = false

    //We wait until the expected result appears
    while (!found && cycles < maxCycles) {
      if (dut.io.result.peek().litValue == expected) {
        found = true
      } else {
        dut.clock.step(1)
        cycles += 1
      }
    }

    //We check that the expected value was reached
    assert(found, s"Checkpoint '$label' with expected value $expected was not reached")
  }

  it should "reach all A06 checkpoints and never raise an exception" in {
    test(new PipelinedRV32I("src/test/programs/BinaryFile")).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      //We disable the default timeout
      dut.clock.setTimeout(0)

      //We check the setup checkpoint
      stepUntil(dut, 100, 50, "setup done")
      assert(!dut.io.exception.peek().litToBoolean, "exception raised after setup")

      //We check the not-taken branch path
      stepUntil(dut, 201, 50, "branch not taken")

      //We check the taken branch path
      stepUntil(dut, 202, 50, "branch taken")

      //We check the backward loop branch
      stepUntil(dut, 203, 100, "backward loop done")

      //We check repeated branch reuse
      stepUntil(dut, 204, 150, "repeated branch pattern done")

      //We check forwarding into branch comparison
      stepUntil(dut, 205, 50, "forwarding into branch works")

      //We check JAL
      stepUntil(dut, 206, 50, "JAL forward jump landed")

      //We check JALR
      stepUntil(dut, 207, 50, "JALR jump landed")

      //We check the BTB set collision case
      stepUntil(dut, 208, 50, "BTB LRU collision handled")
      assert(!dut.io.exception.peek().litToBoolean, "exception raised after BTB LRU test")

      //We check the final sentinel
      stepUntil(dut, 1234, 50, "final sentinel")
      assert(!dut.io.exception.peek().litToBoolean, "exception raised at end of program")
    }
  }
}
