// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.tile

import Chisel._
import chisel3.util.HasBlackBoxResource
import chisel3.experimental.IntParam
import chisel3.experimental.{chiselName, NoChiselNamePrefix}

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.InOrderArbiter
import com.google.protobuf.UInt32Value

class CustomRocc(opcodes: OpcodeSet, val n: Int = 16)(implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new CustomRoccModuleImp(this)
}

@chiselName
class CustomRoccModuleImp(outer: CustomRocc)(implicit p: Parameters) extends LazyRoCCModuleImp(outer)
    with HasCoreParameters {
  val regfile   = Mem(outer.n, UInt(width = xLen))
  val buffer_io = Module(new CustomBuffer(BufferConstant.IOBUFFER_BLOCK_N))
  val buffer_f  = Module(new CustomBuffer(8))
  val busy_regfile = Reg(init = Vec.fill(outer.n){0.U(2.W)})
  val busy_buffer_io = Reg(init = Vec.fill(BufferConstant.IOBUFFER_BLOCK_N){0.U(2.W)})
  def NOTBUSY      = UInt(0)
  def DMABUSY      = UInt(1)
  def MVBUSY       = UInt(2)
  // 11:resv
  val cmd   = Queue(io.cmd)
  val funct = cmd.bits.inst.funct
  val addr  = cmd.bits.rs2(log2Up(outer.n)-1,0)
  //-------------------------- instruction --------------------------
  // i r r rs1:target reg rs2:value
  val doWrite = funct === UInt(0)
  // r r i rs1:source reg
  val doRead = funct === UInt(1)
  // i r r rs1:target reg rs2:source addr
  val doLoad = funct === UInt(2)
  // i r r rs1:source reg rs2:target addr
  val doStore = funct === UInt(3)
  // i r r rs1:des addr   rs2:source addr
  val doMove = funct === UInt(4)
  val memRespTag = io.mem.resp.bits.tag
  // -------------------------- state define --------------------------------
  val reg_write = cmd.bits.rs1(log2Up(outer.n)-1,0)
  val value_read = regfile(cmd.bits.rs1)
  val value_write = cmd.bits.rs2
  val value_resp = value_read
  // core_state
  val core_state = RegInit(0.U(8.W))
  def VIDLE      = UInt(0)
  def VLOAD      = UInt(1) 
  def VSTORE     = UInt(2) 
  def WRITE      = UInt(3)
  def VMOV       = UInt(4)

  // dma_state
  val dma_mem_state = RegInit(0.U(8.W)) //between mem and io buffer
  val dma_mem_state_next = RegInit(0.U(8.W))
  def DMAIDLE   = UInt(0)
  def DMAWAIT   = UInt(1)
  def DMALOAD   = UInt(2)
  def DMASTORE  = UInt(3)
  val dma_i_state        = RegInit(0.U(8.W))  //between io buffer and f/w buffer
  val dma_i_state_next   = RegInit(0.U(8.W))  //
  def DMAMOVE   = UInt(2)

  // regfile def
  val v_ls_length = regfile(0)
  val v_ls_mask   = regfile(1)(BufferConstant.ADDR_WIDTH-1,0)

  val v_mv_length = regfile(4)

  // constant
  def reg_addr_width = log2Up(outer.n)
  def reg_index_shift_bits = log2Up(xLen/8)
  def block_reg_ratio = log2Up(BufferConstant.BLOCK_WIDTH/xLen)
  def cnt2block(cnt:UInt) = cnt >> block_reg_ratio
  def addr2block(addr_begin:UInt) = (addr_begin & 0x0000FFFF.U) >> (block_reg_ratio + reg_index_shift_bits)
  def addr2reg(addr_begin:UInt)  = (addr_begin & 0x0000FFFF.U) >> reg_index_shift_bits
  val core_temp_rs1 = RegInit(0.U(xLen.W))
  val core_temp_rs2 = RegInit(0.U(xLen.W))

  // dma mem control register
  val dma_mem_rocc_addr = RegInit(0.U(xLen.W))
  val dma_mem_req_cnt = RegInit(0.U(16.W))
  val dma_mem_resp_cnt = RegInit(0.U(16.W))
  val dma_mem_req_addr = RegInit(0.U(xLen.W))
  val dma_mem_req_reg_index = addr2reg(dma_mem_rocc_addr) + dma_mem_req_cnt
  val dma_mem_load_done = dma_mem_resp_cnt >= v_ls_length
  val dma_mem_store_done = dma_mem_resp_cnt >= v_ls_length// && io.mem.req.ready
  val dma_mem_load_req_valid = dma_mem_state === DMALOAD && dma_mem_req_cnt < v_ls_length 
  val dma_mem_store_req_valid = dma_mem_state === DMASTORE && dma_mem_req_cnt < v_ls_length 
  val dma_mem_buffer_io_ready = busy_buffer_io(addr2block(dma_mem_rocc_addr)) === NOTBUSY && busy_buffer_io(addr2block(dma_mem_rocc_addr) + cnt2block(v_ls_length) - 1.U) === NOTBUSY
  // dma0 mem control register
  val mem_dma_start = dma_mem_state === DMAIDLE && (core_state === VSTORE || core_state === VLOAD)
  val buffer_io_block_temp = RegInit(0.U(BufferConstant.BLOCK_WIDTH.W))
  // switch
  val load_to_buffer_io = dma_mem_rocc_addr >= RoccAddr.ADDR_IOBUFFER.U && dma_mem_rocc_addr < RoccAddr.ADDR_FBUFFER.U
  val load_to_regfile = dma_mem_rocc_addr < RoccAddr.ADDR_IOBUFFER.U
  val sw_buffer_io_rd = dma_mem_state === DMASTORE && load_to_buffer_io // 1:mem store 0:move to buffer_f

  // buffer_io
  def in_buffer_io(addr:UInt) = addr >= RoccAddr.ADDR_IOBUFFER.U && addr < RoccAddr.ADDR_FBUFFER.U
  val buffer_io_rden_store = dma_mem_state === DMASTORE && load_to_buffer_io && dma_mem_req_cnt < v_ls_length
  val buffer_io_rden_move = Wire(Bool())


  // buffer_f
  def in_buffer_f(addr:UInt) = addr >= RoccAddr.ADDR_FBUFFER.U && addr < RoccAddr.ADDR_WBUFFER.U
  val dma_i_mv_start = dma_i_state === DMAIDLE && core_state === VMOV && in_buffer_f(core_temp_rs1) && in_buffer_io(core_temp_rs2)
  val dma_i_move_cnt = RegInit(0.U(16.W))
  val dma_i_addr_src = RegInit(0.U(xLen.W))
  val dma_i_addr_des = RegInit(0.U(xLen.W))
  val dma_i_move_done = dma_i_move_cnt >= v_mv_length
  val dma_i_buffer_io_ready = busy_buffer_io(addr2block(dma_i_addr_src)) === NOTBUSY && busy_buffer_io(addr2block(dma_i_addr_src) + cnt2block(v_mv_length) - 1.U) === NOTBUSY
  val dma_i_can_move = dma_i_state === DMAWAIT && dma_mem_state =/= DMASTORE && dma_i_buffer_io_ready 

  val no_move_store_conflit = dma_mem_state_next =/= DMASTORE || dma_i_state =/= DMAMOVE
  val dma_mem_can_start = dma_mem_state === DMAWAIT && no_move_store_conflit && dma_mem_buffer_io_ready
  // --------------------------- buffer connection ---------------------------
  buffer_io.io.wr_en := dma_mem_state === DMALOAD && load_to_buffer_io 
  buffer_io.io.w_addr := dma_mem_rocc_addr
  buffer_io.io.w_data := io.mem.resp.bits.data
  buffer_io.io.w_valid := io.mem.resp.valid
  // buffer_io.io.w_ready :=  [todo] busy
  buffer_io.io.rd_en := Mux(sw_buffer_io_rd,buffer_io_rden_store,buffer_io_rden_move)
  buffer_io.io.r_addr := Mux(sw_buffer_io_rd,dma_mem_rocc_addr,dma_i_addr_src)
  buffer_io.io.r_ready := Mux(sw_buffer_io_rd,io.mem.req.ready,buffer_f.io.w_ready)
  //
  buffer_io_rden_move := dma_i_state === DMAMOVE && dma_i_move_cnt < v_mv_length
  buffer_f.io.wr_en   := dma_i_state === DMAMOVE
  buffer_f.io.w_addr  := dma_i_addr_des
  buffer_f.io.w_data  := buffer_io.io.r_data
  buffer_f.io.w_valid := buffer_io.io.r_valid

  // mem_io
  def addr_delta = 8.U// 64 bytes or 8 bytes
  val mem_cmd = RegInit(M_XRD)
  var mem_req_valid = Mux(dma_mem_state === DMAIDLE || dma_mem_state === DMAWAIT,false.B,
                          Mux(dma_mem_state === DMALOAD,dma_mem_load_req_valid,
                              Mux(load_to_buffer_io,buffer_io.io.r_valid,dma_mem_store_req_valid
                          )))
  val mem_req_data = Mux(load_to_buffer_io,buffer_io.io.r_data,regfile(dma_mem_req_reg_index))// for store
  val mem_req_addr = dma_mem_req_addr
  val mem_req_tag = Cat(0.U(3.W),dma_mem_req_cnt(4,0))
  val mem_req_size = log2Ceil(8).U

  // busy management
  for(r <- 0 until BufferConstant.IOBUFFER_BLOCK_N){
    when(dma_mem_can_start
      && in_buffer_io(core_temp_rs1) 
      && r.U >= addr2block(core_temp_rs1)
      && r.U < addr2block(core_temp_rs1) + cnt2block(v_ls_length)){
      busy_buffer_io(r) := DMABUSY
    }
    when(dma_i_can_move
    && r.U >= addr2block(dma_i_addr_src)
    && r.U < addr2block(dma_i_addr_src) + cnt2block(v_mv_length)){
      busy_buffer_io(r) := MVBUSY
    }
  }
  // debug
  val debug_port = Wire(Vec(4, UInt(width = xLen)))
  debug_port(0) := addr2block(dma_i_addr_src)
  debug_port(1) := cnt2block(v_mv_length)
  debug_port(2) := addr2block(dma_i_addr_src) + cnt2block(v_mv_length) - 1.U
  val debug_io_mem_req_fire = io.mem.req.valid && io.mem.req.ready
  chisel3.dontTouch(debug_port)
  chisel3.dontTouch(busy_buffer_io)
  chisel3.dontTouch(debug_io_mem_req_fire)
  //--------------------------- _core ---------------------------
  when(cmd.fire()){
    core_temp_rs1 := cmd.bits.rs1
    core_temp_rs2 := cmd.bits.rs2
    when(doLoad){
      core_state := VLOAD
    }.elsewhen(doStore){
      core_state := VSTORE
    }.elsewhen(doWrite){
      core_state := WRITE
    }.elsewhen(doMove){
      core_state := VMOV
    }
  }
  // exec
  when(core_state === WRITE && !busy_regfile(core_temp_rs1)){
    regfile(core_temp_rs1) := core_temp_rs2
    core_state := VIDLE
  }
  when(core_state === VLOAD && dma_mem_state === DMAIDLE){
    core_state := VIDLE
  }
  when(core_state === VSTORE && dma_mem_state === DMAIDLE){
    core_state := VIDLE
  }
  when(core_state === VMOV && dma_i_state === DMAIDLE){
    core_state := VIDLE
  }
  // --------------------------- _mem_dma ---------------------------
  
  when(mem_dma_start){
    dma_mem_rocc_addr := core_temp_rs1 // get rocc addr
    dma_mem_req_addr := core_temp_rs2 // get memory addr
    dma_mem_req_cnt := 0.U
    dma_mem_resp_cnt := 0.U
    busy_regfile(0) := DMABUSY
    dma_mem_state := DMAWAIT
    when(core_state === VLOAD){
      dma_mem_state_next := DMALOAD
      mem_cmd := M_XRD
    }
    when(core_state === VSTORE){
      dma_mem_state_next := DMASTORE
      mem_cmd := M_XWR
    }
  }

  when(dma_mem_state === DMAWAIT){
    when(dma_mem_can_start){
      dma_mem_state := dma_mem_state_next
    }
  }
  
  when(dma_mem_state === DMALOAD){
    when(io.mem.req.fire()){
      dma_mem_req_addr := dma_mem_req_addr + addr_delta
      dma_mem_req_cnt := dma_mem_req_cnt + 1.U
    }
    when(dma_mem_load_done){
      dma_mem_state := DMAIDLE
      busy_regfile(0) := NOTBUSY
    }
    when(io.mem.resp.valid){
      dma_mem_resp_cnt := dma_mem_resp_cnt + 1.U
      when(dma_mem_resp_cnt(block_reg_ratio-1,0) === (BufferConstant.BLOCK_WIDTH/xLen).U - 1.U){
        busy_buffer_io(addr2block(dma_mem_rocc_addr) + cnt2block(dma_mem_resp_cnt)) := NOTBUSY
      }
      when(load_to_regfile){
        regfile(addr2reg(dma_mem_rocc_addr) + dma_mem_resp_cnt) := io.mem.resp.bits.data
      }
    }

  }

  when(dma_mem_state === DMASTORE){
    when(io.mem.req.fire()){
      dma_mem_req_addr := dma_mem_req_addr + addr_delta
      dma_mem_req_cnt := dma_mem_req_cnt + 1.U
      when(dma_mem_req_cnt(block_reg_ratio-1,0) === (BufferConstant.BLOCK_WIDTH/xLen).U - 1.U){
        busy_buffer_io(addr2block(dma_mem_rocc_addr) + cnt2block(dma_mem_req_cnt)) := NOTBUSY
      }
    }
    when(io.mem.resp.valid){
      dma_mem_resp_cnt := dma_mem_resp_cnt + 1.U
    }
    when(dma_mem_store_done){
      dma_mem_state := DMAIDLE
      busy_regfile(0) := NOTBUSY
    }
  }
  //--------------------------- _i_dma ---------------------------
  when(dma_i_mv_start){
    dma_i_addr_des := core_temp_rs1
    dma_i_addr_src := core_temp_rs2
    dma_i_move_cnt := 0.U
    busy_regfile(4) := DMABUSY
    dma_i_state := DMAWAIT
    when(core_state === VMOV){
      dma_i_state_next := DMAMOVE
    }
  }
  
  when(dma_i_state === DMAWAIT){
    when(dma_i_can_move){
      dma_i_state := dma_i_state_next
    }
  }

  when(dma_i_state === DMAMOVE){
    when(buffer_f.io.w_ready && buffer_f.io.w_valid){
      dma_i_move_cnt := dma_i_move_cnt + 1.U
      when(dma_i_move_cnt(block_reg_ratio-1,0) === (BufferConstant.BLOCK_WIDTH/xLen).U - 1.U){
        busy_buffer_io(addr2block(dma_i_addr_src) + cnt2block(dma_i_move_cnt)) := NOTBUSY
      }
    }
    when(dma_i_move_done){
      dma_i_state := DMAIDLE
      busy_regfile(4) := NOTBUSY
    }
  }



  // --------------------------- rocc resp ---------------------------
  val doResp = cmd.bits.inst.xd
  // val stallReg = busy_regfile(cmd.bits.rs1) =/= 0.U
  val stallReg = false.B
  val stallLoad = dma_mem_state =/= DMAIDLE
  val stallResp = doResp && !io.resp.ready

  // cmd.ready := !stallReg && core_state === VIDLE && !stallResp
  cmd.ready := core_state === VIDLE
    // command resolved if no stalls AND not issuing a load that will need a request

  // PROC RESPONSE INTERFACE
  io.resp.valid := cmd.valid && doResp && !stallReg
    // valid response if valid command, need a response, and no stalls
  io.resp.bits.rd := cmd.bits.inst.rd
    // Must respond with the appropriate tag or undefined behavior
  // io.resp.bits.data := accum
  io.resp.bits.data := value_resp
    // Semantics is to always send out prior accumulator register value

  // io.busy := cmd.valid || busy.reduce(_||_)
  io.busy := cmd.valid
    // Be busy when have pending memory requests or committed possibility of pending requests
  io.interrupt := Bool(false)
    // Set this true to trigger an interrupt on the processor (please refer to supervisor documentation)

  // MEMORY REQUEST INTERFACE
  // io.mem.req.valid := cmd.valid && doLoad && !stallReg && !stallResp
  io.mem.req.valid := mem_req_valid
  io.mem.req.bits.addr := mem_req_addr
  io.mem.req.bits.tag := mem_req_tag

  io.mem.req.bits.cmd := mem_cmd // perform a load (M_XWR for stores)
  io.mem.req.bits.size := mem_req_size
  io.mem.req.bits.signed := Bool(false)
  io.mem.req.bits.data := mem_req_data
  io.mem.req.bits.phys := Bool(false)
  io.mem.req.bits.dprv := cmd.bits.status.dprv
}

