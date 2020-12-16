package freechips.rocketchip.tile

import chisel3._
import chisel3.util._
import chisel3.experimental._

import chisel3.experimental.{chiselName, NoChiselNamePrefix}
///*

object  Constant {
  def inputType = UInt(16.W)
  def outputType = UInt(16.W) 
  def accType = UInt(16.W)

  def OS = false.B
  def WS = true.B
  def df = OS
  def latency = 0
  def tileRows=C.N_ARRAY
  def tileColumns=C.N_ARRAY
  def meshColumns =1 

}

//only OS ,c= a * b
@chiselName
class PEIO extends Bundle{
	val in_a = Input(Constant.inputType)
	val in_b = Input(Constant.inputType)
	val in_c = Input(Constant.outputType)

	val out_a = Input(Constant.inputType)
	val out_b = Input(Constant.inputType)

	val out_c = Output(Constant.outputType)

	val in_propagate = Input(UInt(1.W))
	val out_propagate = Output(UInt(1.W))

	val in_valid = Input(Bool())
	val out_valid = Output(Bool())
}
class PE extends Module{

	val io =IO(new Bundle{
		val in_a = Input(Constant.inputType)
		val in_b = Input(Constant.inputType)
		val in_c = Input(Constant.outputType)

		val out_a = Output(Constant.inputType)
		val out_b = Output(Constant.inputType)

		val out_c = Output(Constant.outputType)

		val in_propagate = Input(UInt(1.W))
		val out_propagate = Output(UInt(1.W))

		val in_valid = Input(Bool())
		val out_valid = Output(Bool())		
	})
	val c1 = Reg(Constant.accType)
	val c2 = Reg(Constant.accType)

	val a = ShiftRegister(io.in_a,Constant.latency)


	val b = ShiftRegister(io.in_b,Constant.latency)
	io.out_b := b 
	io.out_a := a
	val c = ShiftRegister(io.in_c,Constant.latency)

	val valid =  ShiftRegister(io.in_valid,Constant.latency)
	io.out_valid := valid

	val propagate = ShiftRegister(io.in_propagate,Constant.latency)
	io.out_propagate := propagate

	def PROPAGATE = 1.U
	when(!valid){
		c1 := c1
		c2 := c2
	}
	//.otherwise{
		when(propagate === PROPAGATE){
			c1 := c1 + a * b
			io.out_c := c2
			c2 := c
		}.otherwise{
			c2 := c2 + a * b
			io.out_c := c1
			c1 := c
		//}
	}
}
@chiselName
class TileReg extends Module{
	val io =IO(new Bundle{
		val in_a = Input(Vec(Constant.tileRows, Constant.inputType))
		val in_b = Input(Vec(Constant.tileColumns,Constant.inputType))
		//val in_c = Input(Vec(Constant.tileColumns,Constant.outputType))

		val out_a = Output(Vec(Constant.tileRows,Constant.inputType))
		val out_b = Output(Vec(Constant.tileColumns,Constant.inputType))

		val out_c = Output(Vec(Constant.tileColumns,Constant.outputType))

		val in_propagate = Input(Vec(Constant.tileColumns,UInt(1.W)))
		val out_propagate = Output(Vec(Constant.tileColumns,UInt(1.W)))

		val in_valid = Input(Vec(Constant.tileColumns,Bool()))
		val out_valid = Output(Vec(Constant.tileColumns,Bool()))
	})

	val tile = Seq.fill(Constant.tileRows,Constant.tileColumns)(Module (new PE))
	val tileT = tile.transpose

	for ( r <- 0 until Constant.tileRows){
		tile(r).foldLeft(io.in_a(r),io.in_valid(r)){
			case ((in_a,valid),pe) =>{
				pe.io.in_a := RegEnable(in_a,valid)

				(pe.io.out_a,pe.io.out_valid)


			}
		}
	}
	for ( c <- 0 until Constant.tileRows){
		tileT(c).foldLeft((io.in_b(c),0.U,io.in_propagate(c),io.in_valid(c))){
			case ((in_b,in_c,in_propagate,in_valid),pe) =>{

				pe.io.in_b := RegEnable (in_b,in_valid)
				pe.io.in_c := RegEnable (in_c,in_valid)
				pe.io.in_propagate := RegEnable (in_propagate,in_valid)
				pe.io.in_valid := RegNext(in_valid) 


				(pe.io.out_b,
				pe.io.out_c,
				pe.io.out_propagate,
				pe.io.out_valid)

			}
		}
	}	
	for (r <- 0 until Constant.tileRows){
		io.out_a(r) := RegNext(tile(r)(Constant.tileColumns-1).io.out_a)
	}
	for ( c <- 0 until Constant.tileRows){
		io.out_b(c) := RegNext(tile(Constant.tileRows-1)(c).io.out_b)
		io.out_c(c) := RegNext(tile(Constant.tileRows-1)(c).io.out_c)
		io.out_propagate(c) := RegNext(tile(Constant.tileRows-1)(c).io.out_propagate)
		io.out_valid(c) := RegNext(tile(Constant.tileRows-1)(c).io.out_valid)
	}
				
}

//*/
@chiselName
class GEMM extends Module{
		val io =IO(new Bundle{
		val in_a = Input(Vec(Constant.tileRows, Constant.inputType))
		val in_b = Input(Vec(Constant.tileColumns,Constant.inputType))
		//val in_c = Input(Vec(Constant.tileColumns,Constant.outputType))

		//val out_a = Output(Vec(Constant.tileRows,Constant.inputType))
		//val out_b = Output(Vec(Constant.tileColumns,Constant.inputType))

		val out_c = Output(Vec(Constant.tileColumns,Constant.outputType))

		//val in_propagate = Input(Vec(Constant.tileColumns,UInt(1.W)))
		//val out_propagate = Output(Vec(Constant.tileColumns,UInt(1.W)))
		val in_valid = Input(Bool())
		//val out_valid = Output(Vec(Constant.tileColumns,Bool()))

		val out_valid = Output(Bool())
		val ready = Output(Bool())

		//val out_x = Output(UInt(32.W))
	})
	chisel3.dontTouch(io)
	def counter(n:UInt,valid:Bool) ={
    	val x=RegInit(UInt(32.W),0.U)
    	when(valid){
     	when(x===n-1.U){
            x := 0.U
        }.otherwise{
            x := x+1.U
        }   		
    }.otherwise{
    	x := 0.U
    }

        x
    }
    def counter_self_add (n_start:UInt,n:UInt) = {
    	val x=RegInit(UInt(32.W),0.U)
    	when( n_start >= 1.U ){
    			x := x + 1.U
    		}.elsewhen(x > 0.U && x < n){
				x := x + 1.U
    		}.otherwise{
    		x := 0.U
    	}
    	x
    }
    val check_valid = io.in_valid(0) & ! RegNext(io.in_valid(0))
    val my_valid = counter_self_add(check_valid,4.U*Constant.tileRows.U + 3.U) >= 1.U
    val out_start = counter(6.U*(Constant.tileRows).U,my_valid)
    //val out_start = counter(2.U*(Constant.tileRows).U,io.in_valid(0))

    val state = RegInit(0.U(8.W))
    val tile = Module (new TileReg)
    val tile_in_propagate = RegInit(VecInit(Seq.fill(Constant.tileRows)(0.U(1.W))))

    when(out_start > 2.U * Constant.tileRows.U + 3.U && out_start <= 4.U*Constant.tileRows.U + 2.U){
    	io.out_valid := 1.U
    }.otherwise{
    	io.out_valid := 0.U
    }


    def STATE_VOID = 0.U
    def STATE_COMP = 1.U
    def STATE_OUT = 2.U  



//*
    when(!my_valid){
    	state := STATE_VOID
    }.elsewhen(state === STATE_COMP && io.out_valid === 1.U){
    	state := STATE_OUT
    }.elsewhen(state === STATE_OUT && io.ready === 1.U){
    	state := STATE_VOID
    }.elsewhen(state === STATE_OUT && io.ready === 0.U){
    	state := STATE_OUT
    }
    .otherwise{
    	state := STATE_COMP
    }
//*/
    when(out_start > 4.U*Constant.tileRows.U + 2.U || state === STATE_VOID){
    	io.ready := 1.U
    }.otherwise{
    	io.ready := 0.U
    }
	val cnt_prop = counter(2.U*Constant.tileRows.U + 2.U,my_valid)

    val cur_indx = cnt_prop - Constant.tileRows.U - 1.U - 1.U 

//
	when (cnt_prop > Constant.tileRows.U + 1.U && cnt_prop <= 2.U*Constant.tileRows.U + 1.U ){
	for (i <- 0 until Constant.tileRows){
		when (i.U === cur_indx ){
			tile_in_propagate(cur_indx) := (1.U - tile_in_propagate(cur_indx))
		}.otherwise{
			tile_in_propagate(i) := tile_in_propagate(i)
		}
	}
	}   
	tile.io.in_propagate <> tile_in_propagate
    when(state === STATE_OUT || state === STATE_COMP){
    	for (i <- 0 until Constant.tileRows){
    		tile.io.in_valid(i) := 1.B
    	}
    }.otherwise{
    	for (i <- 0 until Constant.tileRows){
    		tile.io.in_valid(i) := 0.B
    	}
    }
    

    tile.io.in_a <> io.in_a
    tile.io.in_b <> io.in_b
    tile.io.out_c <> io.out_c

}