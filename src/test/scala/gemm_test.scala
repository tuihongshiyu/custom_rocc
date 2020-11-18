package systolicMatrix

import chisel3._
import chisel3.iotesters.{PeekPokeTester,ChiselFlatSpec}

object testMeshMain extends App{
    Driver.execute(args,()=>new Mesh(Constant.latency,Constant.tileRows,Constant.tileColumns,Constant.meshRows,Constant.meshColumns))
//
}

class MeshTests(c:Mesh) extends PeekPokeTester(c){
    val matA:Seq[Seq[UInt]] = Seq(Seq[UInt](1.U,2.U,3.U,4.U),Seq[UInt](5.U,6.U,7.U,8.U),Seq[UInt](9.U,10.U,11.U,12.U),Seq[UInt](13.U,14.U,15.U,16.U))
    val matB:Seq[Seq[UInt]] = Seq(Seq[UInt](1.U,2.U,3.U,4.U),Seq[UInt](5.U,6.U,7.U,8.U),Seq[UInt](9.U,10.U,11.U,12.U),Seq[UInt](13.U,14.U,15.U,16.U)).transpose
    val matD:Seq[Seq[UInt]] = Seq(Seq[UInt](10.U,20.U,30.U,40.U),Seq[UInt](50.U,60.U,70.U,80.U),Seq[UInt](9.U,10.U,11.U,12.U),Seq[UInt](13.U,14.U,15.U,16.U))
    val ctrl_d=Seq.fill(Constant.meshRows)(Constant.df)
    ctrl_d.zipWithIndex.foreach{case(value,index)=>poke(c.io.in_control(index)(0).dataflow,value)}
/*
    //WS start
    //start load weight
    poke(c.io.in_valid(0)(0),1.B)
    poke(c.io.in_valid(1)(0),1.B)
    poke(c.io.in_control(0)(0).propagate,0.U)
    poke(c.io.in_control(1)(0).propagate,0.U)

    poke(c.io.in_d(0)(0),2.U)    
    poke(c.io.in_d(1)(0),4.U)
    step(1)
    //step(1)
    poke(c.io.in_d(0)(0),1.U)    
    poke(c.io.in_d(1)(0),3.U)
    step(1)
 
    //load weight over  
    poke(c.io.in_control(0)(0).propagate,1.U)
    poke(c.io.in_control(1)(0).propagate,1.U) 
    poke(c.io.in_a(0)(0),1.U)
    poke(c.io.in_a(1)(0),0.U)
    poke(c.io.in_b(0)(0),6.U)
    poke(c.io.in_b(1)(0),0.U)
    step(1)
    poke(c.io.in_a(0)(0),3.U)
    poke(c.io.in_a(1)(0),2.U)
    poke(c.io.in_b(0)(0),7.U)
    poke(c.io.in_b(1)(0),8.U)    
    step(1)
    poke(c.io.in_valid(0)(0),0.B)
    poke(c.io.in_a(0)(0),0.U)
    poke(c.io.in_a(1)(0),4.U)
    poke(c.io.in_b(0)(0),0.U)
    poke(c.io.in_b(1)(0),9.U)
    step(1)

    poke(c.io.in_valid(1)(0),0.B)
    step(1)
    step(1)
//WS end
*/
//OS statrt 
    poke(c.io.in_control(0)(0).propagate,1.U)
    poke(c.io.in_control(1)(0).propagate,1.U)  
    poke(c.io.in_valid(0)(0),1.B)
    poke(c.io.in_valid(1)(0),1.B)
    step(1)
    step(1)
 //load d 
    poke(c.io.in_control(0)(0).propagate,0.U)
    poke(c.io.in_control(1)(0).propagate,0.U)

    poke(c.io.in_d(0)(0),8.U)    
    poke(c.io.in_d(1)(0),9.U)
    step(1)
    //step(1)
    poke(c.io.in_d(0)(0),6.U)    
    poke(c.io.in_d(1)(0),7.U)
    step(1)
    //  load d over
    poke(c.io.in_control(0)(0).propagate,1.U)
    poke(c.io.in_control(1)(0).propagate,1.U)   

    poke(c.io.in_a(0)(0),1.U)
    poke(c.io.in_a(1)(0),0.U)
    poke(c.io.in_b(0)(0),1.U)
    poke(c.io.in_b(1)(0),0.U)
    step(1)
    poke(c.io.in_a(0)(0),2.U)
    poke(c.io.in_a(1)(0),3.U)
    poke(c.io.in_b(0)(0),2.U)
    poke(c.io.in_b(1)(0),3.U)    
    step(1)
    poke(c.io.in_valid(0)(0),0.B)
    //poke(c.io.in_valid(0)(0),0.B)
    poke(c.io.in_a(0)(0),0.U)
    poke(c.io.in_a(1)(0),4.U)
    poke(c.io.in_b(0)(0),0.U)
    poke(c.io.in_b(1)(0),4.U)
    step(1)
    step(1)
    step(1)
    step(1)
    poke(c.io.in_control(0)(0).propagate,0.U)
    poke(c.io.in_control(1)(0).propagate,0.U) 
    step(1)
    step(1)
    step(1)
    step(1)
//OS end
    /*
    step(1)
    step(1)
    step(1)
    poke(c.io.in_control(1)(0).propagate,0.U)

    poke(c.io.in_valid(1)(0),1.B)
//valid.zipWithIndex.foreach{case(value,index)=>poke(c.io.in_valid(index)(index),value)}

    poke(c.io.in_a(1)(0),1.U)
    
    poke(c.io.in_b(1)(0),2.U)
    poke(c.io.in_d(1)(0),3.U)

    val valid=Seq.fill(Constant.meshRows)(1.B)
    valid.zipWithIndex.foreach{case(value,index)=>poke(c.io.in_valid(index)(0),value)}
    val a=Seq.fill(Constant.meshRows)(1.U)
    a.zipWithIndex.foreach{case(value,index)=>poke(c.io.in_a(index)(0),value)}
    val b=Seq.fill(Constant.meshRows)(2.U)
    b.zipWithIndex.foreach{case(value,index)=>poke(c.io.in_b(index)(0),value)}
    val d=Seq.fill(Constant.meshRows)(4.U)
    d.zipWithIndex.foreach{case(value,index)=>poke(c.io.in_d(index)(0),value)}
     
    var ctrl_p=Seq.fill(Constant.meshRows)(1.U)
    ctrl_p.zipWithIndex.foreach{case(value,index)=>poke(c.io.in_control(index)(0).propagate,value)}
    val ctrl_d=Seq.fill(Constant.meshRows)(Constant.df)
    ctrl_d.zipWithIndex.foreach{case(value,index)=>poke(c.io.in_control(index)(0).dataflow,value)}
    step(4)
    ctrl_p=Seq.fill(Constant.meshRows)(0.U)
    ctrl_p.zipWithIndex.foreach{case(value,index)=>poke(c.io.in_control(index)(0).propagate,value)}    
 
    step(10)
    ctrl_p=Seq.fill(Constant.meshRows)(1.U)
    ctrl_p.zipWithIndex.foreach{case(value,index)=>poke(c.io.in_control(index)(0).propagate,value)}    
 
    step(1)
    step(1)
    step(500)

*/
}
class MeshTester extends ChiselFlatSpec{
    "running with --generate-vcd-output on" should "create a vcd file from your test" in {
    	iotesters.Driver.execute(Array(
    		"--generate-vcd-output","on",
    		"--target-dir","genarated/gemm",
    		"--top-name","hello_vcd"),()=> new Mesh(Constant.latency,Constant.tileRows,Constant.tileColumns,Constant.meshRows,Constant.meshColumns)){
    		c=>new MeshTests(c)}should be(true)
    	}
}

