GENV=../SetChip.v

baz.o: baz.s
	clang -c $< -o $@ -march=rv32i
	llvm-mc $< --filetype=obj -o $@

baz.elf: baz.o
	ld.lld -T linker.ld $^ -o $@ libclang_rt.builtins-riscv32.a

baz.hex: baz.elf
	llvm-objcopy -O ihex $< $@

$(GENV): baz.hex
	cd ..; sbt "runMain set.Gen"
