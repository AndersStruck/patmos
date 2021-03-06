/*
 * "I/O" module to access performance counters
 *
 * Authors: Wolfgang Puffitsch (wpuffitsch@gmail.com)
 *
 */


package io

import Chisel._

import patmos.Constants._
import patmos.PerfCounterIO

import ocp._

object PerfCounters extends DeviceObject {

  def init(params: Map[String, String]) = { }

  def create(params: Map[String, String]) : PerfCounters = {
    Module(new PerfCounters())
  }

  trait Pins {
  }
}

class PerfCounters() extends CoreDevice() {

  override val io = new CoreDeviceIO() with PerfCounters.Pins {
    override val internalPort = new Bundle() {
      val perf = new PerfCounterIO().asInput
    }
  }

  val masterReg = Reg(next = io.ocp.M)

  // Default response
  val resp = Bits()
  val data = Bits(width = DATA_WIDTH)
  resp := OcpResp.NULL
  data := Bits(0)

  // Ignore writes
  when(masterReg.Cmd === OcpCmd.WR) {
    resp := OcpResp.DVA
  }

  val PERFCOUNTER_COUNT = 10

  val inputVec = Vec.fill(PERFCOUNTER_COUNT) { Reg(Bool()) }
  inputVec(0) := io.internalPort.perf.ic.hit
  inputVec(1) := io.internalPort.perf.ic.miss
  inputVec(2) := io.internalPort.perf.dc.hit
  inputVec(3) := io.internalPort.perf.dc.miss
  inputVec(4) := io.internalPort.perf.sc.spill
  inputVec(5) := io.internalPort.perf.sc.fill
  inputVec(6) := io.internalPort.perf.wc.hit
  inputVec(7) := io.internalPort.perf.wc.miss
  inputVec(8) := io.internalPort.perf.mem.read
  inputVec(9) := io.internalPort.perf.mem.write

  val counterVec = Vec.fill(PERFCOUNTER_COUNT) { Reg(init = UInt(0, width = DATA_WIDTH)) }
  for (i <- 0 until PERFCOUNTER_COUNT) {
    when (inputVec(i)) {
      counterVec(i) := counterVec(i) + UInt(1)
    }
  }

  // Read information
  data := counterVec(masterReg.Addr(5,2))
  when(masterReg.Cmd === OcpCmd.RD) {
    resp := OcpResp.DVA
  }

  // Connections to master
  io.ocp.S.Resp := resp
  io.ocp.S.Data := data
}
