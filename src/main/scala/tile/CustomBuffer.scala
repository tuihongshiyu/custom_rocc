package freechips.rocketchip.tile

import Chisel._
import chisel3.util.HasBlackBoxResource
import chisel3.experimental.IntParam
import chisel3.experimental.{chiselName, NoChiselNamePrefix}

class CustomBuffer(nBlock:Int,wr_width :Int = 64,rd_width :Int = 64 , block_width:Int = 4096) extends Module{
  def xLen = 64
  def WrMaxCounter = (block_width / wr_width).U
  def RdMaxCounter = (block_width / rd_width).U
  def block_addr_shift_bits = log2Up(block_width/8)
  val io = IO(new Bundle{
    val wr_en = Input(Bool())
    val w_addr = Input(UInt(width = BufferConstant.ADDR_WIDTH))
    val w_data = Input(UInt(width = wr_width))
    val w_valid = Input(Bool())
    val w_ready = Output(Bool())

    val rd_en = Input(Bool())
    val r_addr = Input(UInt(width = BufferConstant.ADDR_WIDTH))
    val r_data = Output(UInt(width = rd_width))
    val r_valid = Output(Bool())
    val r_ready = Input(Bool())
  })

  val blocks = Mem(nBlock,UInt(width = block_width))
  val blocks_busy = Reg(init = Vec.fill(nBlock){Bool(false)})
  // write
  val wr_temp_block = RegInit(0.U(block_width.W))
  val wr_counter = RegInit(0.U(16.W))
  val wr_en_posedge = io.wr_en && !RegNext(io.wr_en)
  val wr_block_index = RegInit(0.U(16.W))
  val temp_block_next = Cat(io.w_data,wr_temp_block(block_width-1,wr_width))//right shift
  //
  when(wr_en_posedge){
    wr_block_index := (io.w_addr & 0x0000FFFF.U) >> block_addr_shift_bits
  }
  when(io.wr_en){
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
  val rd_en_posedge = io.rd_en && !RegNext(io.rd_en)
  val rd_block_index = RegInit(0.U(16.W))

  when(rd_en_posedge){
    rd_block_index := (io.r_addr & 0x0000FFFF.U) >> block_addr_shift_bits
    rd_temp_block := blocks((io.r_addr & 0x0000FFFF.U) >> block_addr_shift_bits)
    rd_counter := 0.U
  }
  when(io.rd_en){
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
  io.r_valid := io.rd_en && RegNext(io.rd_en)
  io.r_data := rd_temp_block

  def w_fire(): Bool = io.w_valid && io.w_ready
  def r_fire(): Bool = io.r_valid && io.r_ready

}
