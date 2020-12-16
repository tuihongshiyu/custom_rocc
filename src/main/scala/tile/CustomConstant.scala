package freechips.rocketchip.tile


object C {
  def W_MOVE3 = 2048
  def W_MOVE = 512 //搬移位宽
  def W_DATA = 256 //数据粒度
  def W_ADDR = 30  //地址位宽
  def W_INDEX = 4  //模块编号
  def W_ARRAY = 8
  
  def N_NODE = 12  //节点数量
  def N_BUFFER = 10//Buffer数量
  def N_MOVE3 = 6  //Move3节点数量
  def N_ARRAY = 16//脉动阵列size

  def DEPTH_MOVE_CMD = 6
  def DEPTH_TAG = 18
}

object CN { //constant no.
  // op unit
  def NaN = 0
  def Core = 1
  def Move_1 = 2
  def Move_2 = 3
  def Move_3 = 4
  def SystolicMatrix = 5
  // mem node
  def InputA = 0

  def InputB = 1

  def FeatureA = 2

  def FeatureB = 3

  def WeightA = 4

  def WeightB = 5

  def MiddleA = 6

  def MiddleB = 7

  def OutputA = 8

  def OutputB = 9

  def DDR4 = 10

  def Memory = 11
}

object CA { //constant addr
  def InputA = 0x20000000

  def InputB = 0x20010000

  def FeatureA = 0x20020000

  def FeatureB = 0x20030000

  def WeightA = 0x20040000

  def WeightB = 0x20050000

  def MiddleA = 0x20060000

  def MiddleB = 0x20070000

  def OutputA = 0x20080000

  def OutputB = 0x20090000

  def DDR4 = 0x00000000
}

object CS { //constant size / bit
  def Input = 1024 * 1024 * 8

  // def Feature = 128 * 1024 * 8
  def Feature = C.N_ARRAY * C.N_ARRAY * C.W_ARRAY

  // def Weight = 128 * 1024 * 8
  def Weight = C.N_ARRAY * C.N_ARRAY * C.W_ARRAY

  // def Middle = 128 * 1024 * 8
  def Middle = C.N_ARRAY * C.N_ARRAY * C.W_ARRAY * 2

  def Output = 128 * 1024 * 8
}


