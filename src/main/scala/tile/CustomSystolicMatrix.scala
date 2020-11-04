package freechips.rocketchip.tile

import chisel3._
import chisel3.util._
import chisel3.experimental._


class PEControl extends Bundle {
  val dataflow = UInt(1.W) // TODO make this an Enum
  val propagate = UInt(1.W) // Which register should be propagated (and which should be accumulated)?
  //val shift = UInt(log2Up(accType.getWidth).W) // TODO this isn't correct for Floats

 // override def cloneType: PEControl.this.type = new PEControl.asInstanceOf[this.type]
}

// TODO update documentation
/**
  * A PE implementing a MAC operation. Configured as fully combinational when integrated into a Mesh.
  * @param width Data width of operands
  */


class PE( df: Bool, latency: Int)
                   extends Module { // Debugging variables
  //import ev._
  def inputType =UInt(8.W)
  def outputType= UInt(19.W)  
  def accType= UInt(32.W)

  val io = IO(new Bundle {
    val in_a = Input(inputType)
    val in_b = Input(outputType)
    val in_d = Input(outputType)
    val out_a = Output(inputType)
    val out_b = Output(outputType)
    val out_c = Output(outputType)

    val in_control = Input(new PEControl(accType))
    val out_control = Output(new PEControl(accType))

    val in_valid = Input(Bool())
    val out_valid = Output(Bool())
  })
/*
      inputType = SInt(8.W),
    outputType = SInt(19.W),
    accType = SInt(32.W),
    */

  val cType = if (df == Dataflow.WS) inputType else accType

  val a  = ShiftRegister(io.in_a, latency)
  val b  = ShiftRegister(io.in_b, latency)
  val d  = ShiftRegister(io.in_d, latency)
  val c1 = Reg(cType)
  val c2 = Reg(cType)
  val dataflow = ShiftRegister(io.in_control.dataflow, latency)
  val prop  = ShiftRegister(io.in_control.propagate, latency)
  //val shift = ShiftRegister(io.in_control.shift, latency)
  val valid = ShiftRegister(io.in_valid, latency) // TODO should we clockgate the rest of the ShiftRegisters based on the values in this ShiftRegisters

  io.out_a := a
  io.out_control.dataflow := dataflow
  io.out_control.propagate := prop
 // io.out_control.shift := shift
  io.out_valid := valid

  //val last_s = RegEnable(prop, valid)
 // val flip = last_s =/= prop
  //val shift_offset = Mux(flip, shift, 0.U)

  // Which dataflow are we using?
  val OUTPUT_STATIONARY = 0.B
  val WEIGHT_STATIONARY = 1.B

  // Is c1 being computed on, or propagated forward (in the output-stationary dataflow)?
  val COMPUTE = 0.U(1.W)
  val PROPAGATE = 1.U(1.W)

  //double buffer , hard to understand control.
  when ( dataflow === OUTPUT_STATIONARY) {
/*
    when(prop === PROPAGATE) {
      io.out_c := (c1 ).clippedToWidthOf(outputType)
      io.out_b := b
      c2 := c2+a*b.asTypeOf(inputType)
      c1 := d.withWidthOf(cType)
    }.otherwise {
      io.out_c := (c2 ).clippedToWidthOf(outputType)
      io.out_b := b
      c1 := c1+a*b.asTypeOf(inputType)
      c2 := d.withWidthOf(cType)
    }
*/
when(prop === PROPAGATE) {
   io.out_b := b
   c1 := c1 + a*b.asTypeOf(inputType)
}.otherwise{
    c1 := 0
}

  }.elsewhen (dataflow === WEIGHT_STATIONARY) {
    when(prop === PROPAGATE) {
      io.out_c := c1
      io.out_b := b+a* c2.asTypeOf(inputType)
      c1 := d
    }.otherwise {
      io.out_c := c2
      io.out_b := b+a*c1.asTypeOf(inputType)
      c2 := d
    }
  }.otherwise {
    assert(false.B, "unknown dataflow")
    io.out_c := DontCare
    io.out_b := DontCare
  }

  when (!valid) {
    c1 := c1
    c2 := c2
  }
}


/**
  * A Tile is a purely combinational 2D array of passThrough PEs.
  * a, b, s, and in_propag are broadcast across the entire array and are passed through to the Tile's outputs
  * @param width The data width of each PE in bits
  * @param rows Number of PEs on each row
  * @param columns Number of PEs on each column
  */
class Tile(df: Bool, pe_latency: Int, val rows: Int, val columns: Int) extends Module {
  val io = IO(new Bundle {
    val in_a        = Input(Vec(rows, inputType))
    val in_b        = Input(Vec(columns, outputType)) // This is the output of the tile next to it
    val in_d        = Input(Vec(columns, outputType))
    val in_control  = Input(Vec(columns, new PEControl(accType)))
    val out_a       = Output(Vec(rows, inputType))
    val out_c       = Output(Vec(columns, outputType))
    val out_b       = Output(Vec(columns, outputType))
    val out_control = Output(Vec(columns, new PEControl(accType)))

    val in_valid = Input(Vec(columns, Bool()))
    val out_valid = Output(Vec(columns, Bool()))
  })

  val tile = Seq.fill(rows, columns)(Module(new PE(inputType, outputType, accType, df, pe_latency)))
  val tileT = tile.transpose

  // TODO: abstract hori/vert broadcast, all these connections look the same
  // Broadcast 'a' horizontally across the Tile
  for (r <- 0 until rows) {
    tile(r).foldLeft(io.in_a(r)) {
      case (in_a, pe) =>
        pe.io.in_a := in_a
        pe.io.out_a
    }
  }

  // Broadcast 'b' vertically across the Tile
  for (c <- 0 until columns) {
    tileT(c).foldLeft(io.in_b(c)) {
      case (in_b, pe) =>
        pe.io.in_b := in_b
        pe.io.out_b
    }
  }

  // Broadcast 'd' vertically across the Tile
  for (c <- 0 until columns) {
    tileT(c).foldLeft(io.in_d(c)) {
      case (in_d, pe) =>
        pe.io.in_d := in_d
        pe.io.out_c
    }
  }

  // Broadcast 'control' vertically across the Tile
  for (c <- 0 until columns) {
    tileT(c).foldLeft(io.in_control(c)) {
      case (in_ctrl, pe) =>
        pe.io.in_control := in_ctrl
        pe.io.out_control
    }
  }

  // Broadcast 'garbage' vertically across the Tile
  for (c <- 0 until columns) {
    tileT(c).foldLeft(io.in_valid(c)) {
      case (v, pe) =>
        pe.io.in_valid := v
        pe.io.out_valid
    }
  }

  // Drive the Tile's bottom IO
  for (c <- 0 until columns) {
    io.out_c(c) := tile(rows-1)(c).io.out_c
    io.out_b(c) := tile(rows-1)(c).io.out_b
    io.out_control(c) := tile(rows-1)(c).io.out_control
    io.out_valid(c) := tile(rows-1)(c).io.out_valid
  }

  // Drive the Tile's right IO
  for (r <- 0 until rows) {
    io.out_a(r) := tile(r)(columns-1).io.out_a
  }
}


/**
  * A Grid is a 2D array of Tile modules with registers in between each tile and
  * registers from the bottom row and rightmost column of tiles to the Grid outputs.
  * @param width
  * @param tileRows
  * @param tileColumns
  * @param meshRows
  * @param meshColumns
  */
class Mesh(df: Bool, pe_latency: Int,
                                   val tileRows: Int, val tileColumns: Int,
                                   val meshRows: Int, val meshColumns: Int) extends Module {
  val io = IO(new Bundle {
    val in_a   = Input(Vec(meshRows, Vec(tileRows, inputType)))
    val in_b   = Input(Vec(meshColumns, Vec(tileColumns, inputType)))
    val in_d   = Input(Vec(meshColumns, Vec(tileColumns, inputType)))
    val in_control   = Input(Vec(meshColumns, Vec(tileColumns, new PEControl(accType))))
    val out_b  = Output(Vec(meshColumns, Vec(tileColumns, outputType)))
    val out_c  = Output(Vec(meshColumns, Vec(tileColumns, outputType)))
    val in_valid = Input(Vec(meshColumns, Vec(tileColumns, Bool())))
    val out_valid = Output(Vec(meshColumns, Vec(tileColumns, Bool())))
  })
  // mesh(r)(c) => Tile at row r, column c
  val mesh: Seq[Seq[Tile[T]]] = Seq.fill(meshRows, meshColumns)(Module(new Tile(inputType, outputType, accType, df, pe_latency, tileRows, tileColumns)))
  val meshT = mesh.transpose
  // Chain tile_a_out -> tile_a_in (pipeline a across each row)
  // TODO clock-gate A signals with in_garbage
  for (r <- 0 until meshRows) {
    mesh(r).foldLeft(io.in_a(r)) {
      case (in_a, tile) =>
        tile.io.in_a := RegNext(in_a)
        tile.io.out_a
    }
  }
  // Chain tile_out_b -> tile_b_in (pipeline b across each column)
  for (c <- 0 until meshColumns) {
    meshT(c).foldLeft((io.in_b(c), io.in_valid(c))) {
      case ((in_b, valid), tile) =>
        tile.io.in_b := RegEnable(in_b, valid.head)
        (tile.io.out_b, tile.io.out_valid)
    }
  }
  // Chain tile_out -> tile_propag (pipeline output across each column)
  for (c <- 0 until meshColumns) {
    meshT(c).foldLeft((io.in_d(c), io.in_valid(c))) {
      case ((in_propag, valid), tile) =>
        tile.io.in_d := RegEnable(in_propag, valid.head)
        (tile.io.out_c, tile.io.out_valid)
    }
  }
  // Chain control signals (pipeline across each column)
  for (c <- 0 until meshColumns) {
    meshT(c).foldLeft((io.in_control(c), io.in_valid(c))) {
      case ((in_ctrl, valid), tile) =>
        (tile.io.in_control, in_ctrl, valid).zipped.foreach { case (tile_ctrl, ctrl, v) =>
        //  tile_ctrl.shift := RegEnable(ctrl.shift, v)
          tile_ctrl.dataflow := RegEnable(ctrl.dataflow, v)
          tile_ctrl.propagate := RegEnable(ctrl.propagate, v)
        }
        (tile.io.out_control, tile.io.out_valid)
    }
  }
  // Chain in_valid (pipeline across each column)
  for (c <- 0 until meshColumns) {
    meshT(c).foldLeft(io.in_valid(c)) {
      case (in_v, tile) =>
        tile.io.in_valid := RegNext(in_v)
        tile.io.out_valid
    }
  }
  // Capture out_vec and out_control_vec (connect IO to bottom row of mesh)
  // (The only reason we have so many zips is because Scala doesn't provide a zipped function for Tuple4)
  for (((b, c), (v, tile)) <- ((io.out_b zip io.out_c), (io.out_valid zip mesh.last)).zipped) {
    // TODO we pipelined this to make physical design easier. Consider removing these if possible
    // TODO shouldn't we clock-gate these signals with "garbage" as well?
    b := RegNext(tile.io.out_b)
    c := RegNext(tile.io.out_c)
    v := RegNext(tile.io.out_valid)
  }
}
