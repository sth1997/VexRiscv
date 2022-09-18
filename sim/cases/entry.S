  .globl entry
  .section .text.entry
  .align 4
entry:  
  li sp, 0x80400000
  j start
