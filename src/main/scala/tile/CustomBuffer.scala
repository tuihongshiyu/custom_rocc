package freechips.rocketchip.tile

import Chisel._
import chisel3.util.HasBlackBoxResource
import chisel3.experimental.IntParam
import chisel3.experimental.{chiselName, NoChiselNamePrefix}

class BufferBaseIO(wr_width :Int,rd_width :Int) extends Bundle{
    val buffer_mat_n = Input(UInt(width = log2Up(MatC.MAX_N)))

    val w_en = Input(Bool())
    val w_addr = Input(UInt(width = BufferConstant.ADDR_WIDTH))
    val w_data = Input(UInt(width = wr_width))
    val w_valid = Input(Bool())
    val w_ready = Output(Bool())

    val r_en = Input(Bool())
    val r_addr = Input(UInt(width = BufferConstant.ADDR_WIDTH))
    val r_data = Output(UInt(width = rd_width))
    val r_valid = Output(Bool())
    val r_ready = Input(Bool())
}

class Buffer2RdIO(wr_width :Int,rd_width :Int,ex_rd_width :Int) extends BufferBaseIO(wr_width,rd_width){
    val ex_r_en = Input(Bool())
    val ex_r_addr = Input(UInt(width = BufferConstant.ADDR_WIDTH))
    val ex_r_data = Output(Vec(MatC.MAX_N,UInt(width = MatC.WIDTH)))
    val ex_r_valid = Output(Vec(MatC.MAX_N,Bool()))
    val ex_r_ready = Input(Bool())
    
}

class Buffer2Wr2RdIO(wr_width :Int,ex_wr_width :Int,rd_width :Int,ex_rd_width :Int) extends Buffer2RdIO(wr_width,rd_width,ex_rd_width){
    val ex_w_en = Input(Bool())
    val ex_w_addr = Input(UInt(width = BufferConstant.ADDR_WIDTH))
    val ex_w_data = Input(UInt(width = ex_wr_width))
    val ex_w_valid = Input(Bool())
    val ex_w_ready = Output(Bool())
}

class CustomBuffer(nBlock:Int,wr_width :Int,rd_width :Int, block_width:Int = BufferConstant.BLOCK_WIDTH) extends Module{
  def xLen = 64
  def WrMaxCounter = (block_width / wr_width).U
  def RdMaxCounter = (block_width / rd_width).U
  def block_addr_shift_bits = log2Up(block_width/8)
  def counter_width = log2Up(block_width/8)
  lazy val io = new BufferBaseIO(wr_width,rd_width)

  val blocks = Mem(nBlock,UInt(width = block_width))

  // write
  val wr_temp_block = RegInit(0.U(block_width.W))
  val wr_counter = RegInit(0.U(16.W))
  val wr_en_posedge = io.w_en && !RegNext(io.w_en)
  val wr_block_index = RegInit(0.U(16.W))
  val temp_block_next = Cat(io.w_data,wr_temp_block(block_width-1,wr_width))//right shift
  //
  chisel3.dontTouch(wr_temp_block)
  when(wr_en_posedge){
    wr_block_index := (io.w_addr & 0x0000FFFF.U) >> block_addr_shift_bits
  }
  when(io.w_en){
    when(io.w_valid){
      when(wr_counter >= WrMaxCounter-1.U){
        blocks(wr_block_index) := temp_block_next
        wr_block_index := wr_block_index + 1.U
        wr_counter := 0.U
      }.otherwise{
        wr_counter := wr_counter + 1.U
        wr_temp_block := temp_block_next
      }
    }
  }.otherwise{
    wr_counter := 0.U
  }
  io.w_ready := true.B
  // read
  val rd_temp_block = RegInit(0.U(block_width.W))
  val rd_counter = RegInit(0.U(16.W))
  val rd_en_posedge = io.r_en && !RegNext(io.r_en)
  val rd_block_index = RegInit(0.U(16.W))

  when(rd_en_posedge){
    rd_block_index := (io.r_addr & 0x0000FFFF.U) >> block_addr_shift_bits
    rd_temp_block := blocks((io.r_addr & 0x0000FFFF.U) >> block_addr_shift_bits)
    rd_counter := 0.U
  }
  when(io.r_en){
    when(r_fire){
      when(rd_counter >= RdMaxCounter-1.U){
        rd_counter := 0.U
        rd_block_index := rd_block_index + 1.U
        rd_temp_block := blocks(rd_block_index + 1.U)
      }.otherwise{
        rd_counter := rd_counter + 1.U
        rd_temp_block := rd_temp_block >> rd_width
      }
    }
  }
  io.r_valid := io.r_en && RegNext(io.r_en)
  io.r_data := rd_temp_block

  def w_fire(): Bool = io.w_valid && io.w_ready
  def r_fire(): Bool = io.r_valid && io.r_ready

}

class CustomBufferSpWr(nBlock:Int,wr_width :Int,rd_width :Int, block_width:Int = BufferConstant.BLOCK_WIDTH) extends Module{
  // mat
  def mat_cnt_width = log2Up(MatC.MAX_N)
  def mat_row_width = MatC.MAX_N * MatC.WIDTH
  def block_divide_row = BufferConstant.BLOCK_WIDTH / mat_row_width
  // other
  def xLen = 64
  def RdMaxCounter = (block_width / rd_width).U
  def block_addr_shift_bits = log2Up(block_width/8)
  def counter_width = log2Up(block_width/8)
  lazy val io = new BufferBaseIO(wr_width,rd_width)

  val blocks = Mem(nBlock,UInt(width = block_width))
  // mat write  
  val wr_mat_n_reg =  RegInit(0.U((mat_cnt_width).W))
  val wr_mat_n = Mux(wr_mat_n_reg === 0.U,MatC.MAX_N.U,wr_mat_n_reg)
  val wr_temp_rows = Reg(init = Vec.fill(block_divide_row){0.U(mat_row_width.W)})
  val wr_temp_cnt  = Reg(init = Vec.fill(block_divide_row){0.U((2 * mat_cnt_width).W)})
  val wr_temp_total_cnt = Wire(UInt((2 * mat_cnt_width).W))

  val wr_mat_temp_block = Cat(wr_temp_rows.reverse)
  val wr_temp_mat_done = RegInit(false.B)

  // write
  val wr_counter = RegInit(0.U(16.W))
  val wr_en_posedge = io.w_en && !RegNext(io.w_en)
  val wr_block_index = RegInit(0.U(16.W))
  // mat logic
  wr_temp_total_cnt := wr_temp_cnt.reduce(_+_)
  chisel3.dontTouch(wr_temp_cnt)
  chisel3.dontTouch(wr_temp_rows)
  chisel3.dontTouch(wr_temp_total_cnt)
  chisel3.dontTouch(wr_temp_mat_done)

  when(wr_en_posedge){//初始化block指针
    wr_block_index := (io.w_addr & 0x0000FFFF.U) >> block_addr_shift_bits
  }

  when(io.w_en){//有效数据计数
    when(io.w_valid){
      when(wr_counter >= wr_mat_n * wr_mat_n -1.U){
        wr_counter := 0.U
      }.otherwise{
        wr_counter := wr_counter + 1.U
      }
    }
  }

  when(!io.w_en){//各种寄存器初始化
    wr_counter := 0.U
    wr_mat_n_reg := io.buffer_mat_n
    for (i <- 0 until block_divide_row){
      wr_temp_cnt(i) := 0.U
    }
  }

  for (i <- 0 until block_divide_row){//帧结尾处理
    when(wr_temp_total_cnt === wr_mat_n * MatC.MAX_N.U - 1.U){
      when(wr_mat_n ===  MatC.MAX_N.U){
        when(io.w_valid){
          wr_temp_cnt(i) := 0.U
          wr_temp_mat_done := true.B
        }
      }.otherwise{
        wr_temp_cnt(i) := 0.U
        wr_temp_mat_done := true.B
      }
    }
  }

  when(wr_temp_mat_done){
    wr_block_index := wr_block_index + 1.U
    blocks(wr_block_index) := wr_mat_temp_block
  }

  for (i <- 0 until block_divide_row){
    when(io.w_en){
      when(wr_counter >= i.U * wr_mat_n && wr_temp_cnt(i) < wr_mat_n && i.U < wr_mat_n){//有效数据
        when(io.w_valid){
          wr_temp_rows(i) := Cat(io.w_data,wr_temp_rows(i)(mat_row_width - 1, wr_width))
          when(wr_counter === ((MatC.MAX_N * MatC.MAX_N).U - 1.U)){
            wr_temp_cnt(i) := 0.U
          }.otherwise{
            wr_temp_cnt(i) := wr_temp_cnt(i) + 1.U
          }
        }
      }.elsewhen(wr_temp_cnt(i) >= wr_mat_n && wr_temp_cnt(i) < MatC.MAX_N.U && wr_temp_total_cnt < wr_mat_n * MatC.MAX_N.U - 1.U){
        wr_temp_rows(i) := Cat(0.U,wr_temp_rows(i)(mat_row_width - 1, wr_width))
        wr_temp_cnt(i) := wr_temp_cnt(i) + 1.U
      }
    }
  }
  
  when(wr_temp_mat_done){
    wr_temp_mat_done := false.B
  }

  io.w_ready := wr_temp_mat_done
  // read
  val rd_temp_block = RegInit(0.U(block_width.W))
  val rd_counter = RegInit(0.U(16.W))
  val rd_en_posedge = io.r_en && !RegNext(io.r_en)
  val rd_block_index = RegInit(0.U(16.W))

  when(rd_en_posedge){
    rd_block_index := (io.r_addr & 0x0000FFFF.U) >> block_addr_shift_bits
    rd_temp_block := blocks((io.r_addr & 0x0000FFFF.U) >> block_addr_shift_bits)
    rd_counter := 0.U
  }
  when(io.r_en){
    when(r_fire){
      when(rd_counter >= RdMaxCounter-1.U){
        rd_counter := 0.U
        rd_block_index := rd_block_index + 1.U
        rd_temp_block := blocks(rd_block_index + 1.U)
      }.otherwise{
        rd_counter := rd_counter + 1.U
        rd_temp_block := rd_temp_block >> rd_width
      }
    }
  }
  io.r_valid := io.r_en && RegNext(io.r_en)
  io.r_data := rd_temp_block

  def w_fire(): Bool = io.w_valid && io.w_ready
  def r_fire(): Bool = io.r_valid && io.r_ready

}

class CustomBuffer2Rd(nBlock:Int,wr_width :Int,rd_width :Int,ex_rd_width :Int, block_width:Int = BufferConstant.BLOCK_WIDTH) extends CustomBufferSpWr(nBlock,wr_width,rd_width,block_width){
  override lazy val io = new Buffer2RdIO(wr_width,rd_width,ex_rd_width)

  //
  val ex_rd_temp_rows = Reg(init = Vec.fill(MatC.MAX_N){0.U(mat_row_width.W)})
  val ex_rd_counter = RegInit(0.U((mat_cnt_width+1).W))
  val ex_rd_en_posedge = io.ex_r_en && !RegNext(io.ex_r_en)
  // ex_read
  val ex_rd_full_matrix = blocks((io.ex_r_addr & 0x0000FFFF.U) >> block_addr_shift_bits)
  val ex_rd_mat_n_reg =  RegInit(0.U((mat_cnt_width).W))
  val ex_rd_mat_n = Mux(ex_rd_mat_n_reg === 0.U,MatC.MAX_N.U,ex_rd_mat_n_reg)
  val ex_rd_counter_max = 2.U * ex_rd_mat_n_reg - 1.U
  val ex_rd_started = io.ex_r_en && RegNext(io.ex_r_en)
  //
  when(!io.ex_r_en){
    ex_rd_counter := 0.U
  }

  when(ex_rd_en_posedge){
    ex_rd_mat_n_reg := io.buffer_mat_n
    ex_rd_counter := 0.U
    for(i <- 0 until MatC.MAX_N){
      ex_rd_temp_rows(i) := ex_rd_full_matrix((i + 1) * mat_row_width - 1,i * mat_row_width)
    }
  }

  when(ex_rd_started && ex_rd_counter < ex_rd_counter_max){
    ex_rd_counter := ex_rd_counter + 1.U;
  }

  for(i <- 0 until MatC.MAX_N){
    when(io.ex_r_valid(i)){
      ex_rd_temp_rows(i) := ex_rd_temp_rows(i) >> MatC.WIDTH
    }
  }
  
  for(i <- 0 until MatC.MAX_N){
    io.ex_r_valid(i) := ex_rd_started && ex_rd_counter >= i.U && ex_rd_counter < i.U + ex_rd_mat_n && i.U < ex_rd_mat_n
    io.ex_r_data(i) := ex_rd_temp_rows(i)
  }
}

class CustomBuffer2Wr2Rd(nBlock:Int,wr_width :Int,ex_wr_width :Int,rd_width :Int,ex_rd_width :Int, block_width:Int = BufferConstant.BLOCK_WIDTH) extends CustomBuffer2Rd(nBlock,wr_width,rd_width,ex_rd_width,block_width){
  override lazy val io = new Buffer2Wr2RdIO(wr_width,ex_wr_width,rd_width,ex_rd_width)

  def ExWrMaxCounter = (block_width / ex_wr_width).U
  // ex_write
  val ex_wr_temp_block = RegInit(0.U(block_width.W))
  val ex_wr_counter = RegInit(0.U(16.W))
  val ex_wr_en_posedge = io.ex_w_en && !RegNext(io.ex_w_en)
  val ex_wr_block_index = RegInit(0.U(16.W))
  val ex_temp_block_next = Cat(io.ex_w_data,ex_wr_temp_block(block_width-1,ex_wr_width))//right shift
  //
  when(ex_wr_en_posedge){
    ex_wr_block_index := (io.ex_w_addr & 0x0000FFFF.U) >> block_addr_shift_bits
  }
  when(io.ex_w_en){
    when(io.ex_w_valid){
      when(ex_wr_counter >= ExWrMaxCounter-1.U){
        blocks(ex_wr_block_index) := ex_temp_block_next
        ex_wr_block_index := ex_wr_block_index + 1.U
        ex_wr_counter := 0.U
      }.otherwise{
        ex_wr_counter := ex_wr_counter + 1.U
        ex_wr_temp_block := ex_temp_block_next
      }
    }
  }.otherwise{
    ex_wr_counter := 0.U
  }
  io.ex_w_ready := true.B
}
