package ve

import chisel3._
import chisel3.util._
import chisel3.experimental.{chiselName, NoChiselNamePrefix}

object ALU{
    def VE_SIZE = 2
    def VE_NUM = 2
    def ADD = 1.U
    def SUB = 2.U
    def MUL = 3.U
    def DIV = 4.U

    def MAX = 5.U
    def MIN = 6.U

    def AND = 7.U
    def OR = 8.U
}
@chiselName
class VE extends Module{
    val io = IO(new Bundle{
        val in1 = Flipped(Decoupled(Vec(ALU.VE_SIZE, SInt(8.W))))
        val in2 = Flipped(Decoupled(Vec(ALU.VE_SIZE, SInt(8.W))))
        val opcode = Flipped(Decoupled(UInt(8.W)))
        val out = Decoupled(Vec(ALU.VE_SIZE, SInt(8.W)))
    })
    val regs = RegInit(VecInit(Seq.fill(ALU.VE_SIZE)(0.S(8.W))))
    when(io.in1.fire() && io.in2.fire() && io.opcode.fire()){
    	 for( i <- 0 until ALU.VE_SIZE){
    	 	switch(io.opcode.bits) {
    	 		is(ALU.ADD) {regs(i) := io.in1.bits(i) + io.in2.bits(i)}
    	 		is(ALU.SUB) {regs(i) := io.in1.bits(i) - io.in2.bits(i)}
    	 		is(ALU.MUL) {regs(i) := io.in1.bits(i) * io.in2.bits(i)}
    	 		is(ALU.DIV) {regs(i) := io.in1.bits(i) / io.in2.bits(i)}

    	 		is(ALU.MAX) {regs(i) := Mux(io.in1.bits(i) > io.in2.bits(i) ,io.in1.bits(i),io.in2.bits(i)) }
    	 		is(ALU.MIN) {regs(i) := Mux(io.in1.bits(i) < io.in2.bits(i) ,io.in1.bits(i),io.in2.bits(i)) }

    	 		is(ALU.AND) {regs(i) := io.in1.bits(i) & io.in2.bits(i)}
    	 		is(ALU.OR) {regs(i) := io.in1.bits(i) | io.in2.bits(i)}    	 		
    	 }
    }
}
    io.out.bits <> regs
    io.out.valid := 1.B
    io.in1.ready := 1.B
    io.in2.ready := 1.B
    io.opcode.ready := 1.B
}

class VEArray extends Module{
     val io = IO(new Bundle{
        val in1 = Flipped(Decoupled(Vec(ALU.VE_NUM,Vec(ALU.VE_SIZE, SInt(8.W)))))
        val in2 = Flipped(Decoupled(Vec(ALU.VE_NUM,Vec(ALU.VE_SIZE, SInt(8.W)))))
        val opcode = Flipped(Decoupled(UInt(8.W)))
        val out = Decoupled(Vec(ALU.VE_NUM,Vec(ALU.VE_SIZE, SInt(8.W))))
	})
	val veArray = Seq.fill(ALU.VE_NUM)(Module (new VE))

	when(io.in1.fire() && io.in2.fire() && io.opcode.fire()){
	 	for(i <- 0 until ALU.VE_NUM){
            veArray(i).io.in1.valid := 1.B
	 		veArray(i).io.in2.valid := 1.B
	 		veArray(i).io.opcode.valid := 1.B
            veArray(i).io.out.ready := 1.B

	 		veArray(i).io.in1.bits := io.in1.bits(i)
	 		veArray(i).io.in2.bits := io.in2.bits(i)
            veArray(i).io.opcode.bits := io.opcode.bits
             
	 		io.out.bits(i) <> veArray(i).io.out.bits
	 	}
     }.otherwise{
 	 	for(i <- 0 until ALU.VE_NUM){
            veArray(i).io.in1.valid := 0.B
	 		veArray(i).io.in2.valid := 0.B
	 		veArray(i).io.opcode.valid := 0.B
            veArray(i).io.out.ready := 0.B

	 		veArray(i).io.in1.bits := io.in1.bits(i)
	 		veArray(i).io.in2.bits := io.in2.bits(i)
            veArray(i).io.opcode.bits := io.opcode.bits
             
	 		io.out.bits(i) <> veArray(i).io.out.bits

	 	}        
     }
     
    io.out.valid := 1.B
    io.opcode.ready := 1.B
    io.in1.ready := 1.B
    io.in2.ready := 1.B     

}