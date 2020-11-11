package freechips.rocketchip.tile

import chisel3._
import chisel3.utils._
import chisel3.experimental._

//class name capital
class PEControl extends Bundle{
    //define two constant
    val propagate = UInt(1.W)
    val dataflow = UInt(1.W)
}

class PE(df:Bool ,latency: Int) extends  Module{

  def inputType = UInt(8.W)
  def outputType = UInt(19.W) 
  def accType = UInt(32.W)

    val io = IO(
        new Bundle{
            val in_a = Input(inputType)
            val in_b = Input(outputType)
            val in_d = Input(outputType)

            val out_a = Output(inputType)
            val out_b = Output(outputType)
            val out_c = Output(outputType)

            val in_control = Input(new PEControl)
            val out_control = Output(new PEControl)

            val in_valid = Input(Bool())
            val out_valid = Output(Bool())
        }
    )

    val cType = if(df===true.B) inputType else accType

    val a = ShiftRegister(io.in_a,latency)
    val b = ShiftRegister(io.in_b,latency)
    val d = ShiftRegister(io.in_d,latency)
    val c1 = Reg(cType)
    val c2 = Reg(cType)
    val dataflow = ShiftRegister(io.in_control.dataflow,latency)
    val propagate = ShiftRegister(io.in_control.propagate,latency)
    val valid = ShiftRegister(io.in_control.valid,latency)

    io.out_a := a
    io.out_control.dataflow := dataflow
    io.out_control.propagate := propagate
    io.out_valid := valid

    // Which dataflow are we using?
  val OUTPUT_STATIONARY = false.B
    val WEIGHT_STATIONARY = true.B

    // Is c1 being computed on, or propagated forward (in the output-stationary dataflow)?
    val COMPUTE = 0.U(1.W)
    val PROPAGATE = 1.U(1.W)

//hardware compare use when and ===
    when (dataflow === OUTPUT_STATIONARY){
      when(propagate === PROPAGATE){
        io.out_b := b 
        io.out_c := (c1).clippedToWidthOf(outputType)
        c2 := c2 + a * b.asTypeOf(inputType)
        c1 := d.withWidthOf(cType)
      }.otherwise{
        io.out_b := b
        io.out_c := (c2).clippedToWidthOf(outputType)
        c1 := c1 + a * b.asTypeOf(inputType)
        c2 := d.withWidthOf(cType)
      }
    }.elsewhen(dataflow === WEIGHT_STATIONARY){
      when(propagate === PROPAGATE){
        io.out_c := c1
        c1 := d
        io.out_b := b + a * c1.asTypeOf(inputType)
      }.otherwise{
        io.out_c := c2
        c2 := d
        io.out_b := b + a * c2.asTypeOf(inputType)
      }
    }.otherwise{
      assert(false.B,"unknown dataflow")
      io.out_c := DontCare
      io.out_b := DontCare
    }

    when(!valid){
      c1 := c1
      c2 := c2
    }
}

class Tile(df: Bool, latency: Int, rows: Int, columns: Int) extends Module{
  val io = IO(new Bundle{
    val in_a = Input(Vec(rows,inputType))
    val in_b = Input(Vec(columns,outputType))
    val in_d = Input(Vec(columns,outputType))

    val out_a = Output(Vec(rows,inputType))
    val out_b = Output(Vec(columns,outputType))
    val out_c = Output(Vec(columns,outputType))

    val in_control = Input(Vec(columns,new PEControl))
    val out_control = Output(Vec(columns,new PEControl))

    val in_valid = Input(Vec(columns,Bool()))
    val out_valid = Output(Vec(columns,Bool()))
  })

  val tile = Seq.fill(rows,columns)(Module (new PE(df,latency)))
  val tileT = tile.transpose

  for(r <- 0 until rows){
    tile(r).foldLeft(io.in_a(r)){
      case(in_a,pe) => {
        pe.io.in_a := in_a
        pe.io.out_a
      }
    }
  }

  for(c <- 0 until columns){
    tileT(c).foldLeft(io.in_b(c)){
      case (in_b,pe) => {
        pe.io.in_b := in_b
        pe.io.out_b
      } 
    }
  }

  for(c <- 0 until columns){
    tileT(c).foldLeft(io.in_d(c)){
      case (in_d,pe) => {
        pe.io.in_d := in_d
        pe.io.out_c
      }
    }
  }

  for(c <- 0 until columns){
    tileT(c).foldLeft(io.in_control(c)){
      case (in_ctrl,pe) =>{
        pe.in_control := in_ctrl
        pe.out_control
      } 
    }
  }

  for(c <- 0 until columns){
    tileT(c).foldLeft(io.in_valid(c)){
      case (valid,pe) =>{
        pe.in_valid := valid
        pe.out_valid
      } 
    }
  }

  for (r <- 0 until rows){
    io.out_a(r) := tile(r)(columns-1).io.out_a
  }

  for (c <- 0 until columns){
    io.out_b(c) := tile(rows-1)(c).io.out_b 
    io.out_c(c) := tile(rows-1)(c).io.out_c
    io.out_control(c) := tile(rows-1)(c).io.out_control
    io.out_valid(c) := tile(rows-1)(c).io.out_valid
  }
}

class Mesh(df: Bool, pe_latency: Int,
                                   val tileRows: Int, val tileColumns: Int,
                                   val meshRows: Int, val meshColumns: Int) extends Module{
  val io = IO(new Bundle{
    val in_a = Input(Vec(meshRows,Vec(tileRows,inputType)))
    val in_b = Input(Vec(meshColumns,Vec(tileColumns,inputType)))
    val in_d = Input(Vec(meshColumns,Vec(tileColumns,inputType)))

    val out_b = Output(Vec(meshColumns,Vec(tileColumns,outputType)))
    val out_c = Output(Vec(meshColumns,Vec(tileColumns,outputType)))

    val in_control = Input(Vec(meshColumns,Vec(tileColumns,new PEControl))

    val in_valid = Input(Vec(meshColumns,Vec(tileColumns,Bool())))
    val out_valid = Output(Vec(meshColumns,Vec(tileColumns,Bool())))
  })

  val mesh = Seq,fill(meshRows,meshColumns)(Module (new Tile(df,pe_latency,tileRows,tileColumns)))
  val meshT = mesh.transpose

  for(r <- 0 until meshRows){
    mesh(r).foldLeft(io.in_a(r)){
      case (in_a,tile) => {
        tile.io.a := RegNext(in_a)
        tile.io.out_a
      }
    }
  }

  for(c <- 0 until meshColumns){
    meshT(c).foldLeft((io.in_b(c),io.in_valid(c)){
      case((in_b,valid),tile) => {
        tile.io.in_b := RegEnable(in_b,valid.head)
        (tile.io.out_b,tile.io.out_valid)
      }
    }
  }

  for(c <- 0 until meshColumns){
    meshT(c).foldLeft((io.in_d(c),io.in_valid(c))){
      case (in_d,valid) =>{
        tile.io.in_d := RegEnable(in_d,valid.head)
        (tileT.io.out_c,tile.io.out_valid)
      } 
    }
  }

  for(c <- 0 until meshColumns){
    meshT(c).foldLeft((io.in_control(c),io.in_valid(c))){
      case ((in_ctrl,valid),tile) =>{
        (tile.io.in_control,in_ctrl,valid).zipped.foreach{
          case(tile_control,ctrl,v) => {
            tile_ctrl.dataflow := RegEnable(ctrl.dataflow, v)
                  tile_ctrl.propagate := RegEnable(ctrl.propagate, v)
          }
            (tile.io.out_control, tile.io.out_valid)      
        }
      } 
    }
  }
    for (c <- 0 until meshColumns) {
    meshT(c).foldLeft(io.in_valid(c)) {
      case (in_v, tile) =>
        tile.io.in_valid := RegNext(in_v)
        tile.io.out_valid
      }
    } 
  for (((b, c), (v, tile)) <- ((io.out_b zip io.out_c), (io.out_valid zip mesh.last)).zipped) {
    b := RegNext(tile.io.out_b)
    c := RegNext(tile.io.out_c)
    v := RegNext(tile.io.out_valid)
  }
}
