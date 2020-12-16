// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.tile

import Chisel._
import chisel3.VecInit
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
import chisel3.util.MixedVec

class CustomRocc(opcodes: OpcodeSet, val n: Int = 16)(implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new CustomRoccModuleImp(this)
}

@chiselName
class CustomRoccModuleImp(outer: CustomRocc)(implicit p: Parameters) extends LazyRoCCModuleImp(outer)
  with HasCoreParameters {
  // -------------------------- submodule --------------------------
  val regfile = Mem(outer.n, UInt(width = xLen))
  val gemm = Module(new GEMM)
  val buffer_seq = Seq(
    Module(new InputBuffer  (size = CS.Input))  ,////Input A
    Module(new InputBuffer  (size = CS.Input))  ,//Input B
    Module(new WFBuffer     (size = CS.Feature)),//Feature A
    Module(new WFBuffer     (size = CS.Feature)),//Feature B
    Module(new WFBuffer     (size = CS.Weight)) ,//Weight A
    Module(new WFBuffer     (size = CS.Weight)) ,//Weight B
    Module(new MiddleBuffer (size = CS.Middle)) ,//Middle A
    Module(new MiddleBuffer (size = CS.Middle)) ,//Middle B
    Module(new InputBuffer  (size = CS.Output)) ,//Output A
    Module(new InputBuffer  (size = CS.Output))  //Output B
  )
  val w_nodes = Seq(
    Module(new InNode(index = 0,n_i = 3)),//Input A
    Module(new InNode(index = 1,n_i = 3)),//Input B
    Module(new InNode(index = 2))        ,//FeatureA
    Module(new InNode(index = 3))        ,//FeatureB
    Module(new InNode(index = 4))        ,//WeightA
    Module(new InNode(index = 5))        ,//WeightB
    Module(new InNode(index = 6))        ,//MiddleA
    Module(new InNode(index = 7))        ,//MiddleB
    Module(new InNode(index = 8,n_i = 3)),//Onput A
    Module(new InNode(index = 9,n_i = 3)),//Onput B
    Module(new InNode(index = 10))       ,//DDR
    Module(new InNode(index = 11))        //MainMemory
  )
  val w_nodes_mv3 = Seq(
    Module(new InNode(index = 0)),//no use
    Module(new InNode(index = 1)),//no use
    Module(new InNode(index = 2,n_i = 2,w = C.W_MOVE3)),//FeatureA
    Module(new InNode(index = 3,n_i = 2,w = C.W_MOVE3)),//FeatureB
    Module(new InNode(index = 4,n_i = 2,w = C.W_MOVE3)),//WeightA
    Module(new InNode(index = 5,n_i = 2,w = C.W_MOVE3)) //WeightB
  )
  val r_nodes = Seq(
    Module(new OutNode(index = 0)),//Input A
    Module(new OutNode(index = 1)),//Input B
    Module(new OutNode(index = 2)),//FeatureA
    Module(new OutNode(index = 3)),//FeatureB
    Module(new OutNode(index = 4)),//WeightA
    Module(new OutNode(index = 5)),//WeightB
    Module(new OutNode(index = 6,n_o = 2)),//MiddleA
    Module(new OutNode(index = 7,n_o = 2)),//MiddleB
    Module(new OutNode(index = 8,n_o = 2)),//Onput A
    Module(new OutNode(index = 9,n_o = 2)),//Onput B
    Module(new OutNode(index = 10)),//DDR
    Module(new OutNode(index = 11,n_o = 10)) //MainMemory
  )
  val r_nodes_mv3 = Seq(
    Module(new OutNode(index = 0,n_o = 4,w = C.W_MOVE3)),//Input A
    Module(new OutNode(index = 1,n_o = 4,w = C.W_MOVE3)) //Input B
  )

  val feature_read_node = Module(new ArrayNode())
  val weight_read_node  = Module(new ArrayNode())

  val tags = Reg(init = Vec.fill(C.N_NODE) {
     0.U(C.W_INDEX.W)
  })
  
  val tag_queue = Seq.fill(C.N_NODE)(Module (new TagQueue))

  val w_done = Wire(Vec(C.N_BUFFER, Bool()))
  val r_done = Wire(Vec(C.N_BUFFER, Bool()))
  val w_en = Reg(init = Vec.fill(C.N_NODE) {false.B})
  val r_en = Reg(init = Vec.fill(C.N_NODE) {false.B})
  val w_nodes_mux = Reg(init = Vec.fill(C.N_NODE) {0.U(4.W)})
  val w_nodes_mv3_mux = Reg(init = Vec.fill(C.N_MOVE3) {0.U(4.W)})
  // array
  val array_feature_ready = Reg(init = false.B)
  val array_weight_ready = Reg(init = false.B)
  val wf_pingpong = Reg(init = 0.U(1.W))
  val gemm_do_input = Wire(Bool())
  val middle_pingpong = Reg(false.B)
  //-------------------------------------------new
  // val Custom_mesh = Module(new mesh(MeshParameter.df,MeshParameter.pe_latency,MeshParameter.meshRows,MeshParameter.meshColumns,MeshParameter.tileRows,MeshParameter.tileColumns))
  //-------------------------------------------new
  val busy_regfile = Reg(init = Vec.fill(outer.n) {
    false.B
  })
  // 11:resv
  val cmd = Queue(io.cmd)
  val funct = cmd.bits.inst.funct
  val addr = cmd.bits.rs2(log2Up(outer.n) - 1, 0)
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
  val value_read = regfile(cmd.bits.rs1)
  val value_resp = value_read
  // core_state
  val core_state      = RegInit(0.U(8.W))
  val core_state_next = RegInit(0.U(8.W))

  def IDLE = UInt(0)
  def WRITE = UInt(1)
  def MEMWAIT = UInt(2)
  def VLOAD = UInt(3)
  def VSTORE = UInt(4)
  def MOVE = UInt(5)

  //def IDLE      = UInt(0)
  def WAIT = UInt(1)
  def WORK = UInt(2)
  // --------------------------- _function ---------------------------
  def addr2node(addr: UInt) = Mux((addr >> 28.U) > 0.U,addr(19,16),10.U)
  def input2wf(src: UInt,des: UInt) = (addr2node(src) === CN.InputA.U || addr2node(src) === CN.InputB.U) && (addr2node(des) >= CN.FeatureA.U && addr2node(des) <= CN.WeightB.U)
  // --------------------------- _val def ---------------------------
  // mem register
  def mem_temp_cnt_max   = C.W_MOVE/xLen
  val memory_addr        = Wire(UInt(width = 64))
  val rocc_des_w         = Wire(UInt(width = C.W_ADDR))
  val rocc_src_w         = Wire(UInt(width = C.W_ADDR))
  val rocc_addr          = RegInit(0.U(32.W))
  val memory_op_length   = RegInit(0.U(32.W))
  val mem_op_cnt_max     = memory_op_length << log2Up(C.W_DATA/xLen) // length * 256 / 64 === length * 4
  val mem_req_cnt        = RegInit(0.U(32.W))
  val mem_resp_cnt       = RegInit(0.U(32.W))
  val mem_temp_data      = RegInit(0.U(C.W_MOVE.W))
  val mem_temp_data_next = Wire(UInt(width = C.W_MOVE))
  val mem_temp_cnt       = RegInit(0.U(3.W))
  val ready_mem_op       = Wire(Bool())
  val during_mem_op      = Wire(Bool())
  // move cmd
  val push_into_move1     = RegInit(true.B)
  val move_cmd_parse      = Module(new MoveParse)
  val move_cmd_rd         = Wire(UInt(width = 30))
  val move_cmd_rn         = Wire(UInt(width = 30))
  // data move 1
  val move1_queue = Queue(move_cmd_parse.io.cmd_1,C.DEPTH_MOVE_CMD)
  val move1_state = RegInit(0.U(8.W))
  val move1_rd = RegInit(0.U(C.W_ADDR.W))
  val move1_rn = RegInit(0.U(C.W_ADDR.W))
  val move1_mask2 = RegInit(0.U(48.W))
  val move1_rc = RegInit(0.U(16.W))
  val move1_ready = Wire(Bool())
  // data move 2
  val move2_queue = Queue(move_cmd_parse.io.cmd_2,C.DEPTH_MOVE_CMD)
  val move2_state = RegInit(0.U(8.W))
  val move2_rd = RegInit(0.U(C.W_ADDR.W))
  val move2_rn = RegInit(0.U(C.W_ADDR.W))
  val move2_mask2 = RegInit(0.U(48.W))
  val move2_rc = RegInit(0.U(16.W))
  val move2_ready = Wire(Bool())
  // data move 3
  val move3_queue = Queue(move_cmd_parse.io.cmd_3,C.DEPTH_MOVE_CMD)
  val move3_state = RegInit(0.U(8.W))
  val move3_rd = RegInit(0.U(C.W_ADDR.W))
  val move3_rn = RegInit(0.U(C.W_ADDR.W))
  val move3_input2wf_w = Wire(Bool())
  val move3_input2wf_r = RegInit(false.B)
  val move3_ready = Wire(Bool())

  //array

  //regfile def

  // constant
  val core_temp_rs1 = RegInit(0.U(xLen.W))
  val core_temp_rs2 = RegInit(0.U(xLen.W))

  //--------------------------- _mem interface---------------------------
  def addr_delta = 8.U // 8 bytes
  val mem_cmd       = RegInit(M_XRD)
  val mem_req_data  = 0.U // for store
  val mem_req_valid = during_mem_op && mem_req_cnt < mem_op_cnt_max
  val mem_req_addr  = memory_addr + addr_delta * mem_req_cnt
  val mem_req_tag   = mem_req_cnt(3,0)
  val mem_req_size  = log2Ceil(8).U
  // --------------------------- _interconnect ---------------------------
  // --------------------------- BUFFER TAG
  for (i <- 0 until C.N_NODE){
    //read out
    tag_queue(i).io.o.ready := tags(i) === CN.NaN.U

    when(tag_queue(i).io.fire){
      tags(i) := tag_queue(i).io.o.bits
    }
    //write in
    when(cmd.fire()){
      when(doLoad){
        tag_queue(i).io.i.bits := CN.Core.U
        tag_queue(i).io.i.valid := i.U === addr2node(rocc_des_w)
      }
      when(doMove){
        when(move3_input2wf_w){
          tag_queue(i).io.i.bits := CN.Move_3.U
        }.otherwise{
          tag_queue(i).io.i.bits := Mux(push_into_move1,CN.Move_1.U,CN.Move_2.U)
        }
        tag_queue(i).io.i.valid := i.U === addr2node(move_cmd_rd) || i.U === addr2node(move_cmd_rn) 
      }    
    }.otherwise{
      tag_queue(i).io.i.valid := false.B
    }
  }
  // --------------------------- W_NODE MUX
  for (i <- 0 until C.N_NODE){
    when(core_state === MEMWAIT && ready_mem_op && i.U === addr2node(rocc_addr)){
      w_nodes_mux(i) := CN.Memory.U
    }
    when(move1_state === WAIT && move1_ready && i.U === addr2node(move1_rd)){
      w_nodes_mux(i) := addr2node(move1_rn)
    }
    when(move2_state === WAIT && move2_ready && i.U === addr2node(move2_rd)){
      w_nodes_mux(i) := addr2node(move2_rn)
    }
    w_nodes(i).io.mux := w_nodes_mux(i)
  }

  //move3

  for(i <- 2 until C.N_MOVE3){
    when(move3_state === WAIT && move3_ready && i.U === addr2node(move3_rd)){
      w_nodes_mv3_mux(i) := addr2node(move3_rn)
    }
    w_nodes_mv3(i).io.mux := w_nodes_mv3_mux(i)
  }


  // --------------------------- NODE CONNECTION
  r_nodes(11).io.i.bits.data  := mem_temp_data_next
  r_nodes(11).io.i.valid := mem_temp_cnt === (mem_temp_cnt_max - 1).U && io.mem.resp.valid
  
  for (i <- 0 until C.N_BUFFER){
    w_done(i) := buffer_seq(i).io.w_ctrl.done
    r_done(i) := buffer_seq(i).io.r_ctrl.done
    r_nodes(i).io.i <> buffer_seq(i).io.r // buffer read port -> read node input port
    buffer_seq(i).io.w <> w_nodes(i).io.o // write node output port -> buffer write port
  }
  r_nodes_mv3(0).io.i <> buffer_seq(0).io.mv3_r
  r_nodes_mv3(1).io.i <> buffer_seq(1).io.mv3_r
  buffer_seq(2).io.mv3_w <> w_nodes_mv3(2).io.o
  buffer_seq(3).io.mv3_w <> w_nodes_mv3(3).io.o
  buffer_seq(4).io.mv3_w <> w_nodes_mv3(4).io.o
  buffer_seq(5).io.mv3_w <> w_nodes_mv3(5).io.o
  // between nodes
  for(i <- 0 until C.N_BUFFER){ // memory -> all buffers
    w_nodes(i).io.i(0) <> r_nodes(11).io.o(i)
  }

  w_nodes(0).io.i(1) <> r_nodes(8).io.o(0)  // Output A -> Input A
  w_nodes(0).io.i(2) <> r_nodes(9).io.o(1)  // Output B -> Input A

  w_nodes(1).io.i(1) <> r_nodes(8).io.o(0)  // Output A -> Input B
  w_nodes(1).io.i(2) <> r_nodes(9).io.o(1)  // Output B -> Input B

  w_nodes(8).io.i(1) <> r_nodes(6).io.o(0)  // Middle A -> Output A
  w_nodes(8).io.i(2) <> r_nodes(7).io.o(1)  // Middle B -> Output A

  w_nodes(9).io.i(1) <> r_nodes(6).io.o(0)  // Middle A -> Output B
  w_nodes(9).io.i(2) <> r_nodes(7).io.o(1)  // Middle B -> Output B
  // mv3
  w_nodes_mv3(2).io.i(0)<> r_nodes_mv3(0).io.o(0)
  w_nodes_mv3(2).io.i(1)<> r_nodes_mv3(1).io.o(0)
  w_nodes_mv3(3).io.i(0)<> r_nodes_mv3(0).io.o(1)
  w_nodes_mv3(3).io.i(1)<> r_nodes_mv3(1).io.o(1)

  w_nodes_mv3(4).io.i(0)<> r_nodes_mv3(0).io.o(2)
  w_nodes_mv3(4).io.i(1)<> r_nodes_mv3(1).io.o(2)
  w_nodes_mv3(5).io.i(0)<> r_nodes_mv3(0).io.o(3)
  w_nodes_mv3(5).io.i(1)<> r_nodes_mv3(1).io.o(3)

  //array
  feature_read_node.io.i(0) <> buffer_seq(2).io.array_r
  feature_read_node.io.i(1) <> buffer_seq(3).io.array_r
  feature_read_node.io.mux  := wf_pingpong

  weight_read_node.io.i(0)  <> buffer_seq(4).io.array_r
  weight_read_node.io.i(1)  <> buffer_seq(5).io.array_r
  weight_read_node.io.mux   := wf_pingpong
  // gemm input
  val gemm_iv = Wire(Vec(C.N_ARRAY,Bool()))
  val gemm_i_valid = gemm_iv.reduce(_||_)
  gemm.io.in_valid := gemm_i_valid
  for (i <- 0 until C.N_ARRAY){
    feature_read_node.io.o(i).ready := true.B
    gemm.io.in_a(i) := feature_read_node.io.o(i).bits
    gemm.io.in_b(i) := weight_read_node.io.o(i).bits
    gemm_iv(i) := feature_read_node.io.o(i).valid && weight_read_node.io.o(i).valid

    // gemm.io.in_valid(i) := feature_read_node.io.o(i).valid && weight_read_node.io.o(i).valid
  }
  gemm_do_input := r_en(2) || r_en(3) || r_en(4) || r_en(5)//last input not done
  // gemm output
  // for(i <- 0 until C.N_ARRAY){
  //   buffer_seq(6).io.array_w(i) := gemm.io.out_c(i)
  //   buffer_seq(7).io.array_w(i) := gemm.io.out_c(i)
  // }
  // buffer_seq(6).io.array_wv := ~middle_pingpong && gemm.io.out_valid
  // buffer_seq(7).io.array_wv :=  middle_pingpong && gemm.io.out_valid
  for(i <- 0 until C.N_ARRAY){
    buffer_seq(6).io.array_w(i) := feature_read_node.io.o(i).bits
    buffer_seq(7).io.array_w(i) := feature_read_node.io.o(i).bits
  }
  buffer_seq(6).io.array_wv := ~middle_pingpong && gemm_i_valid
  buffer_seq(7).io.array_wv :=  middle_pingpong && gemm_i_valid
  // --------------------------- BUFFER CTRL
  for (i <- 0 until C.N_BUFFER){
    when(tags(i) === CN.Core.U){
      buffer_seq(i).io.w_ctrl.addr_begin := rocc_addr
      buffer_seq(i).io.w_ctrl.length := memory_op_length
      buffer_seq(i).io.w_ctrl.en := w_en(i)
    }
    when(tags(i) === CN.Move_1.U){
      when(i.U === addr2node(move1_rd)){//write
        buffer_seq(i).io.w_ctrl.addr_begin := move1_rd
        buffer_seq(i).io.w_ctrl.length := move1_rc
        buffer_seq(i).io.w_ctrl.en := w_en(i)
      }
      when(i.U === addr2node(move1_rn)){//read
        buffer_seq(i).io.r_ctrl.addr_begin := move1_rn
        buffer_seq(i).io.r_ctrl.length := move1_rc
        buffer_seq(i).io.r_ctrl.en := r_en(i)
      }
    }
    when(tags(i) === CN.Move_2.U){
      when(i.U === addr2node(move2_rd)){//write
        buffer_seq(i).io.w_ctrl.addr_begin := move2_rd
        buffer_seq(i).io.w_ctrl.length := move2_rc
        buffer_seq(i).io.w_ctrl.en := w_en(i)
      }
      when(i.U === addr2node(move2_rn)){//read
        buffer_seq(i).io.r_ctrl.addr_begin := move2_rn
        buffer_seq(i).io.r_ctrl.length := move2_rc
        buffer_seq(i).io.r_ctrl.en := r_en(i)
      }
    }
    when(tags(i) === CN.Move_3.U){
      when(i.U === addr2node(move3_rd)){//write
        buffer_seq(i).io.w_ctrl.addr_begin := move3_rd
        buffer_seq(i).io.w_ctrl.length := (CS.Feature/C.W_DATA).U
        buffer_seq(i).io.w_ctrl.en := w_en(i)
      }
      when(i.U === addr2node(move3_rn)){//read
        buffer_seq(i).io.r_ctrl.addr_begin := move3_rn
        buffer_seq(i).io.r_ctrl.length := (CS.Feature/C.W_DATA).U
        buffer_seq(i).io.r_ctrl.en := r_en(i)
      }
    }
  }

  // --------------------------- LOAD/STORE
  memory_addr   := core_temp_rs2(63,16)
  mem_temp_data_next := Cat(io.mem.resp.bits.data, mem_temp_data(C.W_MOVE - 1, xLen))
  rocc_des_w   := cmd.bits.rs1(29, 0)
  rocc_src_w   := cmd.bits.rs1(59,30)
  ready_mem_op  := tags(addr2node(rocc_addr)) === CN.Core.U
  during_mem_op := core_state === VLOAD || core_state === VSTORE
  
  // --------------------------- MOVE CMD PARSE
  // for cmd queue
  move_cmd_parse.io.valid := core_state === MEMWAIT && core_state_next === MOVE
  move_cmd_parse.io.mux := push_into_move1
  move_cmd_parse.io.do_move3 := move3_input2wf_r
  move_cmd_parse.io.rs1 := core_temp_rs1
  move_cmd_parse.io.rs2 := core_temp_rs2
  // for tag queue
  move_cmd_rd := cmd.bits.rs1(59,30)
  move_cmd_rn := cmd.bits.rs1(29, 0)

  move3_input2wf_w := input2wf(move_cmd_rn,move_cmd_rd)
  move1_ready := tags(addr2node(move1_rd)) === CN.Move_1.U && tags(addr2node(move1_rn)) === CN.Move_1.U
  move2_ready := tags(addr2node(move2_rd)) === CN.Move_2.U && tags(addr2node(move2_rn)) === CN.Move_2.U
  move3_ready := tags(addr2node(move3_rd)) === CN.Move_3.U && tags(addr2node(move3_rn)) === CN.Move_3.U
  //--------------------------- _core ---------------------------
  move1_queue.ready := move1_state === IDLE
  when(cmd.fire()) {
    core_temp_rs1 := cmd.bits.rs1
    core_temp_rs2 := cmd.bits.rs2
    when(doWrite) {
      core_state := WRITE
    }.elsewhen(doMove) {
      core_state := MEMWAIT
      core_state_next := MOVE
      move3_input2wf_r := move3_input2wf_w
    }.elsewhen(doLoad) {
      rocc_addr        := rocc_des_w
      memory_op_length := cmd.bits.rs2(15,0)
      core_state := MEMWAIT
      core_state_next := VLOAD
      mem_cmd := M_XRD
    }.elsewhen(doStore){
      core_state := MEMWAIT
      core_state_next := VSTORE
      mem_cmd := M_XWR
    }

  }
  // exec
  when(core_state === WRITE && !busy_regfile(core_temp_rs1)) {
    regfile(core_temp_rs1) := core_temp_rs2
    core_state := IDLE
  }

  when(core_state === MEMWAIT) {
    when(core_state_next === VLOAD && ready_mem_op){
      w_en(addr2node(rocc_addr)) := true.B
      mem_req_cnt  := 0.U
      mem_resp_cnt := 0.U
      mem_temp_cnt := 0.U
      core_state   := core_state_next
    }
    when(core_state_next === MOVE && move_cmd_parse.io.ready){
      core_state   := core_state_next
    }
  }

  when(core_state === MOVE) {
    push_into_move1 := ~push_into_move1
    core_state := IDLE
  }

  when(core_state === VLOAD){
    when(io.mem.req.fire()){
      mem_req_cnt := mem_req_cnt + 1.U
    }
    when(io.mem.resp.valid){
      mem_temp_data := mem_temp_data_next
      mem_temp_cnt := mem_temp_cnt+ 1.U
      mem_resp_cnt := mem_resp_cnt + 1.U
    }
    when(w_done(addr2node(rocc_addr))){
      core_state := IDLE
      tags(addr2node(rocc_addr)) := CN.NaN.U
      w_en(addr2node(rocc_addr)) := false.B
    }
  }

  //--------------------------- _move1 ---------------------------
  when(move1_queue.fire()) {
    move1_state := WAIT
    move1_rd    := move1_queue.bits.rd
    move1_rn    := move1_queue.bits.rn
    move1_mask2 := move1_queue.bits.mask2
    move1_rc    := move1_queue.bits.rc
  }
  when(move1_state === WAIT && move1_ready){
    move1_state := WORK
    w_en(addr2node(move1_rd)) := true.B
    r_en(addr2node(move1_rn)) := true.B
  }

  when(move1_state === WORK){
    when(w_done(addr2node(move1_rd))){
      move1_state := IDLE
      w_en(addr2node(move1_rd)) := false.B
      r_en(addr2node(move1_rn)) := false.B
      tags(addr2node(move1_rd)) := CN.NaN.U
      tags(addr2node(move1_rn)) := CN.NaN.U
    }
  }

  //--------------------------- _move2 ---------------------------
  move2_queue.ready := move2_state === IDLE
  when(move2_queue.fire()) {
    move2_state := WAIT
    move2_rd    := move2_queue.bits.rd
    move2_rn    := move2_queue.bits.rn
    move2_mask2 := move2_queue.bits.mask2
    move2_rc    := move2_queue.bits.rc
  }
  when(move2_state === WAIT && move2_ready){
    move2_state := WORK
    w_en(addr2node(move2_rd)) := true.B
    r_en(addr2node(move2_rn)) := true.B
  }

  when(move2_state === WORK){
    when(w_done(addr2node(move2_rd))){
      move2_state := IDLE
      w_en(addr2node(move2_rd)) := false.B
      r_en(addr2node(move2_rn)) := false.B
      tags(addr2node(move2_rd)) := CN.NaN.U
      tags(addr2node(move2_rn)) := CN.NaN.U
    }
  }
  //--------------------------- _move3 ---------------------------
  move3_queue.ready := move3_state === IDLE
  for(i <- 0 until C.N_BUFFER){
    when(move3_state === WORK){
      when((i >= CN.InputA).B && (i <= CN.InputB).B && i.U === addr2node(move3_rn)){
        buffer_seq(i).io.move3 := r_en(i)
      }.elsewhen((i >= CN.FeatureA).B && (i <= CN.WeightB).B && i.U === addr2node(move3_rd)){
        buffer_seq(i).io.move3 := w_en(i)
      }.otherwise{
        buffer_seq(i).io.move3 := false.B
      }
    }.otherwise{
      buffer_seq(i).io.move3 := false.B
    }
  }

  when(move3_queue.fire()) {
    move3_state := WAIT
    move3_rd    := move3_queue.bits.rd
    move3_rn    := move3_queue.bits.rn
  }
  when(move3_state === WAIT && move3_ready){
    move3_state := WORK

    w_en(addr2node(move3_rd)) := true.B
    r_en(addr2node(move3_rn)) := true.B
  }

  when(move3_state === WORK){
    when(w_done(addr2node(move3_rd))){
      move3_state := IDLE
      tags(addr2node(move3_rd)) := CN.NaN.U
      tags(addr2node(move3_rn)) := CN.NaN.U

      w_en(addr2node(move3_rd)) := false.B
      r_en(addr2node(move3_rn)) := false.B
    }
  }
  //--------------------------- _array ---------------------------
  // input
  for(i <- 2 until 4){
    when(w_done(i)){
      array_feature_ready := true.B;
    }
  }
  for(i <- 4 until 6){
    when(w_done(i)){
      array_weight_ready := true.B;
    }
  }
  when(array_feature_ready && array_weight_ready && !gemm_do_input){

    array_feature_ready := false.B
    array_weight_ready := false.B
    when(wf_pingpong === 0.U){
      r_en(2) := true.B
      r_en(4) := true.B
    }
    when(wf_pingpong === 1.U){
      r_en(3) := true.B
      r_en(5) := true.B
    }
  }
  when(r_done(2)&&r_done(4) || r_done(3)&&r_done(5)){
    wf_pingpong := wf_pingpong + 1.U

    for(i <- 2 until 6){
      r_en(i) := false.B
    }

  }
  //output
  when(!gemm.io.out_valid && RegNext(gemm.io.out_valid)){//negedge
    middle_pingpong := !middle_pingpong
  }

  // --------------------------- rocc resp ---------------------------
  val doResp = cmd.bits.inst.xd
  val stallReg = false.B
  val stallLoad = false.B
  val stallResp = doResp && !io.resp.ready

  // cmd.ready := !stallReg && core_state === IDLE && !stallResp
  cmd.ready := core_state === IDLE
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
  //
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

