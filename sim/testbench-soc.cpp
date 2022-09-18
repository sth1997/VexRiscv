#include <iostream>
#include <queue>
#include <optional>
#include <iomanip>
#include <random>
#include <fstream>
#include <iterator>

#include "VSetChip.h"
#include "verilated.h"
#include "verilated_vcd_c.h"

#define MAX_SIM_TIME 10000
#define RESET_TIME 10

vluint64_t sim_time = 0;

VSetChip *top = nullptr;

const uint32_t RAM_BASE = 0x80000000ul;

const int RND_SEED = 19260817;
std::mt19937 gen(RND_SEED);
std::uniform_int_distribution<uint8_t> rnd_dist;

class MemBus {
  std::unordered_map<uint32_t, uint8_t> memory;

public:
  MemBus() {}
  void init(std::vector<char> payload) {
    for(size_t i = 0; i < payload.size(); ++i)
      memory[RAM_BASE + i] = payload[i];
  }

  void write(uint32_t addr, uint32_t data, uint8_t we) {
    if(addr == 0xf0000000ul) { // Serial write
      char c = data & 0xFF;
      putchar(c);
    } else if(addr >= 0x80000000ul && addr < 0x80400000ul) {
      for(size_t i = 0; i < 4; ++i) {
        if(((we >> i) & 1) == 0) continue;
        memory[i + addr] = (data >> (i * 8)) & 0xFF;
      }
    }
  }

  uint32_t read(uint32_t addr) {
    if(addr >= 0x80000000ul && addr < 0x80400000ul) {
      uint32_t collected = 0;
      for(size_t i = 0; i < 4; ++i) {
        auto lookup = memory.find(addr + i);
        uint8_t readout;
        if(lookup == memory.end()) {
          readout = rnd_dist(gen);
          memory[addr + i] = readout;
        } else readout = lookup->second;

        collected |= ((uint32_t) readout) << (i * 8);
      }
      return collected;
    }

    return 0;
  }
};

int main(int argc, char *argv[]) {
  if(argc > 2) {
    std::cout<<"Usage: setchip-exec [mem-init]"<<std::endl;
    return 1;
  }

  MemBus mem;
  if(argc == 2) {
    std::ifstream file(argv[1], std::ios::binary);
    if(!file) {
      std::cout<<"Bad file: "<<argv[1]<<std::endl;
      return 1;
    }
    std::vector<char> init = std::vector<char>(
      std::istreambuf_iterator<char>(file),
      std::istreambuf_iterator<char>()
    );
    mem.init(init);
  }

  Verilated::commandArgs(argc, argv);

  Verilated::traceEverOn(true);
  VerilatedVcdC *trace = new VerilatedVcdC;

  top = new VSetChip;

  top->trace(trace, 5);
  trace->open("waveform.vcd");

  top->eval();

  // We're always ready on cmd
  top->io_mem_cmd_ready = true;

  std::optional<uint32_t> read_resp = {};

  while (sim_time < MAX_SIM_TIME) {
    top->clk ^= 1;
    top->reset = sim_time < RESET_TIME ? 1 : 0;
    top->eval();

    if(top->clk == 0) { // Negedge, handle requests
      read_resp = {};
      if(top->io_mem_cmd_valid) {
        if(top->io_mem_cmd_payload_write)
          mem.write(top->io_mem_cmd_payload_address, top->io_mem_cmd_payload_data, top->io_mem_cmd_payload_mask);
        else
          read_resp = { mem.read(top->io_mem_cmd_payload_address) };
      }
    } else { // Posedge, write back data
      top->io_mem_rsp_valid = read_resp.has_value();
      top->io_mem_rsp_payload_data = read_resp.value_or(0);
    }

    top->eval();

    trace->dump(sim_time);

    sim_time++;
  }

  trace->close();
  delete top;

  return 0;
}
