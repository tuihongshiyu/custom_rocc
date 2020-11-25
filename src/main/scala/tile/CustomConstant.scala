package freechips.rocketchip.tile

object BufferConstant{
    def BLOCK_WIDTH = 4096
    def DATA_WIDTH = 4096
    def ADDR_WIDTH = 32
    def BLOCK_N_BUFFER_IO = 8
    def BLOCK_N_BUFFER_F = 8
    def BLOCK_N_BUFFER_M = 8

}

object MatC{
    def MAX_N = 8
    def WIDTH = 64
}

object RoccAddr{
    def ADDR_REGFILE  = 0x00000000 // 0x0 - 0x100
    def ADDR_IOBUFFER = 0x00010000 // 0x1_0000 - 0x1_8000
    def ADDR_FBUFFER  = 0x00020000 // 0x2_0000 - 0x2_8000
    def ADDR_WBUFFER  = 0x00030000 // 0x3_0000 - 0x3_8000
    def ADDR_MBUFFER  = 0x00040000 // 0x4_0000 - 0x4_8000
}