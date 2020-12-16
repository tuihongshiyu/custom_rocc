package freechips.rocketchip.tile

import Chisel._
import chisel3.util.HasBlackBoxResource
import chisel3.experimental.IntParam
import chisel3.experimental.{chiselName, NoChiselNamePrefix}
// cmd parse
class MoveCmd() extends Bundle {
  val rd = Bits(width = C.W_ADDR)//rs1(59,30)
  val rn = Bits(width = C.W_ADDR)//rs1(29, 0)
  val mask2 = Bits(width = 48)   //rs2(63,16)
  val rc = Bits(width = 16)      //rs2(15, 0)
}

class MoveParse() extends Module{
  val io = new Bundle {
    val do_move3 = Input(Bool())
    val mux = Input(Bool())
    val valid = Input(Bool())
    val ready = Output(Bool())
    val rs1 = Input(UInt(64.W))
    val rs2 = Input(UInt(64.W))
    val cmd_1 = Decoupled(new MoveCmd())
    val cmd_2 = Decoupled(new MoveCmd())
    val cmd_3 = Decoupled(new MoveCmd())
  }
  chisel3.dontTouch(io)
  //
  io.ready := Mux(io.do_move3,io.cmd_3.ready,Mux(io.mux,io.cmd_1.ready,io.cmd_2.ready))
  //
  io.cmd_1.bits.rd    := io.rs1(59,30)
  io.cmd_1.bits.rn    := io.rs1(30, 0)
  io.cmd_1.bits.mask2 := io.rs2(63,16)
  io.cmd_1.bits.rc    := io.rs2(15, 0)
  io.cmd_1.valid      := io.valid && io.mux && !io.do_move3
  //
  io.cmd_2.bits.rd    := io.rs1(59,30)
  io.cmd_2.bits.rn    := io.rs1(30, 0)
  io.cmd_2.bits.mask2 := io.rs2(63,16)
  io.cmd_2.bits.rc    := io.rs2(15, 0)
  io.cmd_2.valid      := io.valid && !io.mux && !io.do_move3
  //
    //
  io.cmd_3.bits.rd    := io.rs1(59,30)
  io.cmd_3.bits.rn    := io.rs1(30, 0)
  io.cmd_3.bits.mask2 := io.rs2(63,16)
  io.cmd_3.bits.rc    := io.rs2(15, 0)
  io.cmd_3.valid      := io.valid && io.do_move3
}

//node
class NodeBits(w: Int) extends Bundle {
  val data = UInt(w.W)
  val node_index = UInt(C.W_INDEX.W)
  //
  override def cloneType = new NodeBits(w).asInstanceOf[this.type]
}

// class NodeIO(n_i: Int, w: Int) extends Bundle {
//   val o = Decoupled(new NodeBits(w))
//   val i = Vec(n_i, Decoupled(new NodeBits(w)).flip)
//   val mux = Input(UInt(width = 4))
// }

class InNode(index:Int = 0,n_i: Int = 1, w: Int = C.W_MOVE) extends Module {
  lazy val io = new Bundle{
    val o = Decoupled(new NodeBits(w))
    val i = Vec(n_i, Decoupled(new NodeBits(w)).flip)
    val mux = Input(UInt(width = 4))
  }
  chisel3.dontTouch(io)
  io.o.bits.node_index := index.U

  for (i <- 0 until n_i) {
    when(io.i(i).bits.node_index === io.mux){
      io.i(i).ready := io.o.ready
      io.o.bits := io.i(i).bits
      io.o.valid := io.i(i).valid
    }.otherwise{
      io.i(i).ready := false.B
    }
  }

}

class OutNode(index:Int = 0,n_o: Int = 1,w: Int = C.W_MOVE) extends Module {
  lazy val io = new Bundle{
    val i = Decoupled(new NodeBits(w)).flip
    val o = Vec(n_o, Decoupled(new NodeBits(w)))
  }
  chisel3.dontTouch(io)
  val ready = Wire(Vec(n_o,Bool()))
  for(i <- 0 until n_o){
    io.o(i).bits.data := io.i.bits.data
    io.o(i).bits.node_index := index.U
    io.o(i).valid := io.i.valid
    ready(i) := io.o(i).ready
  }
  io.i.ready := ready.reduce(_||_)
}
//
class ArrayIO(n_i: Int = 2) extends Bundle{
  // val i = Vec(n_i,Vec(C.N_ARRAY, Decoupled(new NodeBits(C.W_ARRAY)).flip))
  // val o = Vec(C.N_ARRAY, Decoupled(new NodeBits(C.W_ARRAY)))
  val i = Vec(n_i,Vec(C.N_ARRAY, Decoupled(UInt(C.W_ARRAY.W)).flip))
  val o = Vec(C.N_ARRAY, Decoupled(UInt(C.W_ARRAY.W)))
  val mux = Input(UInt(width = 1))
}
class ArrayNode(n_i: Int = 2) extends Module{
  lazy val io = new ArrayIO(n_i)
  chisel3.dontTouch(io)
  for (i <- 0 until n_i) {
    when(io.mux === i.U){
      io.o <> io.i(i)
    }
  }
  
}

// tag
class TagQueue(width :Int = C.W_INDEX) extends Module {
  val io = new Bundle{
    val i = Decoupled(UInt(width.W)).flip
    val o = Decoupled(UInt(width.W))
    val fire = Output(Bool())
  }
  chisel3.dontTouch(io.i)
  val tag_queue = Queue(io.i,C.DEPTH_TAG)
  io.o.bits := tag_queue.bits
  io.o.valid := tag_queue.valid
  tag_queue.ready := io.o.ready
  io.fire := tag_queue.fire()
}