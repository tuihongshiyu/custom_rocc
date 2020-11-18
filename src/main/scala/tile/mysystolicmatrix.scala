package systolicMatrix
//*
import chisel3._
import chisel3.util._
import chisel3.experimental._
//*/
///*
//class name capital
import chisel3.experimental.{chiselName, NoChiselNamePrefix}

@chiselName
class PEControl extends Bundle{
    //define two constant
    val propagate = UInt(1.W)
    val dataflow = UInt(1.W)
}
object  Constant {
  def inputType = UInt(8.W)
  def outputType = UInt(19.W) 
  def accType = UInt(32.W)

  def OS = false.B
  def WS = true.B
  def df = OS
  def latency = 0
  def tileRows=1
  def tileColumns=1
  def meshRows=2
  def meshColumns=2
}
class PE(latency:Int) extends  Module{
    val io = IO(
        new Bundle{
            val in_a = Input(Constant.inputType)
            val in_b = Input(Constant.outputType)
            val in_d = Input(Constant.outputType)

            val out_a = Output(Constant.inputType)
            val out_b = Output(Constant.outputType)
            val out_c = Output(Constant.outputType)

            val in_control = Input(new PEControl)
            val out_control = Output(new PEControl)

            val in_valid = Input(Bool())
            val out_valid = Output(Bool())
        }
    )

    val cType = if(Constant.df==true.B){Constant.inputType} else {Constant.accType}  

    val a = ShiftRegister(io.in_a,latency)
    val b = ShiftRegister(io.in_b,latency)
    val d = ShiftRegister(io.in_d,latency)
    val c1 = Reg(cType)
    val c2 = Reg(cType)
    val dataflow = ShiftRegister(io.in_control.dataflow,latency)
    val propagate = ShiftRegister(io.in_control.propagate,latency)
    val valid = ShiftRegister(io.in_valid,latency)

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
        io.out_c := (c1)
        c2 := c2 + a * b
        c1 := d
      }.otherwise{
        io.out_b := b
        io.out_c := (c2)
        c1 := c1 + a * b
        c2 := d
      }
    }.elsewhen(dataflow === WEIGHT_STATIONARY){
      when(propagate === PROPAGATE){
        io.out_c := c1
        c1 := d
        io.out_b := b + a * c2
      }.otherwise{
        io.out_c := c2
        c2 := d
        io.out_b := b + a * c1
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

class Tile( latency: Int, rows: Int, columns: Int) extends Module{
  val io = IO(new Bundle{
    val in_a = Input(Vec(rows,Constant.inputType))
    val in_b = Input(Vec(columns,Constant.outputType))
    val in_d = Input(Vec(columns,Constant.outputType))

    val out_a = Output(Vec(rows,Constant.inputType))
    val out_b = Output(Vec(columns,Constant.outputType))
    val out_c = Output(Vec(columns,Constant.outputType))

    val in_control = Input(Vec(columns,new PEControl))
    val out_control = Output(Vec(columns,new PEControl))

    val in_valid = Input(Vec(columns,Bool()))
    val out_valid = Output(Vec(columns,Bool()))
  })

  val tile = Seq.fill(rows,columns)(Module (new PE(latency)))
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
        pe.io.in_control := in_ctrl
        pe.io.out_control
      } 
    }
  }

  for(c <- 0 until columns){
    tileT(c).foldLeft(io.in_valid(c)){
      case (valid,pe) =>{
        pe.io.in_valid := valid
        pe.io.out_valid
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

class Mesh( pe_latency: Int,
                                   val tileRows: Int, val tileColumns: Int,
                                   val meshRows: Int, val meshColumns: Int) extends Module{
  val io = IO(new Bundle{
    val in_a = Input(Vec(meshRows,Vec(tileRows,Constant.inputType)))
    val in_b = Input(Vec(meshColumns,Vec(tileColumns,Constant.inputType)))
    val in_d = Input(Vec(meshColumns,Vec(tileColumns,Constant.inputType)))

    val out_b = Output(Vec(meshColumns,Vec(tileColumns,Constant.outputType)))
    val out_c = Output(Vec(meshColumns,Vec(tileColumns,Constant.outputType)))

    val in_control = Input(Vec(meshColumns,Vec(tileColumns,new PEControl)))

    val in_valid = Input(Vec(meshColumns,Vec(tileColumns,Bool())))
    val out_valid = Output(Vec(meshColumns,Vec(tileColumns,Bool())))
  })

  val mesh = Seq.fill(meshRows,meshColumns)(Module (new Tile(pe_latency,tileRows,tileColumns)))
  val meshT = mesh.transpose
//a
  for(r <- 0 until meshRows){
    mesh(r).foldLeft(io.in_a(r)){
      case (in_a,tile) => {
        tile.io.in_a := RegNext(in_a)
        tile.io.out_a
      }
    }
  }
//b
  for(c <- 0 until meshColumns){
    meshT(c).foldLeft((io.in_b(c),io.in_valid(c))){
      case((in_b,valid),tile) => {
        tile.io.in_b := RegEnable(in_b,valid.head)
        (tile.io.out_b,tile.io.out_valid)
      }
    }
  }
//d
  for(c <- 0 until meshColumns){
    meshT(c).foldLeft((io.in_d(c),io.in_valid(c))){
      case ((in_d,valid),tile) =>{
        tile.io.in_d := RegEnable(in_d,valid.head)
        (tile.io.out_c,tile.io.out_valid)
      } 
    }
  }

  for(c <- 0 until meshColumns){
    meshT(c).foldLeft((io.in_control(c),io.in_valid(c))){
      case ((in_ctrl,valid),tile) =>
        (tile.io.in_control,in_ctrl,valid).zipped.foreach {
          case(tile_ctrl,ctrl,v) => 
            tile_ctrl.dataflow := RegEnable(ctrl.dataflow, v)
            tile_ctrl.propagate := RegEnable(ctrl.propagate, v)
          }
            (tile.io.out_control, tile.io.out_valid)      
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

 //*/
 
 //*
 class  WrapperMesh(pe_latency: Int, tileRows: Int, tileColumns: Int, meshRows: Int, meshColumns: Int) extends Module{
     val A_TYPE = Vec(meshRows, Vec(tileRows, Constant.inputType))
     val B_TYPE = Vec(meshColumns, Vec(tileColumns, Constant.inputType))
     val C_TYPE = Vec(meshColumns, Vec(tileColumns, Constant.outputType))
     val D_TYPE = Vec(meshColumns, Vec(tileColumns, Constant.inputType))
     val io = IO(new Bundle{
     val a = Flipped(Decoupled(A_TYPE))
     val b = Flipped(Decoupled(B_TYPE))
     val d = Flipped(Decoupled(D_TYPE))

     val c = Flipped(Decoupled(C_TYPE))
 //state machine
     val state = Input(UInt(2.W))

     val load_over = Output(UInt(1.W))
     val compute_over = Output(UInt(1.W))
     val store_over =Output(UInt(1.W))

     val flush = Input(UInt(1.W))
     val flush_over = Output(UInt(1.W))


   })
     def IDLE = 0.U
     def LOAD = 1.U
     def COMP = 2.U
     def STORE = 3.U

     val mesh = Module(new Mesh(pe_latency,tileRows,tileColumns,meshRows,meshColumns))
     val idle_valid = Seq.fill(meshColumns,tileColumns)(0.U)     
     val load_prop = Seq.fill(meshColumns,tileColumns)(0.U)
     val load_valid = Seq.fill(meshColumns,tileColumns)(1.U)

     val comp_prop = Seq.fill(meshColumns,tileColumns)(1.U)
     

     val store_prop = Seq.fill(meshColumns,tileColumns)(0.U)
      

     val load_count = Reg(UInt(log2Ceil(meshColumns*tileColumns).W))
     when(io.state === LOAD){
      load_count :=load_count+1
    }.otherwise { load_count := 0.U}
     io.load_over := load_count === meshColumns*tileColumns

     io.compute_over := ShiftRegister(1.U,meshColumns*tileColumns*2+meshRows*tileRows-1,io.state === COMP)
     when(io.state === IDLE){        
     for ((slice, i) <- idle_valid.zipWithIndex) {
       for ((elem, j) <- slice.zipWithIndex) {
         mesh.io.in_valid(i)(j) := elem
       }
     }

     }.elsewhen(io.state === LOAD){
     for ((slice, i) <- load_valid.zipWithIndex) {
       for ((elem, j) <- slice.zipWithIndex) {
         mesh.io.in_valid(i)(j) := elem
       }
     }
     for ((slice, i) <- load_prop.zipWithIndex) {
       for ((elem, j) <- slice.zipWithIndex) {
         mesh.io.in_control.propagate(i)(j) := elem
       }
     }     
     }.elsewhen(io.state === COMP){
     for ((slice, i) <- comp_prop.zipWithIndex) {
       for ((elem, j) <- slice.zipWithIndex) {
         mesh.io.in_control.propagate(i)(j) := elem
       }
     }

     }
     .otherwise{
      if(Constant.df == true.B){
        io.store_over := 1.U
      }
      else {
     for ((slice, i) <- store_prop.zipWithIndex) {
       for ((elem, j) <- slice.zipWithIndex) {
         mesh.io.in_control.propagate(i)(j) := elem
       }
      } 
      //give a signal,then pipe out results        
      }
     }
     
 }
 //*/