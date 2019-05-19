/*
 * Simple I/O module for LEDs
 *
 * Authors: Wolfgang Puffitsch (wpuffitsch@gmail.com)
 *
 */

package io

import Chisel._
import patmos.Constants._
import ocp._

object Vga extends DeviceObject {

  def init(params: Map[String, String]) = {}

  def create(params: Map[String, String]) : Vga = {
    Module(new Vga( ))
  }

  trait Pins {
    val vgaPins = new Bundle() {
      val vga       = Bits( OUTPUT )
      val hSync     = Bits( OUTPUT )
      val vSync     = Bits( OUTPUT )
      val blank     = Bits( OUTPUT )
      val greenSync = Bits( OUTPUT )
      val pixClk    = Bits( OUTPUT )
      // val column    = Bits( OUTPUT )
      // val row       = Bits( OUTPUT )
      // val red       = Bits( INPUT )
      // val green     = Bits( INPUT )
      // val blue      = Bits( INPUT )
      // val mem       = new OcpBurstMasterPort(EXTMEM_ADDR_WIDTH, DATA_WIDTH, BURST_LENGTH)
    }
  }
}

class Vga( ) extends CoreDevice() {

  override val io = new CoreDeviceIO() with Vga.Pins
  val vgaReg = Reg(init = Bits(0, 24) )
  val pixclkReg = Reg(init = Bool(true)) 
  pixclkReg := !pixclkReg
  //io.vgaPins.vga := vgaReg
  val vgaController     = Module(new vgaController(1,1,1) )
  val vgaImage          = Module(new vgaImage(1152, 864) )
  val vgaImageBox       = Module(new vgaImageBox(1152, 864) )

  // io.vgaPins.column     := vgaController.io.column
  // io.vgaPins.row        := vgaController.io.row
  io.vgaPins.hSync      := vgaController.io.h_sync
  io.vgaPins.vSync      := vgaController.io.v_sync
  io.vgaPins.blank      := vgaController.io.n_blank
  io.vgaPins.greenSync  := vgaController.io.n_sync
  io.vgaPins.pixClk     := pixclkReg

  vgaImage.io.column    := vgaController.io.column
  vgaImage.io.row       := vgaController.io.row
  // vgaImage.io.iRed      := io.red
  // vgaImage.io.iGreen    := io.green
  // vgaImage.io.iBlue     := io.blue
  vgaImage.io.disp_ena  := vgaController.io.disp_ena
  io.vgaPins.vga        := vgaImage.io.oVga
  
  vgaImageBox.io.column    := vgaController.io.column
  vgaImageBox.io.row       := vgaController.io.row
  // vgaImageBox.io.disp_ena  := vgaController.io.disp_ena
  // io.vgaPins.vga        := Cat(Cat(vgaImageBox.io.vga_red, vgaImageBox.io.vga_green), vgaImageBox.io.vga_blue)
  
  
  // vgaImage.io.mem       <> io.vgaPins.mem

  val respReg   = Reg(init = OcpResp.NULL)
  respReg       := OcpResp.NULL
  val char      = Reg(init = Bits(0,32))
  val valid     = Reg(init = Bool(false))
  val burstReg  = Reg(init = UInt(0, 3))
  valid         := valid

  when( vgaImage.io.ok ){
    valid     := Bool(false)
  }

  // Write 
  when(io.ocp.M.Cmd === OcpCmd.WR) {
    respReg   := OcpResp.DVA
    when(burstReg === UInt(0) ){
      char(31, 0)   := SInt( 0x81422418 ) //io.ocp.M.Data
      burstReg      := burstReg + UInt(1)
      valid         := Bool(true)
    } .elsewhen(burstReg === UInt(1) ){
      // char(63, 32)  := Bits( 0xffffffff )
      char  := SInt( 0x0000000 )
      burstReg      := burstReg + UInt(1)
      valid         := Bool(true)
    } .elsewhen(burstReg === UInt(2) ){
      // char(95, 64)  := Bits( 0x0000000 )
      char  := SInt( 0xf00ff00f )
      burstReg      := burstReg + UInt(1) 
      valid         := Bool(true)
    } .elsewhen(burstReg === UInt(3) ){
      // char(127, 96) := Bits( 0xf0f0f0f0 )//io.ocp.M.Data
      char := SInt( 0x0000000 )
      burstReg      := UInt(0)
      valid         := Bool(true)
    }
  }

  // Read current state
  when(io.ocp.M.Cmd === OcpCmd.RD) {
    respReg   := OcpResp.DVA
  }

  vgaImage.io.valid := valid
  vgaImage.io.char := char
  // Connections to master
  io.ocp.S.Resp := respReg
  io.ocp.S.Data := char
}

/*******************************************************/
class vgaImage( Width : Int, Height : Int ) extends Module {
    val io = new Bundle {
        val column      = Bits(INPUT, 16)
        val row         = Bits(INPUT, 16)
        val disp_ena    = Bool(INPUT)
        val valid       = Bool(INPUT)
        val ok          = Bool(OUTPUT)
        // val iRed        = UInt(INPUT, 8)
        // val iGreen      = UInt(INPUT, 8)
        // val iBlue       = UInt(INPUT, 8)
        val char        = UInt(INPUT, width = 32)
        val oVga        = UInt(OUTPUT, 24)
    }
    val MEM_SIZE   = 512
    // val frame         = Reg(init = UInt(0,16))
    val redreg        = Reg( init = UInt(0, 8) )
    val greenreg      = Reg( init = UInt(0, 8) )
    val bluereg       = Reg( init = UInt(100, 8) )
    // val dataReceived  = Reg( init = UInt(255, 128) )
    // val burstReg      = Reg( init = UInt(0, 3) )
    // val memAddrReg    = Reg( init = SInt(0xf00b0000) )

    val m       = Mem( UInt(width = 32), MEM_SIZE)
    val ptr     = Reg(init = UInt(0, 16))
    val endPtr  = Reg(init = UInt(0, 10))
    val pixPtr  = Reg(init = UInt(0, 3))
    val s       = Reg(init = UInt(1, 1))
    s := UInt(0)

    io.ok := Bool(false)
    when( io.valid ){
      io.ok := Bool(true)
      m(endPtr) := io.char
      // m(endPtr) := Bits( 0x100000000, 100 ) //Cat(Cat(Bits( 0xffff0000 ), Bits( 0xffff0000 )), Cat(Bits( 0xffff0000 ), Bits( 0xffff0000 )))
      // m(endPtr) := SInt( 0xffffffff )
      when(endPtr < UInt(MEM_SIZE) ){
        endPtr := endPtr + UInt(1)
      } .otherwise{
        endPtr := UInt(0)
      }
    }
    for(t <- 400 until 500){
      m(t) := SInt(0xffffffff)
    }
    // for(t <- 128 until 255){
    //   m(t) := SInt(0xffff0000)
    // }
    // val rowPtr     = (Height/8)*(io.row>>4 )
    // val rowPtr     = (144)*(io.row>>4 )
    // val rowPtr     = (128 + 16)*(io.row>>4 )
    val rowPtr  = Reg(init = Bits(0,16))
    rowPtr  := ((io.row>>Bits(4)) << Bits(4)) + ((io.row>>Bits(4))<<Bits(7) )
    ptr     := ((io.column>>1)&Bits(0xfffc))+((io.row>>2)&Bits(0x3)+rowPtr)
    when( pixPtr < UInt(8) ){
      when(ptr < UInt(MEM_SIZE,16) ){
        s := m( ptr )( ((io.row & UInt(0x0003))<<3)+pixPtr )
      } .otherwise{
        s := UInt(0)
      }
      pixPtr := pixPtr + UInt(1)
    } .otherwise{
      pixPtr := UInt(0)
    }
    when ( io.column === 0.U && io.row === 0.U){
      ptr := UInt(0)
    }
    when ( io.column > 200.U && io.column < 400.U && io.row > 200.U &&io.row < 400.U){
      s := UInt(1)
    }
    // io.mem.M.Cmd    := OcpCmd.IDLE
    // io.mem.M.Addr   := memAddrReg
    // dataReceived := dataReceived
    // burstReg := burstReg
    // when ( io.column === 0.U && io.row === 0.U){
      
    // }
    
    // when( io.mem.S.Resp === OcpResp.DVA ){
    
    /*Does not work - the connection to the memory might not be available*/
    // when(burstReg === UInt(0)) {
    //   when ( io.column === 0.U && io.row === 0.U){
    //     io.mem.M.Cmd    := OcpCmd.RD
    //     burstReg := UInt(1)
    //     dataReceived := UInt(0xffffff)
    //   }
    // } .elsewhen( burstReg === UInt(1) ) {
    //   when( io.mem.S.Resp === OcpResp.DVA ){
    //     io.mem.M.Cmd    := OcpCmd.IDLE
    //     dataReceived    := io.mem.S.Data
    //     dataReceived := UInt(0xffff00)
    //     burstReg := UInt(2)
    //   } .otherwise{
    //     io.mem.M.Cmd    := OcpCmd.RD
    //   }
    // } .elsewhen(burstReg === UInt(2)){
    //   dataReceived    := Cat(io.mem.S.Data, dataReceived)
    //   dataReceived := UInt(0xff00ff)
    //   burstReg := UInt(3)
    // } .elsewhen(burstReg === UInt(3)){
    //   dataReceived    := Cat(io.mem.S.Data, dataReceived)
    //   dataReceived := UInt(0x00ffff)
    //   burstReg := UInt(4)
    // } .elsewhen(burstReg === UInt(4)){
    //   dataReceived    := Cat(io.mem.S.Data, dataReceived)
    //   dataReceived := UInt(0x99ff99)
    //   burstReg := UInt(0)
    // }
    
    when ( io.disp_ena ) {
        // io.oVga     := Cat(Cat(io.iBlue, io.iGreen), io.iRed)
        io.oVga     := Fill(24, s)
    } .otherwise {
        io.oVga     := UInt(0)
    }
}

object vgaImage extends App{
    chiselMain(Array[String]("--backend", "v", "--targetDir", "generated/vgaImage"),
      () => Module(new vgaImage(1152, 864) ))
}
/*******************************************************/
class vgaImageBox( Width : Int, Height : Int ) extends Module {
    val io = new Bundle {
        val column      = UInt(INPUT, 16)
        val row         = UInt(INPUT, 16)
        val disp_ena     = Bool(INPUT)
        val vga_red     = UInt(OUTPUT, 8)
        val vga_green   = UInt(OUTPUT, 8)
        val vga_blue    = UInt(OUTPUT, 8)
    }

    val hJump = UInt(10)
    val vJump = UInt(10)
    val frame = Reg(init = UInt(0,16))

    val boxHeight     = 50 
    val boxWidth      = 50 
    val dir           = Reg(init = Bool(true) )

    val boxPosX       = Reg( init = UInt(0, 11) )
    val boxPosY       = Reg( init = UInt(0, 11) )
    val redreg        = Reg( init = UInt(0, 8) )
    val greenreg      = Reg( init = UInt(0, 8) )
    val bluereg       = Reg( init = UInt(100, 8) )
    val boxColR       = Reg( init = UInt(255, 8) )
    val boxColG       = Reg( init = UInt(128, 8))
    val boxColB       = Reg( init = UInt(75, 8))
    val boxCol_temp   = Reg( init = UInt(0, 8))
    
    boxPosX := boxPosX
    boxPosY := boxPosY
    frame := frame
    // when ( io.column === 0.U && io.row === 0.U){
    //     frame := frame + UInt(1)
    //     when (boxPosX > UInt(Width-boxWidth) ){
    //         boxPosX := UInt(10)
    //         when (boxPosY < UInt(Height-boxHeight) ){
    //             boxPosY := boxPosY + vJump
    //         } .otherwise {
    //             boxPosX := UInt(0)
    //             boxPosY := UInt(0)
    //         }
    //     } .otherwise {
    //         boxPosX := UInt(boxPosX) + hJump
    //     }
    // }

    when (frame === UInt(1)){
        boxColR := UInt(255)
        boxColG := UInt(128)
        boxColB := UInt(75)
    }
    // Update box possition and colours
    when ( io.column === 0.U && io.row === 0.U){
        frame := frame + UInt(1)
        when ( boxPosX > UInt(Width-boxWidth-1) && dir){
            //boxPosX := UInt(10)
            dir := Bool(false)
            when (boxPosY < UInt(Height-boxHeight-1) ){
                boxPosY := boxPosY + vJump
                // boxCol_temp := boxColR
                // boxColR := boxColG 
                // boxColG := boxColB
                // boxColB := boxCol_temp
                boxColR := boxColG 
                boxColG := boxColB
                boxColB := boxColR
            } .otherwise {
                boxPosX := UInt(0)
                boxPosY := UInt(0)
            }
        } .elsewhen ( boxPosX === UInt(0) && ~dir ){
            boxPosY := boxPosY + vJump
            dir := Bool(true)
            boxColR := boxColG 
            boxColG := boxColB
            boxColB := boxColR
        } .otherwise {
            when( dir ){
                boxPosX := UInt(boxPosX) + hJump
            } .otherwise {
                boxPosX := UInt(boxPosX) - hJump
            }
        }
    }

    // Draw box in frame
    when ( io.column >= boxPosX && io.column < boxPosX + UInt(boxWidth) &&
          io.row >= boxPosY && io.row < boxPosY + UInt(boxHeight) ){
    // when ( io.column < UInt(200) &&
    //         io.row < UInt(100) ){
        redreg := boxColR
        greenreg := boxColG
        bluereg := boxColB
    } .otherwise {
        redreg := UInt(0)
        greenreg := UInt(0)
        bluereg := UInt(0)
    }

    when ( io.disp_ena ) {
        io.vga_red := redreg
        io.vga_green := greenreg
        io.vga_blue := bluereg
    } .otherwise {
        io.vga_red := UInt(0)
        io.vga_green := UInt(0)
        io.vga_blue := UInt(0)
    }
}

object vgaImageBox extends App{
    chiselMain(Array[String]("--backend", "v", "--targetDir", "generated/vgaImage"),
      () => Module(new vgaImageBox(1152, 864) ))
}

/*******************************************************/
class vgaController(Clkfreq: Int, Width: Int, Height: Int ) extends Module {
	val io = new Bundle {
		val h_sync 		= UInt(OUTPUT, width = 1)
		val v_sync 		= UInt(OUTPUT, width = 1)
		val disp_ena 	= UInt(OUTPUT, width = 1)
		val column 		= SInt(OUTPUT, width = 16)
		val row 		  = SInt(OUTPUT, width = 16)
		val n_blank 	= UInt(OUTPUT, width = 1)
		val n_sync 		= UInt(OUTPUT, width = 1)
	} 

// 640x480 60Hz
	// val h_pulse 	= 96
	// val h_bp 		= 48
	// val h_pixels 	= 640
	// val h_fp 		= 16
	// val h_pol 		= Bool(false)
	// val v_pulse 	= 2
	// val v_bp 		= 33
	// val v_pixels 	= 480
	// val v_fp 		= 10
	// val v_pol 		= Bool(false)

	// 800x600 60 Hz
	// val h_pulse 	= 128
	// val h_bp 		= 88
	// val h_pixels 	= 800
	// val h_fp 		= 40
	// val h_pol 		= Bool(true) 
	// val v_pulse 	= 4
	// val v_bp 		= 23
	// val v_pixels 	= 600
	// val v_fp 		= 1
	// val v_pol 		= Bool(true)

  // 1152x864 60Hz
	val h_pulse 	= 120
	val h_bp 		  = 184
	val h_pixels 	= 1152
	val h_fp 		  = 64
	val h_pol 		= Bool(false)
	val v_pulse 	= 3
	val v_bp 		  = 27
	val v_pixels 	= 864
	val v_fp 		  = 1
	val v_pol 		= Bool(true)

	val h_period: Int = h_pulse + h_bp + h_pixels + h_fp  //total number of pixel clocks in a row
	val v_period: Int = v_pulse + v_bp + v_pixels + v_fp  //total number of rows in column



	val h_count 	= Reg( init = UInt(0,16) )
	val v_count 	= Reg( init = UInt(0,16) )

	val blankreg 	= Reg(init = Bool( true ))
	val n_sync 		= Reg(init = Bool( false ))
	
	val hsyncreg 	= Reg(init = Bool( ~h_pol ))
	val vsyncreg 	= Reg(init = Bool( ~v_pol ))

	val columnreg = Reg(init = UInt(0, 16))
	val rowreg 		= Reg(init = UInt(0, 16))
	val dispreg 	= Reg(init = Bool( false ))

	// Picture Counters
	when ( h_count < UInt(h_period - 1 )) {		// Horisontal counter - Pixels
		h_count := h_count + UInt(1)
	} .otherwise {
		h_count := UInt(0)
		when (v_count < UInt(v_period - 1)) {	// veritcal counter - Rows
			v_count := v_count + UInt(1)
		} .otherwise {
			v_count := UInt(0)
		}
	}
	
	//horizontal sync signal
	when (h_count < UInt(h_pixels + h_fp) || h_count >= UInt(h_pixels + h_fp + h_pulse)) {
		hsyncreg := ~h_pol			// deassert horiztonal sync pulse
	} .otherwise {
		hsyncreg := h_pol			// assert horiztonal sync pulse
	}

	// vertical sync signal
	when (v_count < UInt(v_pixels + v_fp ) || v_count >= UInt(v_pixels + v_fp + v_pulse)) {
		vsyncreg := ~v_pol			// deassert vertical sync pulse
	} .otherwise {
		vsyncreg := v_pol			// assert vertical sync pulse
	}

	// set pixel coordinates
	
	when (h_count < UInt(h_pixels)) {  	// horiztonal display time
		columnreg := h_count		// set horiztonal pixel coordinate
	} .otherwise {
		columnreg := columnreg
	}
	
	when (v_count < UInt(v_pixels)) {		// vertical display time
		rowreg := v_count			// set vertical pixel coordinate
	} .otherwise {
		rowreg := rowreg
	}
	
	// set display enable output
	when ( (h_count < UInt(h_pixels)) && (v_count < UInt(v_pixels)) ) {  	// display time
		dispreg := Bool(true)						 	// enable display
	} .otherwise {									// blanking time
		dispreg := Bool(false)							// disable display
	}

	io.n_blank 		:= blankreg
	io.n_sync 		:= n_sync
	io.h_sync 		:= hsyncreg
	io.v_sync 		:= vsyncreg
	io.column			:= columnreg
	io.row				:= rowreg
	io.disp_ena 	:= dispreg
}
object vgaController extends App{
    chiselMain(Array[String]("--backend", "v", "--targetDir", "generated/vgaController"),
      () => Module(new vgaController(50000000, 800, 600) ))
}

class vgaTester(c: vgaController) extends Tester(c) {
  step(700000)
}

object vgaTest {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array("--genHarness", "--test", "--backend", "c",
      "--compile", "--vcd", "--targetDir", "generated/vgaController"),
      () => Module(new vgaController(50000000,800,600))) {
        c => new vgaTester(c)
      }
  }
}
