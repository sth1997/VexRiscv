SRCS = main.c crt.s

OBJS := $(SRCS)
OBJS := $(OBJS:.c=.o)
OBJS := $(OBJS:.cpp=.o)
OBJS := $(OBJS:.s=.o)

all: main.asm main.hex

%.o: %.c
	clang -c $< -o $@ -march=rv32i

%.o: %.s
	llvm-mc $< --filetype=obj -o $@

%.elf: $(OBJS)
	ld.lld -T linker.ld -o $@ $^ libclang_rt.builtins-riscv32.a

%.hex: %.elf
	llvm-objcopy -O ihex $^ $@

%.asm: %.elf
	llvm-objdump -S -d $^ > $@

.PHONY: clean
clean:
	rm -f *.o
	rm -f *.elf
	rm -f *.hex
	rm -f *.asm