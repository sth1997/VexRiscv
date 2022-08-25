BUILD_DIR=./build
TOP=SetChip
TOP_V=$(BUILD_DIR)/$(TOP).v

SRC_SCALA=$(shell find ./src/main/scala -name '*.scala')

verilog: $(TOP_V)

	mkdir -p $(@D)
$(TOP_V): $(SRC_SCALA)

