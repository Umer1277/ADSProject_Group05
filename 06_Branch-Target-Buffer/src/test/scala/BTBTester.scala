package PipelinedRV32I

import chisel3._
import chiseltest._
import core_tile._
import org.scalatest.flatspec.AnyFlatSpec

// BTB 2-bit state values (as raw UInt):
//   0 = strongNotTaken, 1 = weakNotTaken, 2 = weakTaken, 3 = strongTaken
// predictTaken is driven by bit[1] of the state:
//   state 0 (00) → predictTaken = false
//   state 1 (01) → predictTaken = false
//   state 2 (10) → predictTaken = true
//   state 3 (11) → predictTaken = true

class BTBTester extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "BTB"

  // -----------------------------------------------------------------------
  // Test 1: On reset all entries are invalid → always miss
  // -----------------------------------------------------------------------
  it should "miss on reset" in {
    test(new BTB()).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      dut.io.update.poke(false.B)
      dut.io.mispredicted.poke(false.B)
      dut.io.updatePC.poke(0.U)
      dut.io.updateTarget.poke(0.U)

      dut.io.PC.poke("h00000010".U)
      dut.clock.step(1)
      dut.io.valid.expect(false.B)
      dut.io.predictTaken.expect(false.B)
      dut.io.target.expect(0.U)

      dut.io.PC.poke("h00000030".U)
      dut.clock.step(1)
      dut.io.valid.expect(false.B)
      dut.io.predictTaken.expect(false.B)
      dut.io.target.expect(0.U)
    }
  }

  // -----------------------------------------------------------------------
  // Test 2: Insert one entry → lookup should hit
  // New entry with mispredicted=false → state = weakNotTaken (1) → predictTaken=false
  // -----------------------------------------------------------------------
  it should "insert one entry and then hit on lookup" in {
    test(new BTB()).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      // idle defaults
      dut.io.update.poke(false.B)
      dut.io.mispredicted.poke(false.B)
      dut.io.updatePC.poke(0.U)
      dut.io.updateTarget.poke(0.U)
      dut.io.PC.poke(0.U)
      dut.clock.step(1)

      // insert PC=0x10, target=0x80, mispredicted=false → state=weakNotTaken(1)
      dut.io.update.poke(true.B)
      dut.io.updatePC.poke("h00000010".U)
      dut.io.updateTarget.poke("h00000080".U)
      dut.io.mispredicted.poke(false.B)
      dut.clock.step(1)

      dut.io.update.poke(false.B)
      dut.io.PC.poke("h00000010".U)
      dut.clock.step(1)

      dut.io.valid.expect(true.B)
      dut.io.target.expect("h00000080".U)
      dut.io.predictTaken.expect(false.B)   // state=1 (weakNotTaken) → bit[1]=0 → not taken
    }
  }

  // -----------------------------------------------------------------------
  // Test 3: Two entries in the same set (same PC[4:2], different tags)
  // 0x10 and 0x30 both map to set index = PC[4:2] = 100 = 4
  // -----------------------------------------------------------------------
  it should "use both ways for the same set" in {
    test(new BTB()).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      dut.io.update.poke(false.B)
      dut.io.mispredicted.poke(false.B)
      dut.io.updatePC.poke(0.U)
      dut.io.updateTarget.poke(0.U)
      dut.io.PC.poke(0.U)
      dut.clock.step(1)

      // first entry → way0: mispredicted=false → state=weakNotTaken(1)
      dut.io.update.poke(true.B)
      dut.io.updatePC.poke("h00000010".U)
      dut.io.updateTarget.poke("h00000080".U)
      dut.io.mispredicted.poke(false.B)
      dut.clock.step(1)

      // second entry → way1: mispredicted=true → state=weakTaken(2)
      dut.io.updatePC.poke("h00000030".U)
      dut.io.updateTarget.poke("h00000090".U)
      dut.io.mispredicted.poke(true.B)
      dut.clock.step(1)

      dut.io.update.poke(false.B)

      // lookup first entry
      dut.io.PC.poke("h00000010".U)
      dut.clock.step(1)
      dut.io.valid.expect(true.B)
      dut.io.target.expect("h00000080".U)
      dut.io.predictTaken.expect(false.B)   // state=1 (weakNotTaken) → predictTaken=false

      // lookup second entry
      dut.io.PC.poke("h00000030".U)
      dut.clock.step(1)
      dut.io.valid.expect(true.B)
      dut.io.target.expect("h00000090".U)
      dut.io.predictTaken.expect(true.B)    // state=2 (weakTaken) → predictTaken=true
    }
  }

  // -----------------------------------------------------------------------
  // Test 4: LRU eviction — when both ways are full, evict least recently used
  // 0x10, 0x30, 0x50 all map to same set (PC[4:2] = 100 = 4)
  // -----------------------------------------------------------------------
  it should "evict the least recently used entry" in {
    test(new BTB()).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      dut.io.update.poke(false.B)
      dut.io.mispredicted.poke(false.B)
      dut.io.updatePC.poke(0.U)
      dut.io.updateTarget.poke(0.U)
      dut.io.PC.poke(0.U)
      dut.clock.step(1)

      // fill way0 with 0x10
      dut.io.update.poke(true.B)
      dut.io.updatePC.poke("h00000010".U)
      dut.io.updateTarget.poke("h00000080".U)
      dut.io.mispredicted.poke(false.B)
      dut.clock.step(1)

      // fill way1 with 0x30
      dut.io.updatePC.poke("h00000030".U)
      dut.io.updateTarget.poke("h00000090".U)
      dut.io.mispredicted.poke(true.B)
      dut.clock.step(1)

      // access 0x30 → makes 0x30 most recently used → lruWay points to way0 (0x10)
      dut.io.update.poke(false.B)
      dut.io.PC.poke("h00000030".U)
      dut.clock.step(1)
      dut.io.valid.expect(true.B)
      dut.io.target.expect("h00000090".U)

      // access 0x10 → makes 0x10 most recently used → lruWay points to way1 (0x30)
      dut.io.update.poke(false.B)
      dut.io.PC.poke("h00000010".U)
      dut.clock.step(1)
      dut.io.valid.expect(true.B)
      dut.io.target.expect("h00000080".U)

      // insert 0x50 → both ways full → evict LRU = way1 (0x30)
      dut.io.update.poke(true.B)
      dut.io.updatePC.poke("h00000050".U)
      dut.io.updateTarget.poke("h000000A0".U)
      dut.io.mispredicted.poke(false.B)
      dut.clock.step(1)

      dut.io.update.poke(false.B)

      // 0x10 should still be there
      dut.io.PC.poke("h00000010".U)
      dut.clock.step(1)
      dut.io.valid.expect(true.B)
      dut.io.target.expect("h00000080".U)

      // 0x30 should be evicted
      dut.io.PC.poke("h00000030".U)
      dut.clock.step(1)
      dut.io.valid.expect(false.B)

      // 0x50 should be present
      dut.io.PC.poke("h00000050".U)
      dut.clock.step(1)
      dut.io.valid.expect(true.B)
      dut.io.target.expect("h000000A0".U)

      // --- test set 0 LRU ---
      dut.io.update.poke(true.B)
      dut.io.updatePC.poke("h00000000".U)
      dut.io.updateTarget.poke("h00000060".U)
      dut.io.mispredicted.poke(false.B)
      dut.clock.step(1)

      dut.io.updatePC.poke("h00000020".U)
      dut.io.updateTarget.poke("h00000020".U)
      dut.io.mispredicted.poke(true.B)
      dut.clock.step(1)

      dut.io.update.poke(false.B)
      dut.io.PC.poke("h00000020".U)
      dut.clock.step(1)
      dut.io.valid.expect(true.B)
      dut.io.target.expect("h00000020".U)

      dut.io.update.poke(false.B)
      dut.io.PC.poke("h00000000".U)
      dut.clock.step(1)
      dut.io.valid.expect(true.B)
      dut.io.target.expect("h00000060".U)

      dut.io.update.poke(true.B)
      dut.io.updatePC.poke("h00000040".U)
      dut.io.updateTarget.poke("h00000003".U)
      dut.io.mispredicted.poke(false.B)
      dut.clock.step(1)

      dut.io.update.poke(false.B)

      dut.io.PC.poke("h00000000".U)
      dut.clock.step(1)
      dut.io.valid.expect(true.B)
      dut.io.target.expect("h00000060".U)

      dut.io.PC.poke("h00000020".U)
      dut.clock.step(1)
      dut.io.valid.expect(false.B)

      dut.io.PC.poke("h00000040".U)
      dut.clock.step(1)
      dut.io.valid.expect(true.B)
      dut.io.target.expect("h00000003".U)
    }
  }

  // -----------------------------------------------------------------------
  // Test 5: 2-bit saturating counter FSM state transitions
  //
  // Your BTB uses raw 2-bit UInt states (no enum), observable via predictTaken:
  //   state 0 (strongNotTaken) → predictTaken = false
  //   state 1 (weakNotTaken)   → predictTaken = false
  //   state 2 (weakTaken)      → predictTaken = true
  //   state 3 (strongTaken)    → predictTaken = true
  //
  // State transitions are driven by mispredicted signal sent from EX stage.
  // The BTB infers actualTaken = (oldPredictTaken XOR mispredicted).
  //
  // Sequence tested:
  //   Insert:  mispredicted=false → state=weakNotTaken(1)   predictTaken=false
  //   Update1: mispredicted=true  → actualTaken=true  → state=weakTaken(2)    predictTaken=true
  //   Update2: mispredicted=false → actualTaken=true  → state=strongTaken(3)  predictTaken=true
  //   Update3: mispredicted=true  → actualTaken=false → state=weakTaken(2)    predictTaken=true
  //   Update4: mispredicted=true  → actualTaken=false → state=weakNotTaken(1) predictTaken=false
  // -----------------------------------------------------------------------
  it should "update the 2-bit predictor state correctly" in {
    test(new BTB()).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      dut.io.update.poke(false.B)
      dut.io.mispredicted.poke(false.B)
      dut.io.updatePC.poke(0.U)
      dut.io.updateTarget.poke(0.U)
      dut.io.PC.poke(0.U)
      dut.clock.step(1)

      // Insert: mispredicted=false → new entry gets state=weakNotTaken(1)
      dut.io.update.poke(true.B)
      dut.io.updatePC.poke("h00000010".U)
      dut.io.updateTarget.poke("h00000080".U)
      dut.io.mispredicted.poke(false.B)
      dut.clock.step(1)

      // Verify state=weakNotTaken → predictTaken=false
      dut.io.update.poke(false.B)
      dut.io.PC.poke("h00000010".U)
      dut.clock.step(1)
      dut.io.valid.expect(true.B)
      dut.io.predictTaken.expect(false.B)   // state=1 (weakNotTaken)

      // Update1: mispredicted=true → oldPredict=false, actualTaken = false XOR true = true
      //          state: weakNotTaken(1) → weakTaken(2)
      dut.io.update.poke(true.B)
      dut.io.updatePC.poke("h00000010".U)
      dut.io.updateTarget.poke("h00000080".U)
      dut.io.mispredicted.poke(true.B)
      dut.clock.step(1)

      // Verify state=weakTaken → predictTaken=true
      dut.io.update.poke(false.B)
      dut.io.PC.poke("h00000010".U)
      dut.clock.step(1)
      dut.io.predictTaken.expect(true.B)    // state=2 (weakTaken)

      // Update2: mispredicted=false → oldPredict=true, actualTaken = true XOR false = true
      //          state: weakTaken(2) → strongTaken(3)
      dut.io.update.poke(true.B)
      dut.io.updatePC.poke("h00000010".U)
      dut.io.updateTarget.poke("h00000080".U)
      dut.io.mispredicted.poke(false.B)
      dut.clock.step(1)

      // Verify state=strongTaken → predictTaken=true
      dut.io.update.poke(false.B)
      dut.io.PC.poke("h00000010".U)
      dut.clock.step(1)
      dut.io.predictTaken.expect(true.B)    // state=3 (strongTaken)

      // Update3: mispredicted=true → oldPredict=true, actualTaken = true XOR true = false
      //          state: strongTaken(3) → weakTaken(2)
      dut.io.update.poke(true.B)
      dut.io.updatePC.poke("h00000010".U)
      dut.io.updateTarget.poke("h00000080".U)
      dut.io.mispredicted.poke(true.B)
      dut.clock.step(1)

      // Verify state=weakTaken → predictTaken=true
      dut.io.update.poke(false.B)
      dut.io.PC.poke("h00000010".U)
      dut.clock.step(1)
      dut.io.predictTaken.expect(true.B)    // state=2 (weakTaken)

      // Update4: mispredicted=true → oldPredict=true, actualTaken = true XOR true = false
      //          state: weakTaken(2) → weakNotTaken(1)
      dut.io.update.poke(true.B)
      dut.io.updatePC.poke("h00000010".U)
      dut.io.updateTarget.poke("h00000080".U)
      dut.io.mispredicted.poke(true.B)
      dut.clock.step(1)

      // Verify state=weakNotTaken → predictTaken=false
      dut.io.update.poke(false.B)
      dut.io.PC.poke("h00000010".U)
      dut.clock.step(1)
      dut.io.predictTaken.expect(false.B)   // state=1 (weakNotTaken)
    }
  }
}