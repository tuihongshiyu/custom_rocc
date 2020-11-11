module PE(
  input         clock,
  input         reset,
  input  [7:0]  io_in_a,
  input  [18:0] io_in_b,
  input  [18:0] io_in_d,
  output [7:0]  io_out_a,
  output [18:0] io_out_b,
  output [18:0] io_out_c,
  input         io_in_control_propagate,
  input         io_in_control_dataflow,
  output        io_out_control_propagate,
  output        io_out_control_dataflow,
  input         io_in_valid,
  output        io_out_valid
);
`ifdef RANDOMIZE_REG_INIT
  reg [31:0] _RAND_0;
  reg [31:0] _RAND_1;
`endif // RANDOMIZE_REG_INIT
  reg [31:0] c1; // @[mysystolicmatrix.scala 53:17]
  reg [31:0] c2; // @[mysystolicmatrix.scala 54:17]
  wire  _T = ~io_in_control_dataflow; // @[mysystolicmatrix.scala 73:20]
  wire [18:0] _GEN_18 = {{11'd0}, io_in_a}; // @[mysystolicmatrix.scala 77:22]
  wire [26:0] _T_2 = _GEN_18 * io_in_b; // @[mysystolicmatrix.scala 77:22]
  wire [31:0] _GEN_19 = {{5'd0}, _T_2}; // @[mysystolicmatrix.scala 77:18]
  wire [31:0] _T_4 = c2 + _GEN_19; // @[mysystolicmatrix.scala 77:18]
  wire [31:0] _T_7 = c1 + _GEN_19; // @[mysystolicmatrix.scala 82:18]
  wire [31:0] _GEN_1 = io_in_control_propagate ? c1 : c2; // @[mysystolicmatrix.scala 74:36]
  wire [31:0] _GEN_22 = {{24'd0}, io_in_a}; // @[mysystolicmatrix.scala 89:27]
  wire [39:0] _T_10 = _GEN_22 * c2; // @[mysystolicmatrix.scala 89:27]
  wire [39:0] _GEN_23 = {{21'd0}, io_in_b}; // @[mysystolicmatrix.scala 89:23]
  wire [39:0] _T_12 = _GEN_23 + _T_10; // @[mysystolicmatrix.scala 89:23]
  wire [39:0] _T_13 = _GEN_22 * c1; // @[mysystolicmatrix.scala 93:27]
  wire [39:0] _T_15 = _GEN_23 + _T_13; // @[mysystolicmatrix.scala 93:23]
  wire [39:0] _GEN_6 = io_in_control_propagate ? _T_12 : _T_15; // @[mysystolicmatrix.scala 86:36]
  wire  _T_18 = ~reset; // @[mysystolicmatrix.scala 96:13]
  wire [39:0] _GEN_12 = _T ? {{21'd0}, io_in_b} : _GEN_6; // @[mysystolicmatrix.scala 73:42]
  wire [31:0] _GEN_13 = _T ? _GEN_1 : _GEN_1; // @[mysystolicmatrix.scala 73:42]
  wire  _T_19 = ~io_in_valid; // @[mysystolicmatrix.scala 101:10]
  wire  _GEN_26 = ~_T; // @[mysystolicmatrix.scala 96:13]
  wire  _GEN_28 = _GEN_26 & _T; // @[mysystolicmatrix.scala 96:13]
  assign io_out_a = io_in_a; // @[mysystolicmatrix.scala 59:14]
  assign io_out_b = _GEN_12[18:0]; // @[mysystolicmatrix.scala 75:18 mysystolicmatrix.scala 80:18 mysystolicmatrix.scala 89:18 mysystolicmatrix.scala 93:18]
  assign io_out_c = _GEN_13[18:0]; // @[mysystolicmatrix.scala 76:18 mysystolicmatrix.scala 81:18 mysystolicmatrix.scala 87:18 mysystolicmatrix.scala 91:18]
  assign io_out_control_propagate = io_in_control_propagate; // @[mysystolicmatrix.scala 61:30]
  assign io_out_control_dataflow = io_in_control_dataflow; // @[mysystolicmatrix.scala 60:29]
  assign io_out_valid = io_in_valid; // @[mysystolicmatrix.scala 62:18]
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE_MEM_INIT
  integer initvar;
`endif
`ifndef SYNTHESIS
`ifdef FIRRTL_BEFORE_INITIAL
`FIRRTL_BEFORE_INITIAL
`endif
initial begin
  `ifdef RANDOMIZE
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      `ifdef RANDOMIZE_DELAY
        #`RANDOMIZE_DELAY begin end
      `else
        #0.002 begin end
      `endif
    `endif
`ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  c1 = _RAND_0[31:0];
  _RAND_1 = {1{`RANDOM}};
  c2 = _RAND_1[31:0];
`endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`ifdef FIRRTL_AFTER_INITIAL
`FIRRTL_AFTER_INITIAL
`endif
`endif // SYNTHESIS
  always @(posedge clock) begin
    if (!(_T_19)) begin
      if (_T) begin
        if (io_in_control_propagate) begin
          c1 <= {{13'd0}, io_in_d};
        end else begin
          c1 <= _T_7;
        end
      end else if (io_in_control_dataflow) begin
        if (io_in_control_propagate) begin
          c1 <= {{13'd0}, io_in_d};
        end
      end
    end
    if (!(_T_19)) begin
      if (_T) begin
        if (io_in_control_propagate) begin
          c2 <= _T_4;
        end else begin
          c2 <= {{13'd0}, io_in_d};
        end
      end else if (io_in_control_dataflow) begin
        if (!(io_in_control_propagate)) begin
          c2 <= {{13'd0}, io_in_d};
        end
      end
    end
    `ifndef SYNTHESIS
    `ifdef PRINTF_COND
      if (`PRINTF_COND) begin
    `endif
        if (_GEN_28 & _T_18) begin
          $fwrite(32'h80000002,"Assertion failed: unknown dataflow\n    at mysystolicmatrix.scala:96 assert(false.B,\"unknown dataflow\")\n"); // @[mysystolicmatrix.scala 96:13]
        end
    `ifdef PRINTF_COND
      end
    `endif
    `endif // SYNTHESIS
    `ifndef SYNTHESIS
    `ifdef STOP_COND
      if (`STOP_COND) begin
    `endif
        if (_GEN_28 & _T_18) begin
          $fatal; // @[mysystolicmatrix.scala 96:13]
        end
    `ifdef STOP_COND
      end
    `endif
    `endif // SYNTHESIS
  end
endmodule
module Tile(
  input         clock,
  input         reset,
  input  [7:0]  io_in_a_0,
  input  [18:0] io_in_b_0,
  input  [18:0] io_in_d_0,
  output [7:0]  io_out_a_0,
  output [18:0] io_out_b_0,
  output [18:0] io_out_c_0,
  input         io_in_control_0_propagate,
  input         io_in_control_0_dataflow,
  output        io_out_control_0_propagate,
  output        io_out_control_0_dataflow,
  input         io_in_valid_0,
  output        io_out_valid_0
);
  wire  tile_0_0_clock; // @[mysystolicmatrix.scala 124:44]
  wire  tile_0_0_reset; // @[mysystolicmatrix.scala 124:44]
  wire [7:0] tile_0_0_io_in_a; // @[mysystolicmatrix.scala 124:44]
  wire [18:0] tile_0_0_io_in_b; // @[mysystolicmatrix.scala 124:44]
  wire [18:0] tile_0_0_io_in_d; // @[mysystolicmatrix.scala 124:44]
  wire [7:0] tile_0_0_io_out_a; // @[mysystolicmatrix.scala 124:44]
  wire [18:0] tile_0_0_io_out_b; // @[mysystolicmatrix.scala 124:44]
  wire [18:0] tile_0_0_io_out_c; // @[mysystolicmatrix.scala 124:44]
  wire  tile_0_0_io_in_control_propagate; // @[mysystolicmatrix.scala 124:44]
  wire  tile_0_0_io_in_control_dataflow; // @[mysystolicmatrix.scala 124:44]
  wire  tile_0_0_io_out_control_propagate; // @[mysystolicmatrix.scala 124:44]
  wire  tile_0_0_io_out_control_dataflow; // @[mysystolicmatrix.scala 124:44]
  wire  tile_0_0_io_in_valid; // @[mysystolicmatrix.scala 124:44]
  wire  tile_0_0_io_out_valid; // @[mysystolicmatrix.scala 124:44]
  PE tile_0_0 ( // @[mysystolicmatrix.scala 124:44]
    .clock(tile_0_0_clock),
    .reset(tile_0_0_reset),
    .io_in_a(tile_0_0_io_in_a),
    .io_in_b(tile_0_0_io_in_b),
    .io_in_d(tile_0_0_io_in_d),
    .io_out_a(tile_0_0_io_out_a),
    .io_out_b(tile_0_0_io_out_b),
    .io_out_c(tile_0_0_io_out_c),
    .io_in_control_propagate(tile_0_0_io_in_control_propagate),
    .io_in_control_dataflow(tile_0_0_io_in_control_dataflow),
    .io_out_control_propagate(tile_0_0_io_out_control_propagate),
    .io_out_control_dataflow(tile_0_0_io_out_control_dataflow),
    .io_in_valid(tile_0_0_io_in_valid),
    .io_out_valid(tile_0_0_io_out_valid)
  );
  assign io_out_a_0 = tile_0_0_io_out_a; // @[mysystolicmatrix.scala 173:17]
  assign io_out_b_0 = tile_0_0_io_out_b; // @[mysystolicmatrix.scala 177:17]
  assign io_out_c_0 = tile_0_0_io_out_c; // @[mysystolicmatrix.scala 178:17]
  assign io_out_control_0_propagate = tile_0_0_io_out_control_propagate; // @[mysystolicmatrix.scala 179:23]
  assign io_out_control_0_dataflow = tile_0_0_io_out_control_dataflow; // @[mysystolicmatrix.scala 179:23]
  assign io_out_valid_0 = tile_0_0_io_out_valid; // @[mysystolicmatrix.scala 180:21]
  assign tile_0_0_clock = clock;
  assign tile_0_0_reset = reset;
  assign tile_0_0_io_in_a = io_in_a_0; // @[mysystolicmatrix.scala 130:20]
  assign tile_0_0_io_in_b = io_in_b_0; // @[mysystolicmatrix.scala 139:20]
  assign tile_0_0_io_in_d = io_in_d_0; // @[mysystolicmatrix.scala 148:20]
  assign tile_0_0_io_in_control_propagate = io_in_control_0_propagate; // @[mysystolicmatrix.scala 157:26]
  assign tile_0_0_io_in_control_dataflow = io_in_control_0_dataflow; // @[mysystolicmatrix.scala 157:26]
  assign tile_0_0_io_in_valid = io_in_valid_0; // @[mysystolicmatrix.scala 166:24]
endmodule
module Mesh(
  input         clock,
  input         reset,
  input  [7:0]  io_in_a_0_0,
  input  [7:0]  io_in_a_1_0,
  input  [7:0]  io_in_b_0_0,
  input  [7:0]  io_in_b_1_0,
  input  [7:0]  io_in_d_0_0,
  input  [7:0]  io_in_d_1_0,
  output [18:0] io_out_b_0_0,
  output [18:0] io_out_b_1_0,
  output [18:0] io_out_c_0_0,
  output [18:0] io_out_c_1_0,
  input         io_in_control_0_0_propagate,
  input         io_in_control_0_0_dataflow,
  input         io_in_control_1_0_propagate,
  input         io_in_control_1_0_dataflow,
  input         io_in_valid_0_0,
  input         io_in_valid_1_0,
  output        io_out_valid_0_0,
  output        io_out_valid_1_0
);
`ifdef RANDOMIZE_REG_INIT
  reg [31:0] _RAND_0;
  reg [31:0] _RAND_1;
  reg [31:0] _RAND_2;
  reg [31:0] _RAND_3;
  reg [31:0] _RAND_4;
  reg [31:0] _RAND_5;
  reg [31:0] _RAND_6;
  reg [31:0] _RAND_7;
  reg [31:0] _RAND_8;
  reg [31:0] _RAND_9;
  reg [31:0] _RAND_10;
  reg [31:0] _RAND_11;
  reg [31:0] _RAND_12;
  reg [31:0] _RAND_13;
  reg [31:0] _RAND_14;
  reg [31:0] _RAND_15;
  reg [31:0] _RAND_16;
  reg [31:0] _RAND_17;
  reg [31:0] _RAND_18;
  reg [31:0] _RAND_19;
  reg [31:0] _RAND_20;
  reg [31:0] _RAND_21;
  reg [31:0] _RAND_22;
  reg [31:0] _RAND_23;
  reg [31:0] _RAND_24;
  reg [31:0] _RAND_25;
  reg [31:0] _RAND_26;
  reg [31:0] _RAND_27;
  reg [31:0] _RAND_28;
  reg [31:0] _RAND_29;
`endif // RANDOMIZE_REG_INIT
  wire  mesh_0_0_clock; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_0_reset; // @[mysystolicmatrix.scala 201:52]
  wire [7:0] mesh_0_0_io_in_a_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_0_0_io_in_b_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_0_0_io_in_d_0; // @[mysystolicmatrix.scala 201:52]
  wire [7:0] mesh_0_0_io_out_a_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_0_0_io_out_b_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_0_0_io_out_c_0; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_0_io_in_control_0_propagate; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_0_io_in_control_0_dataflow; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_0_io_out_control_0_propagate; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_0_io_out_control_0_dataflow; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_0_io_in_valid_0; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_0_io_out_valid_0; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_1_clock; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_1_reset; // @[mysystolicmatrix.scala 201:52]
  wire [7:0] mesh_0_1_io_in_a_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_0_1_io_in_b_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_0_1_io_in_d_0; // @[mysystolicmatrix.scala 201:52]
  wire [7:0] mesh_0_1_io_out_a_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_0_1_io_out_b_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_0_1_io_out_c_0; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_1_io_in_control_0_propagate; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_1_io_in_control_0_dataflow; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_1_io_out_control_0_propagate; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_1_io_out_control_0_dataflow; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_1_io_in_valid_0; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_0_1_io_out_valid_0; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_0_clock; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_0_reset; // @[mysystolicmatrix.scala 201:52]
  wire [7:0] mesh_1_0_io_in_a_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_1_0_io_in_b_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_1_0_io_in_d_0; // @[mysystolicmatrix.scala 201:52]
  wire [7:0] mesh_1_0_io_out_a_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_1_0_io_out_b_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_1_0_io_out_c_0; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_0_io_in_control_0_propagate; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_0_io_in_control_0_dataflow; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_0_io_out_control_0_propagate; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_0_io_out_control_0_dataflow; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_0_io_in_valid_0; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_0_io_out_valid_0; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_1_clock; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_1_reset; // @[mysystolicmatrix.scala 201:52]
  wire [7:0] mesh_1_1_io_in_a_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_1_1_io_in_b_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_1_1_io_in_d_0; // @[mysystolicmatrix.scala 201:52]
  wire [7:0] mesh_1_1_io_out_a_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_1_1_io_out_b_0; // @[mysystolicmatrix.scala 201:52]
  wire [18:0] mesh_1_1_io_out_c_0; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_1_io_in_control_0_propagate; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_1_io_in_control_0_dataflow; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_1_io_out_control_0_propagate; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_1_io_out_control_0_dataflow; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_1_io_in_valid_0; // @[mysystolicmatrix.scala 201:52]
  wire  mesh_1_1_io_out_valid_0; // @[mysystolicmatrix.scala 201:52]
  reg [7:0] _T_0; // @[mysystolicmatrix.scala 207:32]
  reg [7:0] _T_1_0; // @[mysystolicmatrix.scala 207:32]
  reg [7:0] _T_2_0; // @[mysystolicmatrix.scala 207:32]
  reg [7:0] _T_3_0; // @[mysystolicmatrix.scala 207:32]
  reg [7:0] _T_4_0; // @[Reg.scala 15:16]
  reg [18:0] _T_5_0; // @[Reg.scala 15:16]
  reg [7:0] _T_6_0; // @[Reg.scala 15:16]
  reg [18:0] _T_7_0; // @[Reg.scala 15:16]
  reg [7:0] _T_8_0; // @[Reg.scala 15:16]
  reg [18:0] _T_9_0; // @[Reg.scala 15:16]
  reg [7:0] _T_10_0; // @[Reg.scala 15:16]
  reg [18:0] _T_11_0; // @[Reg.scala 15:16]
  reg  _T_12; // @[Reg.scala 15:16]
  reg  _T_13; // @[Reg.scala 15:16]
  reg  _T_14; // @[Reg.scala 15:16]
  reg  _T_15; // @[Reg.scala 15:16]
  reg  _T_16; // @[Reg.scala 15:16]
  reg  _T_17; // @[Reg.scala 15:16]
  reg  _T_18; // @[Reg.scala 15:16]
  reg  _T_19; // @[Reg.scala 15:16]
  reg  _T_20_0; // @[mysystolicmatrix.scala 247:36]
  reg  _T_21_0; // @[mysystolicmatrix.scala 247:36]
  reg  _T_22_0; // @[mysystolicmatrix.scala 247:36]
  reg  _T_23_0; // @[mysystolicmatrix.scala 247:36]
  reg [18:0] _T_24_0; // @[mysystolicmatrix.scala 252:17]
  reg [18:0] _T_25_0; // @[mysystolicmatrix.scala 253:17]
  reg  _T_26_0; // @[mysystolicmatrix.scala 254:17]
  reg [18:0] _T_27_0; // @[mysystolicmatrix.scala 252:17]
  reg [18:0] _T_28_0; // @[mysystolicmatrix.scala 253:17]
  reg  _T_29_0; // @[mysystolicmatrix.scala 254:17]
  Tile mesh_0_0 ( // @[mysystolicmatrix.scala 201:52]
    .clock(mesh_0_0_clock),
    .reset(mesh_0_0_reset),
    .io_in_a_0(mesh_0_0_io_in_a_0),
    .io_in_b_0(mesh_0_0_io_in_b_0),
    .io_in_d_0(mesh_0_0_io_in_d_0),
    .io_out_a_0(mesh_0_0_io_out_a_0),
    .io_out_b_0(mesh_0_0_io_out_b_0),
    .io_out_c_0(mesh_0_0_io_out_c_0),
    .io_in_control_0_propagate(mesh_0_0_io_in_control_0_propagate),
    .io_in_control_0_dataflow(mesh_0_0_io_in_control_0_dataflow),
    .io_out_control_0_propagate(mesh_0_0_io_out_control_0_propagate),
    .io_out_control_0_dataflow(mesh_0_0_io_out_control_0_dataflow),
    .io_in_valid_0(mesh_0_0_io_in_valid_0),
    .io_out_valid_0(mesh_0_0_io_out_valid_0)
  );
  Tile mesh_0_1 ( // @[mysystolicmatrix.scala 201:52]
    .clock(mesh_0_1_clock),
    .reset(mesh_0_1_reset),
    .io_in_a_0(mesh_0_1_io_in_a_0),
    .io_in_b_0(mesh_0_1_io_in_b_0),
    .io_in_d_0(mesh_0_1_io_in_d_0),
    .io_out_a_0(mesh_0_1_io_out_a_0),
    .io_out_b_0(mesh_0_1_io_out_b_0),
    .io_out_c_0(mesh_0_1_io_out_c_0),
    .io_in_control_0_propagate(mesh_0_1_io_in_control_0_propagate),
    .io_in_control_0_dataflow(mesh_0_1_io_in_control_0_dataflow),
    .io_out_control_0_propagate(mesh_0_1_io_out_control_0_propagate),
    .io_out_control_0_dataflow(mesh_0_1_io_out_control_0_dataflow),
    .io_in_valid_0(mesh_0_1_io_in_valid_0),
    .io_out_valid_0(mesh_0_1_io_out_valid_0)
  );
  Tile mesh_1_0 ( // @[mysystolicmatrix.scala 201:52]
    .clock(mesh_1_0_clock),
    .reset(mesh_1_0_reset),
    .io_in_a_0(mesh_1_0_io_in_a_0),
    .io_in_b_0(mesh_1_0_io_in_b_0),
    .io_in_d_0(mesh_1_0_io_in_d_0),
    .io_out_a_0(mesh_1_0_io_out_a_0),
    .io_out_b_0(mesh_1_0_io_out_b_0),
    .io_out_c_0(mesh_1_0_io_out_c_0),
    .io_in_control_0_propagate(mesh_1_0_io_in_control_0_propagate),
    .io_in_control_0_dataflow(mesh_1_0_io_in_control_0_dataflow),
    .io_out_control_0_propagate(mesh_1_0_io_out_control_0_propagate),
    .io_out_control_0_dataflow(mesh_1_0_io_out_control_0_dataflow),
    .io_in_valid_0(mesh_1_0_io_in_valid_0),
    .io_out_valid_0(mesh_1_0_io_out_valid_0)
  );
  Tile mesh_1_1 ( // @[mysystolicmatrix.scala 201:52]
    .clock(mesh_1_1_clock),
    .reset(mesh_1_1_reset),
    .io_in_a_0(mesh_1_1_io_in_a_0),
    .io_in_b_0(mesh_1_1_io_in_b_0),
    .io_in_d_0(mesh_1_1_io_in_d_0),
    .io_out_a_0(mesh_1_1_io_out_a_0),
    .io_out_b_0(mesh_1_1_io_out_b_0),
    .io_out_c_0(mesh_1_1_io_out_c_0),
    .io_in_control_0_propagate(mesh_1_1_io_in_control_0_propagate),
    .io_in_control_0_dataflow(mesh_1_1_io_in_control_0_dataflow),
    .io_out_control_0_propagate(mesh_1_1_io_out_control_0_propagate),
    .io_out_control_0_dataflow(mesh_1_1_io_out_control_0_dataflow),
    .io_in_valid_0(mesh_1_1_io_in_valid_0),
    .io_out_valid_0(mesh_1_1_io_out_valid_0)
  );
  assign io_out_b_0_0 = _T_24_0; // @[mysystolicmatrix.scala 252:7]
  assign io_out_b_1_0 = _T_27_0; // @[mysystolicmatrix.scala 252:7]
  assign io_out_c_0_0 = _T_25_0; // @[mysystolicmatrix.scala 253:7]
  assign io_out_c_1_0 = _T_28_0; // @[mysystolicmatrix.scala 253:7]
  assign io_out_valid_0_0 = _T_26_0; // @[mysystolicmatrix.scala 254:7]
  assign io_out_valid_1_0 = _T_29_0; // @[mysystolicmatrix.scala 254:7]
  assign mesh_0_0_clock = clock;
  assign mesh_0_0_reset = reset;
  assign mesh_0_0_io_in_a_0 = _T_0; // @[mysystolicmatrix.scala 207:22]
  assign mesh_0_0_io_in_b_0 = {{11'd0}, _T_4_0}; // @[mysystolicmatrix.scala 216:22]
  assign mesh_0_0_io_in_d_0 = {{11'd0}, _T_8_0}; // @[mysystolicmatrix.scala 225:22]
  assign mesh_0_0_io_in_control_0_propagate = _T_13; // @[mysystolicmatrix.scala 237:33]
  assign mesh_0_0_io_in_control_0_dataflow = _T_12; // @[mysystolicmatrix.scala 236:32]
  assign mesh_0_0_io_in_valid_0 = _T_20_0; // @[mysystolicmatrix.scala 247:26]
  assign mesh_0_1_clock = clock;
  assign mesh_0_1_reset = reset;
  assign mesh_0_1_io_in_a_0 = _T_1_0; // @[mysystolicmatrix.scala 207:22]
  assign mesh_0_1_io_in_b_0 = {{11'd0}, _T_6_0}; // @[mysystolicmatrix.scala 216:22]
  assign mesh_0_1_io_in_d_0 = {{11'd0}, _T_10_0}; // @[mysystolicmatrix.scala 225:22]
  assign mesh_0_1_io_in_control_0_propagate = _T_17; // @[mysystolicmatrix.scala 237:33]
  assign mesh_0_1_io_in_control_0_dataflow = _T_16; // @[mysystolicmatrix.scala 236:32]
  assign mesh_0_1_io_in_valid_0 = _T_22_0; // @[mysystolicmatrix.scala 247:26]
  assign mesh_1_0_clock = clock;
  assign mesh_1_0_reset = reset;
  assign mesh_1_0_io_in_a_0 = _T_2_0; // @[mysystolicmatrix.scala 207:22]
  assign mesh_1_0_io_in_b_0 = _T_5_0; // @[mysystolicmatrix.scala 216:22]
  assign mesh_1_0_io_in_d_0 = _T_9_0; // @[mysystolicmatrix.scala 225:22]
  assign mesh_1_0_io_in_control_0_propagate = _T_15; // @[mysystolicmatrix.scala 237:33]
  assign mesh_1_0_io_in_control_0_dataflow = _T_14; // @[mysystolicmatrix.scala 236:32]
  assign mesh_1_0_io_in_valid_0 = _T_21_0; // @[mysystolicmatrix.scala 247:26]
  assign mesh_1_1_clock = clock;
  assign mesh_1_1_reset = reset;
  assign mesh_1_1_io_in_a_0 = _T_3_0; // @[mysystolicmatrix.scala 207:22]
  assign mesh_1_1_io_in_b_0 = _T_7_0; // @[mysystolicmatrix.scala 216:22]
  assign mesh_1_1_io_in_d_0 = _T_11_0; // @[mysystolicmatrix.scala 225:22]
  assign mesh_1_1_io_in_control_0_propagate = _T_19; // @[mysystolicmatrix.scala 237:33]
  assign mesh_1_1_io_in_control_0_dataflow = _T_18; // @[mysystolicmatrix.scala 236:32]
  assign mesh_1_1_io_in_valid_0 = _T_23_0; // @[mysystolicmatrix.scala 247:26]
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE_MEM_INIT
  integer initvar;
`endif
`ifndef SYNTHESIS
`ifdef FIRRTL_BEFORE_INITIAL
`FIRRTL_BEFORE_INITIAL
`endif
initial begin
  `ifdef RANDOMIZE
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      `ifdef RANDOMIZE_DELAY
        #`RANDOMIZE_DELAY begin end
      `else
        #0.002 begin end
      `endif
    `endif
`ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  _T_0 = _RAND_0[7:0];
  _RAND_1 = {1{`RANDOM}};
  _T_1_0 = _RAND_1[7:0];
  _RAND_2 = {1{`RANDOM}};
  _T_2_0 = _RAND_2[7:0];
  _RAND_3 = {1{`RANDOM}};
  _T_3_0 = _RAND_3[7:0];
  _RAND_4 = {1{`RANDOM}};
  _T_4_0 = _RAND_4[7:0];
  _RAND_5 = {1{`RANDOM}};
  _T_5_0 = _RAND_5[18:0];
  _RAND_6 = {1{`RANDOM}};
  _T_6_0 = _RAND_6[7:0];
  _RAND_7 = {1{`RANDOM}};
  _T_7_0 = _RAND_7[18:0];
  _RAND_8 = {1{`RANDOM}};
  _T_8_0 = _RAND_8[7:0];
  _RAND_9 = {1{`RANDOM}};
  _T_9_0 = _RAND_9[18:0];
  _RAND_10 = {1{`RANDOM}};
  _T_10_0 = _RAND_10[7:0];
  _RAND_11 = {1{`RANDOM}};
  _T_11_0 = _RAND_11[18:0];
  _RAND_12 = {1{`RANDOM}};
  _T_12 = _RAND_12[0:0];
  _RAND_13 = {1{`RANDOM}};
  _T_13 = _RAND_13[0:0];
  _RAND_14 = {1{`RANDOM}};
  _T_14 = _RAND_14[0:0];
  _RAND_15 = {1{`RANDOM}};
  _T_15 = _RAND_15[0:0];
  _RAND_16 = {1{`RANDOM}};
  _T_16 = _RAND_16[0:0];
  _RAND_17 = {1{`RANDOM}};
  _T_17 = _RAND_17[0:0];
  _RAND_18 = {1{`RANDOM}};
  _T_18 = _RAND_18[0:0];
  _RAND_19 = {1{`RANDOM}};
  _T_19 = _RAND_19[0:0];
  _RAND_20 = {1{`RANDOM}};
  _T_20_0 = _RAND_20[0:0];
  _RAND_21 = {1{`RANDOM}};
  _T_21_0 = _RAND_21[0:0];
  _RAND_22 = {1{`RANDOM}};
  _T_22_0 = _RAND_22[0:0];
  _RAND_23 = {1{`RANDOM}};
  _T_23_0 = _RAND_23[0:0];
  _RAND_24 = {1{`RANDOM}};
  _T_24_0 = _RAND_24[18:0];
  _RAND_25 = {1{`RANDOM}};
  _T_25_0 = _RAND_25[18:0];
  _RAND_26 = {1{`RANDOM}};
  _T_26_0 = _RAND_26[0:0];
  _RAND_27 = {1{`RANDOM}};
  _T_27_0 = _RAND_27[18:0];
  _RAND_28 = {1{`RANDOM}};
  _T_28_0 = _RAND_28[18:0];
  _RAND_29 = {1{`RANDOM}};
  _T_29_0 = _RAND_29[0:0];
`endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`ifdef FIRRTL_AFTER_INITIAL
`FIRRTL_AFTER_INITIAL
`endif
`endif // SYNTHESIS
  always @(posedge clock) begin
    _T_0 <= io_in_a_0_0;
    _T_1_0 <= mesh_0_0_io_out_a_0;
    _T_2_0 <= io_in_a_1_0;
    _T_3_0 <= mesh_1_0_io_out_a_0;
    if (io_in_valid_0_0) begin
      _T_4_0 <= io_in_b_0_0;
    end
    if (mesh_0_0_io_out_valid_0) begin
      _T_5_0 <= mesh_0_0_io_out_b_0;
    end
    if (io_in_valid_1_0) begin
      _T_6_0 <= io_in_b_1_0;
    end
    if (mesh_0_1_io_out_valid_0) begin
      _T_7_0 <= mesh_0_1_io_out_b_0;
    end
    if (io_in_valid_0_0) begin
      _T_8_0 <= io_in_d_0_0;
    end
    if (mesh_0_0_io_out_valid_0) begin
      _T_9_0 <= mesh_0_0_io_out_c_0;
    end
    if (io_in_valid_1_0) begin
      _T_10_0 <= io_in_d_1_0;
    end
    if (mesh_0_1_io_out_valid_0) begin
      _T_11_0 <= mesh_0_1_io_out_c_0;
    end
    if (io_in_valid_0_0) begin
      _T_12 <= io_in_control_0_0_dataflow;
    end
    if (io_in_valid_0_0) begin
      _T_13 <= io_in_control_0_0_propagate;
    end
    if (mesh_0_0_io_out_valid_0) begin
      _T_14 <= mesh_0_0_io_out_control_0_dataflow;
    end
    if (mesh_0_0_io_out_valid_0) begin
      _T_15 <= mesh_0_0_io_out_control_0_propagate;
    end
    if (io_in_valid_1_0) begin
      _T_16 <= io_in_control_1_0_dataflow;
    end
    if (io_in_valid_1_0) begin
      _T_17 <= io_in_control_1_0_propagate;
    end
    if (mesh_0_1_io_out_valid_0) begin
      _T_18 <= mesh_0_1_io_out_control_0_dataflow;
    end
    if (mesh_0_1_io_out_valid_0) begin
      _T_19 <= mesh_0_1_io_out_control_0_propagate;
    end
    _T_20_0 <= io_in_valid_0_0;
    _T_21_0 <= mesh_0_0_io_out_valid_0;
    _T_22_0 <= io_in_valid_1_0;
    _T_23_0 <= mesh_0_1_io_out_valid_0;
    _T_24_0 <= mesh_1_0_io_out_b_0;
    _T_25_0 <= mesh_1_0_io_out_c_0;
    _T_26_0 <= mesh_1_0_io_out_valid_0;
    _T_27_0 <= mesh_1_1_io_out_b_0;
    _T_28_0 <= mesh_1_1_io_out_c_0;
    _T_29_0 <= mesh_1_1_io_out_valid_0;
  end
endmodule
