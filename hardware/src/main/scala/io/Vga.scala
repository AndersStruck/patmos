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
    }
  }
}

class Vga( ) extends CoreDevice() {

  override val io = new CoreDeviceIO() with Vga.Pins
  val pixclkReg = Reg(init = Bool(true)) 
  pixclkReg := !pixclkReg
  val vgaController     = Module(new vgaController(1,1,1) )
  val vgaImage          = Module(new vgaImage(1152, 864) )

  io.vgaPins.hSync      := vgaController.io.h_sync
  io.vgaPins.vSync      := vgaController.io.v_sync
  io.vgaPins.blank      := vgaController.io.n_blank
  io.vgaPins.greenSync  := vgaController.io.n_sync
  io.vgaPins.pixClk     := pixclkReg
  io.vgaPins.vga        := vgaImage.io.oVga
  vgaImage.io.column    := vgaController.io.column
  vgaImage.io.row       := vgaController.io.row
  vgaImage.io.disp_ena  := vgaController.io.disp_ena
  

  val respReg   = Reg(init = OcpResp.NULL)
  val char      = Reg(init = Bits(0,32))
  val valid     = Reg(init = Bool(false))
  val burstReg  = Reg(init = UInt(0, 3))
  respReg       := OcpResp.NULL
  valid         := valid
  char          := char
  when( vgaImage.io.ok ){
    valid         := Bool(false)
  }

  // Write 
  when(io.ocp.M.Cmd === OcpCmd.WR) {
    respReg       := OcpResp.DVA
    char          := io.ocp.M.Data
    valid         := Bool(true)
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
        val char        = UInt(INPUT, width = 32)
        val oVga        = UInt(OUTPUT, 24)
    }
    val MEM_SIZE      = 512

    val redreg        = Reg( init = UInt(0, 8) )
    val greenreg      = Reg( init = UInt(0, 8) )
    val bluereg       = Reg( init = UInt(100, 8) )
    val m             = Mem( UInt(width = 32), MEM_SIZE)
    val charPtr       = Reg(init = UInt(0, 16))
    val endPtr        = Reg(init = UInt(0, 10))
    val s             = Reg(init = UInt(1, 1))
    s := UInt(0)
    endPtr := endPtr
    io.ok := Bool(false)

    when( io.valid ){
      io.ok := Bool(true)
      m(endPtr) := io.char
      when(endPtr < UInt(MEM_SIZE) ){
        endPtr := endPtr + UInt(1)
      } .otherwise{
        endPtr := UInt(0)
      }
    }

    //          (Height/8)*(io.row>>4 )
    //          (144)*(io.row>>4 )
    //          (128 + 16)*(io.row>>4 )
    val rowPtr  = Reg(init = Bits(0,16))
    rowPtr  := ((io.row>>Bits(4)) << Bits(4)) + ((io.row>>Bits(4))<<Bits(7) )
    charPtr := ((io.column>>1)&Bits(0xfffc))+((io.row>>2)&Bits(0x3)+rowPtr)
    ////// Outside of memory space ? 
    ////// Does not seem to work, since there is replication issue !
    when(charPtr < UInt(MEM_SIZE) ){ 
      s := m( charPtr )( UInt(31) - (((io.row & UInt(0x3) )<<3) + (io.column & UInt(0x7))) )
    } .otherwise{
      s := UInt(0)
    }

    when ( io.column === 0.U && io.row === 0.U){
      charPtr := UInt(0)
    }
    ///// Draws a white box on the display. 200x200
    // when ( io.column > 200.U && io.column < 400.U && io.row > 200.U &&io.row < 400.U){
    //   s := UInt(1)
    // }
    
    when ( io.disp_ena ) {
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

	val blankreg 	= Reg( init = Bool( true ) )
	val n_sync 		= Reg( init = Bool( false ) )
	
	val hsyncreg 	= Reg( init = Bool( ~h_pol ) )
	val vsyncreg 	= Reg( init = Bool( ~v_pol ) )

	val columnreg = Reg( init = UInt(0, 16) )
	val rowreg 		= Reg( init = UInt(0, 16) )
	val dispreg 	= Reg( init = Bool( false ) )

	// Pixel Counters
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
      () => Module(new vgaController(80000000, 1152, 864) ))
}

class vgaContTester(c: vgaController) extends Tester(c) {
  step(700000)
}

object vgaContTest {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array("--genHarness", "--test", "--backend", "c",
      "--compile", "--vcd", "--targetDir", "generated/vgaController"),
      () => Module(new vgaController(80000000,1152,864))) {
        c => new vgaContTester(c)
      }
  }
}
