PREFIX=riscv64-elf-

.PHONY: clean

all: program.bin

program.elf: entry.S startup.c
	$(PREFIX)gcc -o program.elf -march=rv32g -mabi=ilp32 -nostartfiles -nostdlib -T linker.ld entry.S startup.c -O3

program.bin: program.elf
	$(PREFIX)objcopy -O binary program.elf program.bin

clean:
	rm -f program.bin program.elf
