
package io.test

import Chisel._

import io._


class vgaTester(c: Vga) extends Tester(c) {
  step(700000)
}

object vgaTest {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array("--genHarness", "--test", "--backend", "c",
      "--compile", "--vcd", "--targetDir", "Vga"),
      () => Module(new Vga( ))) {
        c => new vgaTester(c)
      }
  }
}