package freechips.rocketchip.tile

object BufferConstant{
    def BLOCK_WIDTH = 4096
    def DATA_WIDTH = 4096
    def ADDR_WIDTH = 32
    def IOBUFFER_BLOCK_N = 8
}


object RoccAddr{
    def ADDR_REGFILE  = 0x00000000 // 0x0 - 0x100
    def ADDR_IOBUFFER = 0x00010000 // 0x1_0000 - 0x1_8000
    def ADDR_FBUFFER  = 0x00020000 // 0x2_0000 - 0x2_8000
    def ADDR_WBUFFER  = 0x00030000 // 0x3_0000 - 0x3_8000
    def ADDR_MBUFFER  = 0x00040000 // 0x4_0000 - 0x4_8000
}

object MeshParameter {
    def df: Bool = 1.B
    def pe_latency: Int = 0
    def tileRows: Int = 16
    def tileColumns: Int  = 16                           
    def meshRows: Int = 1
    def meshColumns: Int   = 1                         
}