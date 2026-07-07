package PipelinedRV32I

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class PerformanceTest extends AnyFlatSpec with ChiselScalatestTester {

  def runBenchmark(name: String, path: String, useBTB: Boolean): (Int, Int, Int) = {
    var cycles = 0
    var branches = 0
    var mispredicts = 0
    
    test(new PipelinedRV32I(path)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      dut.io.useBTB.poke(useBTB.B)
      dut.clock.setTimeout(0)

      // Run until we see the final result 123 in x3 (which we've mapped to io.result in our program)
      // For bench_loop_simple, we expect 123 to appear in WB at the end.
      var timeout = 2000
      var found = false
      while (!found && timeout > 0) {
        dut.clock.step(1)
        cycles = dut.io.totalCycles.peek().litValue.toInt
        branches = dut.io.totalBranches.peek().litValue.toInt
        mispredicts = dut.io.mispredictedBranches.peek().litValue.toInt
        
        if (dut.io.result.peek().litValue == 123) {
          found = true
        }
        timeout -= 1
      }
      
      // Step a few more times to be sure
      dut.clock.step(5)
      cycles = dut.io.totalCycles.peek().litValue.toInt
      branches = dut.io.totalBranches.peek().litValue.toInt
      mispredicts = dut.io.mispredictedBranches.peek().litValue.toInt
    }
    (cycles, branches, mispredicts)
  }

  def printStats(name: String, cNoBTB: Int, bNoBTB: Int, mNoBTB: Int, cBTB: Int, bBTB: Int, mBTB: Int): Unit = {
    println(s"--- Performance Evaluation: $name ---")
    println(s"Configuration | Cycles | Branches | Mispredicts | Accuracy")
    println(s"--------------|--------|----------|-------------|---------")
    val accNoBTB = if (bNoBTB > 0) 100.0 * (bNoBTB - mNoBTB) / bNoBTB else 0.0
    val accBTB = if (bBTB > 0) 100.0 * (bBTB - mBTB) / bBTB else 0.0
    
    println(f"No BTB        | $cNoBTB%6d | $bNoBTB%8d | $mNoBTB%11d | $accNoBTB%7.2f%%")
    println(f"With BTB      | $cBTB%6d | $bBTB%8d | $mBTB%11d | $accBTB%7.2f%%")
    println(s"--------------------------------------------------")
  }

  "BTB" should "be evaluated on various benchmarks" in {
    // Benchmark 1: Simple Loop (primarily not taken branch)
    val (c1NoBTB, b1NoBTB, m1NoBTB) = runBenchmark("Simple Loop (Not Taken)", "src/test/programs/bench_loop_simple", false)
    val (c1BTB, b1BTB, m1BTB) = runBenchmark("Simple Loop (Not Taken)", "src/test/programs/bench_loop_simple", true)
    printStats("bench_loop_simple", c1NoBTB, b1NoBTB, m1NoBTB, c1BTB, b1BTB, m1BTB)

    // Benchmark 2: Simple Loop (primarily taken branch)
    val (c2NoBTB, b2NoBTB, m2NoBTB) = runBenchmark("Simple Loop (Taken)", "src/test/programs/bench_loop_taken", false)
    val (c2BTB, b2BTB, m2BTB) = runBenchmark("Simple Loop (Taken)", "src/test/programs/bench_loop_taken", true)
    printStats("bench_loop_taken", c2NoBTB, b2NoBTB, m2NoBTB, c2BTB, b2BTB, m2BTB)

    // Benchmark 3: Nested Loop
    val (c3NoBTB, b3NoBTB, m3NoBTB) = runBenchmark("Nested Loop", "src/test/programs/bench_loop_nested", false)
    val (c3BTB, b3BTB, m3BTB) = runBenchmark("Nested Loop", "src/test/programs/bench_loop_nested", true)
    printStats("bench_loop_nested", c3NoBTB, b3NoBTB, m3NoBTB, c3BTB, b3BTB, m3BTB)

    assert(c2BTB < c2NoBTB, "BTB should significantly reduce the number of cycles for predominantly taken branches")
  }
}
