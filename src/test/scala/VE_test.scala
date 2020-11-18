package ve

import chisel3._
import chisel3.util._
import chisel3.iotesters.{PeekPokeTester,ChiselFlatSpec}
//*
class VETests(c:VE) extends PeekPokeTester(c){
    step(5)
    poke(c.io.in1.valid,1)
    poke(c.io.in2.valid,1)
    poke(c.io.opcode.valid,1)

    poke(c.io.in1.bits(1),(3))
    poke(c.io.in1.bits(0),(3))
    poke(c.io.in2.bits(0),(3))
    poke(c.io.in1.bits(1),(3))
    poke(c.io.opcode.bits,ALU.ADD)
    //assert(c.io.out.bits(1)==6)
    step(5)

}
//*/
class VEArrayTests(c:VEArray) extends PeekPokeTester(c){
    step(5)
    poke(c.io.in1.valid,1)
    poke(c.io.in2.valid,1)
    poke(c.io.opcode.valid,1)
    poke(c.io.out.valid ,1.B)
    poke(c.io.in1.bits(1)(0),(3))
    poke(c.io.in1.bits(1)(1),(2))

    poke(c.io.in2.bits(1)(0),(1))
    poke(c.io.in2.bits(1)(1),(4))
    poke(c.io.opcode.bits,ALU.AND)
    //assert(c.io.out.bits(1)(0)==6)
    step(5)

}
///*
class VETester extends ChiselFlatSpec{
    "running with --generate-vcd-output on" should "create a vcd file from your test" in {
    	iotesters.Driver.execute(Array(
    		"--generate-vcd-output","on",
    		"--target-dir","genarated/VE",
    		"--top-name","VE_vcd"),()=> new VE()){
    		c=>new VETests(c)}should be(true)
        }

}
//*/
class VEArrayTester extends ChiselFlatSpec{
    "running with --generate-vcd-output on" should "create a vcd file from your test" in {
    	iotesters.Driver.execute(Array(
    		"--generate-vcd-output","on",
    		"--target-dir","genarated/VEArray",
    		"--top-name","VEArray_vcd"),()=> new VEArray()){
    		c=>new VEArrayTests(c)}should be(true)
    	}
}