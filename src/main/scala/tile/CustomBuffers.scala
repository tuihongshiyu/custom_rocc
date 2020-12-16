package freechips.rocketchip.tile

import Chisel._
import chisel3.util.HasBlackBoxResource
import chisel3.experimental.IntParam
import chisel3.experimental.{chiselName, NoChiselNamePrefix}

class BufferCtrlIO() extends Bundle {
  val en = Input(Bool())
  val length = Input(UInt(width = 16))
  val addr_begin = Input(UInt(width = C.W_ADDR))
  val done = Output(Bool())
}

class BufferIO(base_width: Int = 512,mv3_width: Int = 2048) extends Bundle {
  // tag
  val tag = Input(UInt(width = C.W_ADDR))   // 表征占用模块
  val tag_v = Input(Bool())
  // read out
  val r = Decoupled(new NodeBits(base_width))
  val r_ctrl = new BufferCtrlIO()
  // write in
  val w = Decoupled(new NodeBits(base_width)).flip
  val w_ctrl = new BufferCtrlIO()
  //move3
  val move3 = Input(Bool())
  val mv3_r =  Decoupled(new NodeBits(mv3_width))
  val mv3_w =  Decoupled(new NodeBits(mv3_width)).flip
  //array io
  // val array_r = Vec(C.N_ARRAY, Decoupled(new NodeBits(C.W_ARRAY)))
  val array_r = Vec(C.N_ARRAY, Decoupled(UInt(C.W_ARRAY.W)))
  val array_rv = Output(Bool())
  val array_w = Input(Vec(C.N_ARRAY, UInt(C.W_ARRAY.W)))
  val array_wv = Input(Bool())
}


class InputBuffer(size: Int = 1024,n_i:Int = 1,base_width: Int = 512) extends Module {
  def block_width = 2048
  def nblock = size / block_width
  def addr2index(addr: UInt) = addr >> log2Up(block_width / C.W_DATA)
  val io = new BufferIO(base_width)
  val blocks = Mem(nblock, UInt(width = block_width))
  chisel3.dontTouch(io.mv3_r)
  //--------------------------- write ---------------------------
  def w_fire(): Bool = io.w.valid && io.w.ready

  val wr_en_posedge = io.w_ctrl.en && !RegNext(io.w_ctrl.en)
  val wr_block_index = RegInit(0.U(16.W))
  val wr_block_index_max = RegInit(0.U(16.W))
  val wr_done = Wire(Bool())
  val wr_data_temp = RegInit(0.U(block_width.W))
  val wr_data_temp_next = Wire(UInt(width = block_width))
  val wr_temp_cnt = RegInit(0.U(log2Up(block_width/base_width).W))
  wr_data_temp_next := Cat(io.w.bits.data,wr_data_temp(block_width-1,base_width))
  
  wr_done := wr_block_index >= wr_block_index_max && io.w_ctrl.en && RegNext(io.w_ctrl.en)
  io.w_ctrl.done := wr_done
  //
  when(wr_en_posedge) {
    wr_temp_cnt := 0.U
    wr_block_index := addr2index(io.w_ctrl.addr_begin & 0x0000FFFF.U)
    wr_block_index_max := addr2index((io.w_ctrl.addr_begin & 0x0000FFFF.U) + io.w_ctrl.length)
  }
  when(io.w_ctrl.en){
    when(w_fire()){
      wr_data_temp := wr_data_temp_next
      wr_temp_cnt := wr_temp_cnt + 1.U
      when(wr_temp_cnt === (block_width/base_width-1).U){
        blocks(wr_block_index) := wr_data_temp_next
        wr_block_index := wr_block_index + 1.U
      }
    }
  }
  io.w.ready := io.w_ctrl.en
  //--------------------------- read ---------------------------
  val rd_en_posedge = io.r_ctrl.en && !RegNext(io.r_ctrl.en)
  val rd_state = RegInit(0.U(2.W))
  val rd_block_index = RegInit(0.U(16.W))
  val rd_block_index_max = RegInit(0.U(16.W))
  val rd_done = Wire(Bool())
  val rd_data_temp = RegInit(0.U(block_width.W))
  val rd_temp_cnt = RegInit(0.U(log2Up(block_width/base_width).W))
  rd_done := rd_block_index >= rd_block_index_max && io.r_ctrl.en && RegNext(io.r_ctrl.en)
  io.r_ctrl.done := rd_done
  // move1/2 port
  io.r.bits.data := rd_data_temp
  io.r.valid := io.r_ctrl.en && RegNext(io.r_ctrl.en) && !rd_done && rd_state === 1.U
  // move3 port
  io.mv3_r.bits.data := rd_data_temp;
  io.mv3_r.valid := io.r_ctrl.en && RegNext(io.r_ctrl.en) && !rd_done && rd_state === 2.U
  //
  when(rd_state === 0.U){//IDLE
    when(rd_en_posedge){
      rd_temp_cnt := 0.U
      rd_data_temp := blocks(addr2index(io.r_ctrl.addr_begin & 0x0000FFFF.U))
      rd_block_index := addr2index(io.r_ctrl.addr_begin & 0x0000FFFF.U)
      rd_block_index_max := addr2index((io.r_ctrl.addr_begin & 0x0000FFFF.U) + io.r_ctrl.length)
      rd_state := Mux(io.move3,2.U,1.U)
    }
  }

  when(rd_state === 1.U){//move1/2
    when(io.r.valid && io.r.ready){
      rd_temp_cnt := rd_temp_cnt + 1.U
      when(rd_temp_cnt < (block_width/base_width-1).U){
        rd_data_temp := rd_data_temp >> base_width
      }.otherwise{
        rd_data_temp := blocks(rd_block_index + 1.U)
        rd_block_index := rd_block_index + 1.U
      }
    }
    when(!io.r_ctrl.en){
      rd_state := 0.U
    }
  }

  when(rd_state === 2.U){//move3
    when(io.mv3_r.ready && io.mv3_r.valid){
      rd_data_temp := blocks(rd_block_index + 1.U)
      rd_block_index := rd_block_index + 1.U
    }
    when(!io.r_ctrl.en){
      rd_state := 0.U
    }
  }
}
//---------------------------------------------------------------------------------
//---------------------------------------------------------------------------------
//                             WEIGHT/FEATURE
//---------------------------------------------------------------------------------
//---------------------------------------------------------------------------------
class WFBuffer(size: Int = 1024,n_i:Int = 1,base_width: Int = 512) extends Module {
  def block_width = C.W_ARRAY * C.N_ARRAY
  def nblock = size / block_width
  def addr2index(addr: UInt) = addr >> log2Up(block_width / C.W_DATA)
  val io = new BufferIO(base_width)
  val blocks = Mem(nblock, UInt(width = block_width))
  chisel3.dontTouch(io.mv3_w)
  chisel3.dontTouch(io.array_r)
  //--------------------------- write ---------------------------
  val wr_state = RegInit(0.U(2.W))
  val wr_en_posedge = io.w_ctrl.en && !RegNext(io.w_ctrl.en)
  val wr_block_index = RegInit(0.U(16.W))
  val wr_done = Wire(Bool())

  
  if(block_width > C.W_MOVE && block_width > C.W_MOVE3){
    val wr_data_temp = RegInit(0.U(block_width.W))
    val wr_data_temp_next = Wire(UInt(width = block_width))
    val wr_data_temp_next_mv3 = Wire(UInt(width = block_width))
    val wr_temp_cnt = RegInit(0.U(log2Up(block_width/base_width).W))
    wr_data_temp_next := Cat(io.w.bits.data,wr_data_temp(block_width-1,base_width))
    wr_data_temp_next_mv3 := Cat(io.mv3_w.bits.data,wr_data_temp(block_width-1,C.W_MOVE3))
    when(wr_state === 0.U){//IDLE
      when(wr_en_posedge){
        wr_temp_cnt := 0.U
        wr_block_index := 0.U
        wr_state := Mux(io.move3,2.U,1.U)
      }
    }

    io.w.ready := wr_state === 1.U
    when(wr_state === 1.U){
      when(io.w.valid && io.w.ready){
        wr_data_temp := wr_data_temp_next
        when(wr_temp_cnt >= (block_width/base_width-1).U){
          blocks(wr_block_index) := wr_data_temp_next
          wr_block_index := wr_block_index + 1.U
          wr_temp_cnt := 0.U
        }.otherwise{
          wr_temp_cnt := wr_temp_cnt + 1.U
        }
      }
      when(!io.w_ctrl.en){
        wr_state := 0.U
      }
    }

    io.mv3_w.ready := wr_state === 2.U
    when(wr_state === 2.U){
      when(io.mv3_w.valid && io.mv3_w.ready){
        wr_data_temp := wr_data_temp_next_mv3
        when(wr_temp_cnt >= (block_width/C.W_MOVE3-1).U){
          blocks(wr_block_index) := wr_data_temp_next_mv3
          wr_block_index := wr_block_index + 1.U
          wr_temp_cnt := 0.U
        }.otherwise{
          wr_temp_cnt := wr_temp_cnt + 1.U
        }
      }
      when(!io.w_ctrl.en){
        wr_state := 0.U
      }
    }
  }else if(block_width < C.W_MOVE && block_width < C.W_MOVE3){//for debug 16 * 16
    val wr_data_temp = RegInit(0.U(C.W_MOVE3.W))
    val wr_data_temp_next = Wire(UInt(width = C.W_MOVE3))
    // val wr_data_temp_next_mv3 = Wire(UInt(width = block_width))
    val wr_temp_cnt = RegInit(0.U(log2Up(C.W_MOVE3/block_width).W))
    val wr_read_first = RegInit(false.B)
    wr_data_temp_next := wr_data_temp >>  block_width

    when(wr_state === 0.U){//IDLE
      when(wr_en_posedge){
        wr_read_first := false.B
        wr_temp_cnt := 0.U
        wr_block_index := 0.U
        wr_state := Mux(io.move3,2.U,1.U)
      }
      io.mv3_w.ready := false.B
    }

    
    when(wr_state === 2.U){
      when(!wr_read_first || wr_temp_cnt === (C.W_MOVE3/block_width - 1).U){
        io.mv3_w.ready := wr_block_index < (nblock - 1).U
      }.otherwise{
        io.mv3_w.ready := false.B
      }

      when(io.mv3_w.valid && io.mv3_w.ready){
        wr_read_first := true.B
        wr_data_temp := io.mv3_w.bits.data
        wr_temp_cnt := 0.U
      }.otherwise{
        wr_data_temp := wr_data_temp_next
        wr_temp_cnt := wr_temp_cnt + 1.U
      }

      when(wr_read_first && wr_block_index < nblock.U){
        blocks(wr_block_index) := wr_data_temp
        wr_block_index := wr_block_index + 1.U
      }

      when(!io.w_ctrl.en){
        wr_state := 0.U
      }
    }
  }

  wr_done := wr_block_index >= nblock.U && io.w_ctrl.en && RegNext(io.w_ctrl.en)
  io.w_ctrl.done := wr_done
  //



  //--------------------------- read ---------------------------
  def mat_cnt_width = log2Up(C.N_ARRAY)
  def mat_row_width = C.W_ARRAY * C.N_ARRAY // 16 * 256 = 4096
  val ex_rd_temp_rows = Reg(init = Vec.fill(C.N_ARRAY) {0.U(mat_row_width.W)})
  val ex_rd_counter = RegInit(0.U((mat_cnt_width + 1).W))
  val ex_rd_en_posedge = io.r_ctrl.en && !RegNext(io.r_ctrl.en)
  // ex_read
  val ex_rd_block_index = RegInit(0.U(16.W))
  val ex_rd_mat_n = C.N_ARRAY.U
  val ex_rd_counter_max = (2 * C.N_ARRAY).U - 1.U
  val ex_rd_started = io.r_ctrl.en && RegNext(io.r_ctrl.en)
  //
  when(!io.r_ctrl.en) {
    ex_rd_counter := 0.U
    ex_rd_block_index := 0.U
  }

  when(ex_rd_en_posedge) {
    ex_rd_temp_rows(0) := blocks(0)
    ex_rd_counter := 0.U
  }

  when(io.r_ctrl.en){
    when(ex_rd_block_index < nblock.U){
      ex_rd_block_index := ex_rd_block_index + 1.U;
      for (i <- 0 until C.N_ARRAY) {
        when(i.U === ex_rd_block_index){
          ex_rd_temp_rows(i) := blocks(ex_rd_block_index)
        }
      }
    }
  }

  when(ex_rd_started && ex_rd_counter < ex_rd_counter_max) {
    ex_rd_counter := ex_rd_counter + 1.U;
  }

  io.r_ctrl.done := ex_rd_started && ex_rd_counter >= ex_rd_counter_max

  for (i <- 0 until C.N_ARRAY) {
    when(io.array_r(i).valid) {
      ex_rd_temp_rows(i) := ex_rd_temp_rows(i) >> C.W_ARRAY
    }
  }

  for (i <- 0 until C.N_ARRAY) {
    io.array_r(i).valid := ex_rd_started && ex_rd_counter >= i.U && ex_rd_counter < i.U + ex_rd_mat_n && i.U < ex_rd_mat_n
    // io.array_r(i).bits.data := ex_rd_temp_rows(i)
    io.array_r(i).bits := ex_rd_temp_rows(i)
  }
}
//---------------------------------------------------------------------------------
//---------------------------------------------------------------------------------
//                                 MIDDLE
//---------------------------------------------------------------------------------
//---------------------------------------------------------------------------------
class MiddleBuffer(size: Int = 1024,base_width: Int = 512) extends Module {
  def valid_bits_init = ((1 << C.N_ARRAY) - 1) << (C.N_ARRAY-1)
  def block_width = C.W_ARRAY * C.N_ARRAY
  def nblock = size / block_width
  def addr2index(addr: UInt) = addr >> log2Up(block_width / C.W_DATA)
  val io = new BufferIO(base_width)
  val blocks = Mem(nblock, UInt(width = block_width))
  chisel3.dontTouch(io.mv3_w)
  chisel3.dontTouch(io.array_r)
  //--------------------------- write ---------------------------
  val wr_state = RegInit(0.U(2.W))
  val wr_en_posedge = io.w_ctrl.en && !RegNext(io.w_ctrl.en)
  val wr_block_index = RegInit(0.U(16.W))
  val wr_done = Wire(Bool())
  // gemm wr
  def mat_cnt_width = log2Up(C.N_ARRAY)
  def mat_row_width = C.W_ARRAY * C.N_ARRAY // 16 * 256 = 4096
  val ex_wr_temp_rows = Reg(init = Vec.fill(C.N_ARRAY) {0.U(mat_row_width.W)})
  val ex_wr_counter = RegInit(0.U((mat_cnt_width + 1).W))
  val ex_wr_en_posedge = io.r_ctrl.en && !RegNext(io.r_ctrl.en)
  // ex_write
  val ex_wr_block_index = RegInit(0.U(16.W))
  val ex_wr_mat_n = C.N_ARRAY.U
  val ex_wr_counter_max = (2 * C.N_ARRAY).U - 1.U
  val ex_wr_started = io.r_ctrl.en && RegNext(io.r_ctrl.en)
  val ex_wr_valid_bits = RegInit(valid_bits_init.U((2 * C.N_ARRAY - 1).W))
  val ex_wr_row_valid = Wire(Vec(C.N_ARRAY,Bool()))
  // for(i <- 0 until C.N_ARRAY){
  //   ex_wr_row_valid(i) := ex_wr_valid_bits(C.N_ARRAY -1 - i)
  // }
  // when(wr_state === 0.U){
  //   when(io.array_wv){
  //     when(ex_wr_valid_bits === 1.U){
  //       ex_wr_valid_bits := valid_bits_init.U
  //     }.otherwise{
  //       ex_wr_valid_bits >> 1
  //     }
  //   }

  //   for (i <- 0 until C.N_ARRAY) {
  //     when(io.array_wv && ex_wr_row_valid(i)) {
  //       ex_rd_temp_rows(i) := ex_rd_temp_rows(i) >> C.W_ARRAY
  //     }
  //   }

  // }

  if(block_width > C.W_MOVE && block_width > C.W_MOVE3){
    val wr_data_temp = RegInit(0.U(block_width.W))
    val wr_data_temp_next = Wire(UInt(width = block_width))
    val wr_temp_cnt = RegInit(0.U(log2Up(block_width/base_width).W))
    wr_data_temp_next := Cat(io.w.bits.data,wr_data_temp(block_width-1,base_width))
    when(wr_state === 0.U){//IDLE
      when(wr_en_posedge){
        wr_temp_cnt := 0.U
        wr_block_index := 0.U
        wr_state := 1.U
      }
    }

    io.w.ready := wr_state === 1.U
    when(wr_state === 1.U){
      when(io.w.valid && io.w.ready){
        wr_data_temp := wr_data_temp_next
        when(wr_temp_cnt >= (block_width/base_width-1).U){
          blocks(wr_block_index) := wr_data_temp_next
          wr_block_index := wr_block_index + 1.U
          wr_temp_cnt := 0.U
        }.otherwise{
          wr_temp_cnt := wr_temp_cnt + 1.U
        }
      }
      when(!io.w_ctrl.en){
        wr_state := 0.U
      }
    }

  }else if(block_width < C.W_MOVE && block_width < C.W_MOVE3){//for debug 16 * 16
    val wr_data_temp = RegInit(0.U(C.W_MOVE3.W))
    val wr_data_temp_next = Wire(UInt(width = C.W_MOVE3))
    // val wr_data_temp_next_mv3 = Wire(UInt(width = block_width))
    val wr_temp_cnt = RegInit(0.U(log2Up(C.W_MOVE3/block_width).W))
    val wr_read_first = RegInit(false.B)
    wr_data_temp_next := wr_data_temp >>  block_width

    when(wr_state === 0.U){//IDLE
      when(wr_en_posedge){
        wr_read_first := false.B
        wr_temp_cnt := 0.U
        wr_block_index := 0.U
        wr_state := Mux(io.move3,2.U,1.U)
      }
      io.mv3_w.ready := false.B
    }

  }

  wr_done := wr_block_index >= nblock.U && io.w_ctrl.en && RegNext(io.w_ctrl.en)
  io.w_ctrl.done := wr_done
  //--------------------------- read ---------------------------
  // def r_fire(): Bool = io.r.valid && io.r.ready
  // val rd_en_posedge = io.r_ctrl.en && !RegNext(io.r_ctrl.en)
  // val rd_block_index = RegInit(0.U(16.W))
  // val rd_block_index_max = RegInit(0.U(16.W))
  // val rd_done = Wire(Bool())
  // val rd_data_temp = RegInit(0.U(block_width.W))
  // val rd_temp_cnt = RegInit(0.U(log2Up(block_width/base_width).W))
  // rd_done := rd_block_index >= rd_block_index_max && io.r_ctrl.en && RegNext(io.r_ctrl.en)
  // io.r_ctrl.done := rd_done
  // // io.r.bits.data := Cat(blocks(rd_block_index + 1.U),blocks(rd_block_index))
  // io.r.bits.data := rd_data_temp

  // io.r.valid := io.r_ctrl.en && RegNext(io.r_ctrl.en) && !rd_done

  // when(rd_en_posedge) {
  //   rd_temp_cnt := 0.U
  //   rd_data_temp := blocks(addr2index(io.r_ctrl.addr_begin & 0x0000FFFF.U))
  //   rd_block_index := addr2index(io.r_ctrl.addr_begin & 0x0000FFFF.U)
  //   rd_block_index_max := addr2index((io.r_ctrl.addr_begin & 0x0000FFFF.U) + io.r_ctrl.length)
  // }
  // when(io.r_ctrl.en){
  //   when(r_fire()){
  //     rd_temp_cnt := rd_temp_cnt + 1.U
  //     when(rd_temp_cnt < (block_width/base_width-1).U){
  //       rd_data_temp := rd_data_temp >> base_width
  //     }.otherwise{
  //       rd_data_temp := blocks(rd_block_index + 1.U)
  //       rd_block_index := rd_block_index + 1.U
  //     }
  //   }
  // }
}




// class BaseBuffer(size: Int = 1024,base_width: Int = 512) extends Module {
//   def block_width = 2048
//   def nblock = size / block_width
//   def addr2index(addr: UInt) = addr >> log2Up(block_width / C.W_DATA)
//   lazy val io = new BufferIO(base_width)
//   val blocks = Mem(nblock, UInt(width = block_width))
//   //--------------------------- write ---------------------------
//   def w_fire(): Bool = io.w.valid && io.w.ready

//   val wr_en_posedge = io.w_ctrl.en && !RegNext(io.w_ctrl.en)
//   val wr_block_index = RegInit(0.U(16.W))
//   val wr_block_index_max = RegInit(0.U(16.W))
//   val wr_done = Wire(Bool())
//   val wr_data_temp = RegInit(0.U(block_width.W))
//   val wr_data_temp_next = Wire(UInt(width = block_width))
//   val wr_temp_cnt = RegInit(0.U(log2Up(block_width/base_width).W))
//   wr_data_temp_next := Cat(io.w.bits.data,wr_data_temp(block_width-1,base_width))
  
//   wr_done := wr_block_index >= wr_block_index_max && io.w_ctrl.en && RegNext(io.w_ctrl.en)
//   io.w_ctrl.done := wr_done
//   //
//   when(wr_en_posedge) {
//     wr_temp_cnt := 0.U
//     wr_block_index := addr2index(io.w_ctrl.addr_begin & 0x0000FFFF.U)
//     wr_block_index_max := addr2index((io.w_ctrl.addr_begin & 0x0000FFFF.U) + io.w_ctrl.length)
//   }
//   when(io.w_ctrl.en){
//     when(w_fire()){
//       wr_data_temp := wr_data_temp_next
//       wr_temp_cnt := wr_temp_cnt + 1.U
//       when(wr_temp_cnt === (block_width/base_width-1).U){
//         blocks(wr_block_index) := wr_data_temp_next
//         wr_block_index := wr_block_index + 1.U
//       }
//     }
//   }
//   io.w.ready := io.w_ctrl.en
//   //--------------------------- read ---------------------------
//   def r_fire(): Bool = io.r.valid && io.r.ready
//   val rd_en_posedge = io.r_ctrl.en && !RegNext(io.r_ctrl.en)
//   val rd_block_index = RegInit(0.U(16.W))
//   val rd_block_index_max = RegInit(0.U(16.W))
//   val rd_done = Wire(Bool())
//   val rd_data_temp = RegInit(0.U(block_width.W))
//   val rd_temp_cnt = RegInit(0.U(log2Up(block_width/base_width).W))
//   rd_done := rd_block_index >= rd_block_index_max && io.r_ctrl.en && RegNext(io.r_ctrl.en)
//   io.r_ctrl.done := rd_done
//   // io.r.bits.data := Cat(blocks(rd_block_index + 1.U),blocks(rd_block_index))
//   io.r.bits.data := rd_data_temp

//   io.r.valid := io.r_ctrl.en && RegNext(io.r_ctrl.en) && !rd_done

//   when(rd_en_posedge) {
//     rd_temp_cnt := 0.U
//     rd_data_temp := blocks(addr2index(io.r_ctrl.addr_begin & 0x0000FFFF.U))
//     rd_block_index := addr2index(io.r_ctrl.addr_begin & 0x0000FFFF.U)
//     rd_block_index_max := addr2index((io.r_ctrl.addr_begin & 0x0000FFFF.U) + io.r_ctrl.length)
//   }
//   when(io.r_ctrl.en){
//     when(r_fire()){
//       rd_temp_cnt := rd_temp_cnt + 1.U
//       when(rd_temp_cnt < (block_width/base_width-1).U){
//         rd_data_temp := rd_data_temp >> base_width
//       }.otherwise{
//         rd_data_temp := blocks(rd_block_index + 1.U)
//         rd_block_index := rd_block_index + 1.U
//       }
//     }
//   }
// }
