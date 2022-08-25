VERILATOR_FLAGS=-Wno-WIDTH

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

gensim: $(GENV)
	rm -f SetChip.v*.bin
	cp ../SetChip.v*.bin . | true
	verilator --trace -cc ../SetChip.v --exe testbench-soc.cpp $(VERILATOR_FLAGS)

compile: gensim
	make -C obj_dir -f VSetChip.mk -j 32

run: compile
	./obj_dir/VSetChip
