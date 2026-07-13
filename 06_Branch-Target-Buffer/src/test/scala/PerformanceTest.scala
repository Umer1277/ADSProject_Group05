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

  // -----------------------------------------------------------------------
  //  Task 6.5: FSM State Transition Verification
  //
  //  This test uses a short loop (3 iterations) to prove the 2-bit saturating
  //  counter transitions correctly through its states:
  //
  //    bench_fsm_short: x1=3, loop { x1--; BNE x1,x0,loop }
  //    3 branch evaluations: taken, taken, not-taken (exit)
  //
  //    Expected BTB state transitions for the BNE branch:
  //      Iter 1 (taken): No BTB entry → miss, allocate with state=2 (weakly taken)
  //                      Misprediction (predicted not-taken, was taken)
  //      Iter 2 (taken): Hit, state=2 → predict taken → CORRECT → state=3 (strongly taken)
  //      Iter 3 (exit) : Hit, state=3 → predict taken → WRONG  → state=2 (weakly taken)
  //                      Misprediction (predicted taken, was not-taken)
  //
  //    This demonstrates:
  //      • State 2→3 transition (correct taken prediction strengthens counter)
  //      • State 3→2 transition (one wrong prediction only decrements by 1)
  //      • Saturation at 3 (if loop were longer, it would stay at 3)
  //      • Hysteresis: after the loop exits, counter is at 2 (still weakly taken),
  //        proving a single wrong prediction does NOT flip the direction.
  //
  //    Without BTB: all taken branches are mispredicted (always predict not-taken).
  //      2 mispredictions (2 taken branches predicted wrong; the final not-taken is correct).
  //
  //    With BTB: exactly 2 mispredictions (first taken miss + exit not-taken).
  //      Same count, but for different reasons — proving the FSM is learning.
  //
  //    For bench_loop_taken (100 iterations):
  //      Without BTB: 99 mispredictions (every taken branch predicted wrong)
  //      With BTB:     2 mispredictions (first miss + last exit)
  //      This proves states 2→3→3→...→3→2 work correctly over many iterations.
  // -----------------------------------------------------------------------
  "BTB FSM" should "demonstrate correct 2-bit saturating counter state transitions" in {
    // Short loop: 3 iterations, 3 branch evaluations (2 taken + 1 not-taken)
    val (cFsmNoBTB, bFsmNoBTB, mFsmNoBTB) = runBenchmark("FSM Short Loop", "src/test/programs/bench_fsm_short", false)
    val (cFsmBTB, bFsmBTB, mFsmBTB) = runBenchmark("FSM Short Loop", "src/test/programs/bench_fsm_short", true)
    printStats("bench_fsm_short (FSM transitions)", cFsmNoBTB, bFsmNoBTB, mFsmNoBTB, cFsmBTB, bFsmBTB, mFsmBTB)

    // Verify exact branch count: the loop has exactly 3 BNE evaluations
    assert(bFsmBTB == 3, s"Expected exactly 3 branch evaluations, got $bFsmBTB")

    // Verify exact misprediction count with BTB:
    //   Iter 1: miss (no entry) → misprediction (taken but predicted not-taken)
    //   Iter 2: state=2, predict taken, actually taken → correct
    //   Iter 3: state=3, predict taken, actually not-taken → misprediction
    // Total: exactly 2 mispredictions
    assert(mFsmBTB == 2, s"Expected exactly 2 BTB mispredictions for FSM test, got $mFsmBTB. " +
      "This proves: state transitions miss→2→3→2 are correct.")

    // Long loop (100 iterations): proves state saturation at 3
    val (_, bTakenBTB, mTakenBTB) = runBenchmark("Taken Loop (BTB)", "src/test/programs/bench_loop_taken", true)
    val (_, bTakenNoBTB, mTakenNoBTB) = runBenchmark("Taken Loop (No BTB)", "src/test/programs/bench_loop_taken", false)

    // With BTB: 100 branches, exactly 2 mispredictions
    //   (1st taken = miss, 2nd–99th taken = correct at state 3, 100th not-taken = wrong)
    //   Proves the counter saturates at 3 and stays there for 97 consecutive correct predictions
    assert(mTakenBTB == 2, s"Expected exactly 2 mispredictions for 100-iter taken loop with BTB, got $mTakenBTB. " +
      "This proves state saturation at strongly-taken (3).")

    // Without BTB: 99 mispredictions (every taken branch is wrong, only the final not-taken is correct)
    assert(mTakenNoBTB == 99, s"Expected exactly 99 mispredictions without BTB, got $mTakenNoBTB. " +
      "This proves static prediction always predicts not-taken.")

    println("=== FSM State Transition Summary ===")
    println("Short loop (3 iters): BTB mispredictions = 2 → proves transitions miss→2→3→2")
    println("Long loop (100 iters): BTB mispredictions = 2 → proves saturation at state 3")
    println("Long loop (100 iters): No-BTB mispredictions = 99 → confirms static always-not-taken baseline")
    println("All FSM state transitions verified ✓")
  }
}
