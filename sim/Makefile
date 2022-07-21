VERILATOR_FLAGS=-Wno-WIDTH

baz.o: baz.s
	llvm-mc $< --filetype=obj -o $@

baz.elf: baz.o
	ld.lld -T linker.ld $< -o $@

baz.hex: baz.elf
	llvm-objcopy -O ihex $< $@

genv: SetChip.v
	cd ..; sbt "runMain set.Gen"

gensim: genv
	rm -f SetChip.v*.bin
	cp ../SetChip.v*.bin . | true
	verilator --trace -cc ../SetChip.v testbench-soc.cpp $(VERILATOR_FLAGS)

compile: gensim
	make -C obj_dir -f VSetChip.mk

run: compile
	./obj_dir/VSetChip
