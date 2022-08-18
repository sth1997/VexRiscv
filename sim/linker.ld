ENTRY(_start)

MEMORY {
  ram : ORIGIN = 0x80000000, LENGTH = 8k
}

SECTIONS {
  .text : {
    *(.text)
  } > ram

  .data : {
    *(.data)
  } > ram

  .bss : {
    *(.bss)
  } > ram
}

