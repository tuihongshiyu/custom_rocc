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

case object BuildRoCC extends Field[Seq[Parameters => LazyRoCC]](Nil)

class RoCCInstruction extends Bundle {
  val funct = Bits(width = 7)
  val rs2 = Bits(width = 5)
  val rs1 = Bits(width = 5)
  val xd = Bool()
  val xs1 = Bool()
  val xs2 = Bool()
  val rd = Bits(width = 5)
  val opcode = Bits(width = 7)
}

class RoCCCommand(implicit p: Parameters) extends CoreBundle()(p) {
  val inst = new RoCCInstruction
  val rs1 = Bits(width = xLen)
  val rs2 = Bits(width = xLen)
  val status = new MStatus
}

class RoCCResponse(implicit p: Parameters) extends CoreBundle()(p) {
  val rd = Bits(width = 5)
  val data = Bits(width = xLen)
}

class RoCCCoreIO(implicit p: Parameters) extends CoreBundle()(p) {
  val cmd = Decoupled(new RoCCCommand).flip
  val resp = Decoupled(new RoCCResponse)
  val mem = new HellaCacheIO
  val busy = Bool(OUTPUT)
  val interrupt = Bool(OUTPUT)
  val exception = Bool(INPUT)
}

class RoCCIO(val nPTWPorts: Int)(implicit p: Parameters) extends RoCCCoreIO()(p) {
  val ptw = Vec(nPTWPorts, new TLBPTWIO)
  val fpu_req = Decoupled(new FPInput)
  val fpu_resp = Decoupled(new FPResult).flip
}

/** Base classes for Diplomatic TL2 RoCC units **/
abstract class LazyRoCC(
      val opcodes: OpcodeSet,
      val nPTWPorts: Int = 0,
      val usesFPU: Boolean = false
    )(implicit p: Parameters) extends LazyModule {
  val module: LazyRoCCModuleImp
  val atlNode: TLNode = TLIdentityNode()
  val tlNode: TLNode = TLIdentityNode()
}

class LazyRoCCModuleImp(outer: LazyRoCC) extends LazyModuleImp(outer) {
  val io = IO(new RoCCIO(outer.nPTWPorts))
}

/** Mixins for including RoCC **/

trait HasLazyRoCC extends CanHavePTW { this: BaseTile =>
  val roccs = p(BuildRoCC).map(_(p))

  roccs.map(_.atlNode).foreach { atl => tlMasterXbar.node :=* atl }
  roccs.map(_.tlNode).foreach { tl => tlOtherMastersNode :=* tl }

  nPTWPorts += roccs.map(_.nPTWPorts).foldLeft(0)(_ + _)
  nDCachePorts += roccs.size
}

trait HasLazyRoCCModule extends CanHavePTWModule
    with HasCoreParameters { this: RocketTileModuleImp with HasFpuOpt =>

  val (respArb, cmdRouter) = if(outer.roccs.size > 0) {
    val respArb = Module(new RRArbiter(new RoCCResponse()(outer.p), outer.roccs.size))
    val cmdRouter = Module(new RoccCommandRouter(outer.roccs.map(_.opcodes))(outer.p))
    outer.roccs.zipWithIndex.foreach { case (rocc, i) =>
      rocc.module.io.ptw ++=: ptwPorts
      rocc.module.io.cmd <> cmdRouter.io.out(i)
      val dcIF = Module(new SimpleHellaCacheIF()(outer.p))
      dcIF.io.requestor <> rocc.module.io.mem
      dcachePorts += dcIF.io.cache
      respArb.io.in(i) <> Queue(rocc.module.io.resp)
    }

    fpuOpt foreach { fpu =>
      val nFPUPorts = outer.roccs.filter(_.usesFPU).size
      if (usingFPU && nFPUPorts > 0) {
        val fpArb = Module(new InOrderArbiter(new FPInput()(outer.p), new FPResult()(outer.p), nFPUPorts))
        val fp_rocc_ios = outer.roccs.filter(_.usesFPU).map(_.module.io)
        fpArb.io.in_req <> fp_rocc_ios.map(_.fpu_req)
        fp_rocc_ios.zip(fpArb.io.in_resp).foreach {
          case (rocc, arb) => rocc.fpu_resp <> arb
        }
        fpu.io.cp_req <> fpArb.io.out_req
        fpArb.io.out_resp <> fpu.io.cp_resp
      } else {
        fpu.io.cp_req.valid := Bool(false)
        fpu.io.cp_resp.ready := Bool(false)
      }
    }
    (Some(respArb), Some(cmdRouter))
  } else {
    (None, None)
  }
}

class AccumulatorExample(opcodes: OpcodeSet, val n: Int = 4)(implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new AccumulatorExampleModuleImp(this)
}

class AccumulatorExampleModuleImp(outer: AccumulatorExample)(implicit p: Parameters) extends LazyRoCCModuleImp(outer)
    with HasCoreParameters {
  val regfile = Mem(outer.n, UInt(width = xLen))
  val busy = Reg(init = Vec.fill(outer.n){Bool(false)})

  val cmd = Queue(io.cmd)
  val funct = cmd.bits.inst.funct
  val addr = cmd.bits.rs2(log2Up(outer.n)-1,0)
  val doWrite = funct === UInt(0)
  val doRead = funct === UInt(1)
  val doLoad = funct === UInt(2)
  val doAccum = funct === UInt(3)
  val memRespTag = io.mem.resp.bits.tag(log2Up(outer.n)-1,0)

  // datapath
  val addend = cmd.bits.rs1
  val accum = regfile(addr)
  val wdata = Mux(doWrite, addend, accum + addend)

  when (cmd.fire() && (doWrite || doAccum)) {
    regfile(addr) := wdata
  }

  when (io.mem.resp.valid) {
    regfile(memRespTag) := io.mem.resp.bits.data
    busy(memRespTag) := Bool(false)
  }

  // control
  when (io.mem.req.fire()) {
    busy(addr) := Bool(true)
  }

  val doResp = cmd.bits.inst.xd
  val stallReg = busy(addr)
  val stallLoad = doLoad && !io.mem.req.ready
  val stallResp = doResp && !io.resp.ready

  cmd.ready := !stallReg && !stallLoad && !stallResp
    // command resolved if no stalls AND not issuing a load that will need a request

  // PROC RESPONSE INTERFACE
  io.resp.valid := cmd.valid && doResp && !stallReg && !stallLoad
    // valid response if valid command, need a response, and no stalls
  io.resp.bits.rd := cmd.bits.inst.rd
    // Must respond with the appropriate tag or undefined behavior
  io.resp.bits.data := accum
    // Semantics is to always send out prior accumulator register value

  io.busy := cmd.valid || busy.reduce(_||_)
    // Be busy when have pending memory requests or committed possibility of pending requests
  io.interrupt := Bool(false)
    // Set this true to trigger an interrupt on the processor (please refer to supervisor documentation)

  // MEMORY REQUEST INTERFACE
  io.mem.req.valid := cmd.valid && doLoad && !stallReg && !stallResp
  io.mem.req.bits.addr := addend
  io.mem.req.bits.tag := addr
  io.mem.req.bits.cmd := M_XRD // perform a load (M_XWR for stores)
  io.mem.req.bits.size := log2Ceil(8).U
  io.mem.req.bits.signed := Bool(false)
  io.mem.req.bits.data := Bits(0) // we're not performing any stores...
  io.mem.req.bits.phys := Bool(false)
  io.mem.req.bits.dprv := cmd.bits.status.dprv
}

class VecotrRocc(opcodes: OpcodeSet, val n: Int = 16)(implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new VecotrRoccModuleImp(this)
}

@chiselName
class VecotrRoccModuleImp(outer: VecotrRocc)(implicit p: Parameters) extends LazyRoCCModuleImp(outer)
    with HasCoreParameters {
  val regfile = Mem(outer.n, UInt(width = xLen))
  val busy = Reg(init = Vec.fill(outer.n){Bool(false)})

  val cmd = Queue(io.cmd)
  val funct = cmd.bits.inst.funct
  val addr = cmd.bits.rs2(log2Up(outer.n)-1,0)
  // i r r rs1:target reg rs2:value
  val doWrite = funct === UInt(0)
  // r r i rs1:source reg
  val doRead = funct === UInt(1)
  // i r r rs1:target reg rs2:source addr
  val doLoad = funct === UInt(2)
  // i r r rs1:source reg rs2:target addr
  val doStore = funct === UInt(3)
  // i r r rs1:source reg rs2:target addr
  val doAdd = funct === UInt(4)
  // i r r rs1:source reg rs2:target addr
  val doMul = funct === UInt(5)
  // val doAccum = funct === UInt(3)
  val memRespTag = io.mem.resp.bits.tag(log2Up(outer.n)-1,0)
  // -------------------------- new --------------------------------
  val reg_write = cmd.bits.rs1(log2Up(outer.n)-1,0)
  val value_read = regfile(cmd.bits.rs1)
  val value_write = cmd.bits.rs2
  val value_resp = value_read
  //
  def VIDLE      = UInt(0);
  def VLOAD      = UInt(1); // int load
  def VSTORE     = UInt(2); // int store
  def VADD       = UInt(3);
  def VMUL       = UInt(4);
  val state = RegInit(0.U(8.W))
  // val req_bits = Wire(UInt(width = 8))
  //
  val need_to_load = state === VLOAD || state === VADD || state === VMUL
  val v_length = regfile(0)(log2Up(outer.n)-1,0)
  val addr_delta = 8.U
  val reg_addr_width = log2Up(outer.n)
  val reg_tar_addr = RegInit(0.U(reg_addr_width.W))
  val mem_req_addr = RegInit(0.U(xLen.W))
  val mem_req_cnt = RegInit(0.U(16.W))
  val mem_resp_addr = io.mem.resp.bits.tag(log2Up(outer.n)-1,0)
  val mem_resp_cnt = RegInit(0.U(reg_addr_width.W))
  val load_req_valid = need_to_load&& !busy(reg_tar_addr) && mem_req_cnt < v_length 
  val store_req_valid = state === VSTORE && mem_req_cnt < v_length 
  val load_done = mem_resp_cnt >= v_length
  val store_done = mem_req_cnt >= v_length && io.mem.req.ready
  val mem_cmd = RegInit(M_XRD)
  var mem_req_valid = Mux(need_to_load,load_req_valid,store_req_valid)
  val mem_req_data = regfile(reg_tar_addr)// for store
  chisel3.dontTouch(load_req_valid)
  chisel3.dontTouch(store_req_valid)
  // write
  when(cmd.fire() && doWrite){
    regfile(reg_write) := value_write
  }
  //state machine
  when(cmd.fire()){
    reg_tar_addr := cmd.bits.rs1(log2Up(outer.n)-1,0)
    mem_req_addr := cmd.bits.rs2
    mem_req_cnt := 0.U
    mem_resp_cnt := 0.U
    when(doLoad){
      state := VLOAD
      mem_cmd := M_XRD
    }.elsewhen(doStore){
      state := VSTORE
      mem_cmd := M_XWR
    }.elsewhen(doAdd){
      state := VADD
      mem_cmd := M_XRD
    }.elsewhen(doMul){
      state := VMUL
      mem_cmd := M_XRD
    }
    .otherwise{
      state := VIDLE
    }
  }
  // exec
  when(state === VLOAD){
    when(io.mem.req.fire()){
      reg_tar_addr := reg_tar_addr + 1.U
      mem_req_addr := mem_req_addr + addr_delta
      mem_req_cnt := mem_req_cnt + 1.U
      busy(reg_tar_addr) := Bool(true)
    }
    when(load_done){
      state := VIDLE
    }
    when(io.mem.resp.valid){
      mem_resp_cnt := mem_resp_cnt + 1.U;
      regfile(memRespTag) := io.mem.resp.bits.data
      busy(memRespTag) := Bool(false)
    }
  }
  when(state === VSTORE){
    when(io.mem.req.fire()){
      reg_tar_addr := reg_tar_addr + 1.U
      mem_req_addr := mem_req_addr + addr_delta
      mem_req_cnt := mem_req_cnt + 1.U
    }
    when(store_done){
      state := VIDLE
    }
  }
  when(state === VADD){
    when(io.mem.req.fire()){
      reg_tar_addr := reg_tar_addr + 1.U
      mem_req_addr := mem_req_addr + addr_delta
      mem_req_cnt := mem_req_cnt + 1.U
      busy(reg_tar_addr) := Bool(true)
    }
    when(load_done){
      state := VIDLE
    }
    when(io.mem.resp.valid){
      mem_resp_cnt := mem_resp_cnt + 1.U;
      regfile(memRespTag) := regfile(memRespTag) + io.mem.resp.bits.data
      busy(memRespTag) := Bool(false)
    }
  }
  when(state === VMUL){
    when(io.mem.req.fire()){
      reg_tar_addr := reg_tar_addr + 1.U
      mem_req_addr := mem_req_addr + addr_delta
      mem_req_cnt := mem_req_cnt + 1.U
      busy(reg_tar_addr) := Bool(true)
    }
    when(load_done){
      state := VIDLE
    }
    when(io.mem.resp.valid){
      mem_resp_cnt := mem_resp_cnt + 1.U;
      regfile(memRespTag) := regfile(memRespTag) * io.mem.resp.bits.data
      busy(memRespTag) := Bool(false)
    }
  }
  // 

  //Store
  val addend = mem_req_addr
  // ---------------------------------------------------------------

  val doResp = cmd.bits.inst.xd
  val stallReg = busy(reg_tar_addr)
  val stallLoad = (doLoad || state === VLOAD) && !io.mem.req.ready
  val stallResp = doResp && !io.resp.ready

  cmd.ready := !stallReg && state === VIDLE && !stallResp
    // command resolved if no stalls AND not issuing a load that will need a request

  // PROC RESPONSE INTERFACE
  io.resp.valid := cmd.valid && doResp && !stallReg && !stallLoad
    // valid response if valid command, need a response, and no stalls
  io.resp.bits.rd := cmd.bits.inst.rd
    // Must respond with the appropriate tag or undefined behavior
  // io.resp.bits.data := accum
  io.resp.bits.data := value_resp
    // Semantics is to always send out prior accumulator register value

  // io.busy := cmd.valid || busy.reduce(_||_)
  io.busy := cmd.valid || busy.reduce(_||_)
    // Be busy when have pending memory requests or committed possibility of pending requests
  io.interrupt := Bool(false)
    // Set this true to trigger an interrupt on the processor (please refer to supervisor documentation)

  // MEMORY REQUEST INTERFACE
  // io.mem.req.valid := cmd.valid && doLoad && !stallReg && !stallResp
  io.mem.req.valid := mem_req_valid
  io.mem.req.bits.addr := addend
  io.mem.req.bits.tag := reg_tar_addr

  // io.mem.req.bits.cmd := M_XRD // perform a load (M_XWR for stores)
  io.mem.req.bits.cmd := mem_cmd // perform a load (M_XWR for stores)
  io.mem.req.bits.size := log2Ceil(8).U
  io.mem.req.bits.signed := Bool(false)
  io.mem.req.bits.data := mem_req_data
  io.mem.req.bits.phys := Bool(false)
  io.mem.req.bits.dprv := cmd.bits.status.dprv
}

class  TranslatorExample(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes, nPTWPorts = 1) {
  override lazy val module = new TranslatorExampleModuleImp(this)
}

class TranslatorExampleModuleImp(outer: TranslatorExample)(implicit p: Parameters) extends LazyRoCCModuleImp(outer)
    with HasCoreParameters {
  val req_addr = Reg(UInt(width = coreMaxAddrBits))
  val req_rd = Reg(io.resp.bits.rd)
  val req_offset = req_addr(pgIdxBits - 1, 0)
  val req_vpn = req_addr(coreMaxAddrBits - 1, pgIdxBits)
  val pte = Reg(new PTE)

  val s_idle :: s_ptw_req :: s_ptw_resp :: s_resp :: Nil = Enum(Bits(), 4)
  val state = Reg(init = s_idle)

  io.cmd.ready := (state === s_idle)

  when (io.cmd.fire()) {
    req_rd := io.cmd.bits.inst.rd
    req_addr := io.cmd.bits.rs1
    state := s_ptw_req
  }

  private val ptw = io.ptw(0)

  when (ptw.req.fire()) { state := s_ptw_resp }

  when (state === s_ptw_resp && ptw.resp.valid) {
    pte := ptw.resp.bits.pte
    state := s_resp
  }

  when (io.resp.fire()) { state := s_idle }

  ptw.req.valid := (state === s_ptw_req)
  ptw.req.bits.valid := true.B
  ptw.req.bits.bits.addr := req_vpn

  io.resp.valid := (state === s_resp)
  io.resp.bits.rd := req_rd
  io.resp.bits.data := Mux(pte.leaf(), Cat(pte.ppn, req_offset), SInt(-1, xLen).asUInt)

  io.busy := (state =/= s_idle)
  io.interrupt := Bool(false)
  io.mem.req.valid := Bool(false)
}

class  CharacterCountExample(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new CharacterCountExampleModuleImp(this)
  override val atlNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1("CharacterCountRoCC")))))
}

class CharacterCountExampleModuleImp(outer: CharacterCountExample)(implicit p: Parameters) extends LazyRoCCModuleImp(outer)
  with HasCoreParameters
  with HasL1CacheParameters {
  val cacheParams = tileParams.icache.get

  private val blockOffset = blockOffBits
  private val beatOffset = log2Up(cacheDataBits/8)

  val needle = Reg(UInt(width = 8))
  val addr = Reg(UInt(width = coreMaxAddrBits))
  val count = Reg(UInt(width = xLen))
  val resp_rd = Reg(io.resp.bits.rd)

  val addr_block = addr(coreMaxAddrBits - 1, blockOffset)
  val offset = addr(blockOffset - 1, 0)
  val next_addr = (addr_block + UInt(1)) << UInt(blockOffset)

  val s_idle :: s_acq :: s_gnt :: s_check :: s_resp :: Nil = Enum(Bits(), 5)
  val state = Reg(init = s_idle)

  val (tl_out, edgesOut) = outer.atlNode.out(0)
  val gnt = tl_out.d.bits
  val recv_data = Reg(UInt(width = cacheDataBits))
  val recv_beat = Reg(UInt(width = log2Up(cacheDataBeats+1)), init = UInt(0))

  val data_bytes = Vec.tabulate(cacheDataBits/8) { i => recv_data(8 * (i + 1) - 1, 8 * i) }
  val zero_match = data_bytes.map(_ === UInt(0))
  val needle_match = data_bytes.map(_ === needle)
  val first_zero = PriorityEncoder(zero_match)

  val chars_found = PopCount(needle_match.zipWithIndex.map {
    case (matches, i) =>
      val idx = Cat(recv_beat - UInt(1), UInt(i, beatOffset))
      matches && idx >= offset && UInt(i) <= first_zero
  })
  val zero_found = zero_match.reduce(_ || _)
  val finished = Reg(Bool())

  io.cmd.ready := (state === s_idle)
  io.resp.valid := (state === s_resp)
  io.resp.bits.rd := resp_rd
  io.resp.bits.data := count
  tl_out.a.valid := (state === s_acq)
  tl_out.a.bits := edgesOut.Get(
                       fromSource = UInt(0),
                       toAddress = addr_block << blockOffset,
                       lgSize = UInt(lgCacheBlockBytes))._2
  tl_out.d.ready := (state === s_gnt)

  when (io.cmd.fire()) {
    addr := io.cmd.bits.rs1
    needle := io.cmd.bits.rs2
    resp_rd := io.cmd.bits.inst.rd
    count := UInt(0)
    finished := Bool(false)
    state := s_acq
  }

  when (tl_out.a.fire()) { state := s_gnt }

  when (tl_out.d.fire()) {
    recv_beat := recv_beat + UInt(1)
    recv_data := gnt.data
    state := s_check
  }

  when (state === s_check) {
    when (!finished) {
      count := count + chars_found
    }
    when (zero_found) { finished := Bool(true) }
    when (recv_beat === UInt(cacheDataBeats)) {
      addr := next_addr
      state := Mux(zero_found || finished, s_resp, s_acq)
    } .otherwise {
      state := s_gnt
    }
  }

  when (io.resp.fire()) { state := s_idle }

  io.busy := (state =/= s_idle)
  io.interrupt := Bool(false)
  io.mem.req.valid := Bool(false)
  // Tie off unused channels
  tl_out.b.ready := Bool(true)
  tl_out.c.valid := Bool(false)
  tl_out.e.valid := Bool(false)
}

class BlackBoxExample(opcodes: OpcodeSet, blackBoxFile: String)(implicit p: Parameters)
    extends LazyRoCC(opcodes) {
  override lazy val module = new BlackBoxExampleModuleImp(this, blackBoxFile)
}

class BlackBoxExampleModuleImp(outer: BlackBoxExample, blackBoxFile: String)(implicit p: Parameters)
    extends LazyRoCCModuleImp(outer)
    with HasCoreParameters {

  val blackbox = {
    val roccIo = io
    Module(
      new BlackBox( Map( "xLen" -> IntParam(xLen),
                         "PRV_SZ" -> IntParam(PRV.SZ),
                         "coreMaxAddrBits" -> IntParam(coreMaxAddrBits),
                         "dcacheReqTagBits" -> IntParam(roccIo.mem.req.bits.tag.getWidth),
                         "M_SZ" -> IntParam(M_SZ),
                         "mem_req_bits_size_width" -> IntParam(roccIo.mem.req.bits.size.getWidth),
                         "coreDataBits" -> IntParam(coreDataBits),
                         "coreDataBytes" -> IntParam(coreDataBytes),
                         "paddrBits" -> IntParam(paddrBits),
                         "FPConstants_RM_SZ" -> IntParam(FPConstants.RM_SZ),
                         "fLen" -> IntParam(fLen),
                         "FPConstants_FLAGS_SZ" -> IntParam(FPConstants.FLAGS_SZ)
                   ) ) with HasBlackBoxResource {
        val io = IO( new Bundle {
                      val clock = Input(Clock())
                      val reset = Input(Bool())
                      val rocc = roccIo.cloneType
                    })
        override def desiredName: String = blackBoxFile
        addResource(s"/vsrc/$blackBoxFile.v")
      }
    )
  }

  blackbox.io.clock := clock
  blackbox.io.reset := reset
  blackbox.io.rocc.cmd <> io.cmd
  io.resp <> blackbox.io.rocc.resp
  io.mem <> blackbox.io.rocc.mem
  io.busy := blackbox.io.rocc.busy
  io.interrupt := blackbox.io.rocc.interrupt
  blackbox.io.rocc.exception := io.exception
  io.ptw <> blackbox.io.rocc.ptw
  io.fpu_req <> blackbox.io.rocc.fpu_req
  blackbox.io.rocc.fpu_resp <> io.fpu_resp

}

class OpcodeSet(val opcodes: Seq[UInt]) {
  def |(set: OpcodeSet) =
    new OpcodeSet(this.opcodes ++ set.opcodes)

  def matches(oc: UInt) = opcodes.map(_ === oc).reduce(_ || _)
}

object OpcodeSet {
  def custom0 = new OpcodeSet(Seq(Bits("b0001011")))
  def custom1 = new OpcodeSet(Seq(Bits("b0101011")))
  def custom2 = new OpcodeSet(Seq(Bits("b1011011")))
  def custom3 = new OpcodeSet(Seq(Bits("b1111011")))
  def all = custom0 | custom1 | custom2 | custom3
}

class RoccCommandRouter(opcodes: Seq[OpcodeSet])(implicit p: Parameters)
    extends CoreModule()(p) {
  val io = new Bundle {
    val in = Decoupled(new RoCCCommand).flip
    val out = Vec(opcodes.size, Decoupled(new RoCCCommand))
    val busy = Bool(OUTPUT)
  }

  val cmd = Queue(io.in)
  val cmdReadys = io.out.zip(opcodes).map { case (out, opcode) =>
    val me = opcode.matches(cmd.bits.inst.opcode)
    out.valid := cmd.valid && me
    out.bits := cmd.bits
    out.ready && me
  }
  cmd.ready := cmdReadys.reduce(_ || _)
  io.busy := cmd.valid

  assert(PopCount(cmdReadys) <= UInt(1),
    "Custom opcode matched for more than one accelerator")
}
