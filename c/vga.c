
#include "include/bootable.h"
#include <machine/spm.h>
#include <machine/patmos.h>
#include <stdio.h>

int main() {
  volatile _SPM int *vga_ptr  = (volatile _SPM int *) 0xf00b0000;
  char c = 'A';
  int x, y, z, i, j;
  typedef struct font {
    int A;
    int B;
    int C;
    int D;
  } Font; 
  Font vBar;
  vBar.A = 0b00111100000000000000000111101010;
  vBar.B = 0b11111111111111111111111111111111;
  vBar.C = 0b11111111111111111111111111111111;
  vBar.D = 0b11111111111111111111111111111111;
  
  for (;;) {
    *vga_ptr = vBar.A;
    for (i=10000; i!=0; --i){}

    *vga_ptr = vBar.B;
    for (i=10000; i!=0; --i){}

    *vga_ptr = vBar.C;
    for (i=10000; i!=0; --i){}

    *vga_ptr = vBar.D;
    for (i=100000000; i!=0; --i){}

    // x = *vga_ptr;
    // printf("Value read from Hardware: %d\n", x);
     scanf("%d", &z);
     scanf("%d", &y);
     x    = ((z>>1)&0xfffc)+((y>>2)&0x3) + ((y>>4) <<4) + ((y >>4) <<7 );
     printf("Result: %d\n",x);
    //  *vga_ptr = y;
	}
}