
#include "include/bootable.h"
#include <machine/spm.h>
#include <machine/patmos.h>
#include <stdio.h>

typedef struct font {
  int first;
  int second;
  int third;
  int fourth;
} Font;

struct font GetChar(char ch);
void writeChar(char ch);

int main() {
  
  char c;
  int x, y, z, i, j;
  puts("Available characters: A B E H L O");
  puts("Enter a character and press enter.");
  writeChar('H');
  writeChar('E');
  writeChar('L');
  writeChar('L');
  writeChar('O');
  for (;;) {
    scanf("%c", &c);
    writeChar(c);
	}
}

void writeChar(char ch){
  volatile _SPM int *vga_ptr  = (volatile _SPM int *) 0xf00b0000;
  Font bitmap = GetChar(ch);
  *vga_ptr = bitmap.first;
  *vga_ptr = bitmap.second;
  *vga_ptr = bitmap.third;
  *vga_ptr = bitmap.fourth;
}

struct font GetChar(char ch) {
    Font character;
      switch(ch) {
      case 'A' :
          character.first   = 0b00000000000000000001000000111000;
          character.second  = 0b01101100110001101100011011111110;
          character.third   = 0b11000110110001101100011011000110;
          character.fourth  = 0b00000000000000000000000000000000;
         break;
      case 'B' :
          character.first   = 0b00000000000000001111110001100110;
          character.second  = 0b01100110011001100111110001100110;
          character.third   = 0b01100110011001100110011011111100;
          character.fourth  = 0b00000000000000000000000000000000;
         break;
      case 'E' :
          character.first   = 0b00000000000000001111111001100110;
          character.second  = 0b01100010011010000111100001101000;
          character.third   = 0b01100000011000100110011011111110;
          character.fourth  = 0b00000000000000000000000000000000;
         break;
      case 'H' :
          character.first   = 0b00000000000000001100011011000110;
          character.second  = 0b11000110110001101111111011000110;
          character.third   = 0b11000110110001101100011011000110;
          character.fourth  = 0b00000000000000000000000000000000;
         break;
      case 'L' :
          character.first   = 0b00000000000000001111000001100000;
          character.second  = 0b01100000011000000110000001100000;
          character.third   = 0b01100000011000100110011011111110;
          character.fourth  = 0b00000000000000000000000000000000;
         break;
      case 'O' :
          character.first   = 0b00000000000000000111110011000110;
          character.second  = 0b11000110110001101100011011000110;
          character.third   = 0b11000110110001101100011001111100;
          character.fourth  = 0b00000000000000000000000000000000;
         break;
      case '\n' :
          character.first   = 0b00000000000000000000000000000000;
          character.second  = 0b00000000000000000000000000000000;
          character.third   = 0b00000000000000000000000000000000;
          character.fourth  = 0b00000000000000000000000000000000;
         break;
      default :
         printf("Invalid input \"%c\".\n", ch);
          character.first   = 0b00000000000000000000000000000000;
          character.second  = 0b00000000000000000000000000000000;
          character.third   = 0b00000000000000000000000000000000;
          character.fourth  = 0b00000000000000000000000000000000;
   }
   return character;
}