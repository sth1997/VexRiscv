#include <iostream>
#include <queue>
#include <optional>

#include "VSetChip.h"
#include "verilated.h"
#include "verilated_vcd_c.h"

#define MAX_SIM_TIME 10000
#define RESET_TIME 10

vluint64_t sim_time = 0;

VSetChip *top = nullptr;

class MemBus {
public:
  void write(uint32_t addr, uint32_t data, uint8_t we) {
    if(addr == 0xf0000000ul) { // Serial write
      char c = data & 0xFF;
      // TODO: buffer
      putchar(c);
    }
  }

  uint32_t read(uint32_t addr) {
    // Unimpl!
    return 0;
  }
};

int main(int argc, char *argv[]) {
  Verilated::commandArgs(argc, argv);

  Verilated::traceEverOn(true);
  VerilatedVcdC *trace = new VerilatedVcdC;

  top = new VSetChip;

  top->trace(trace, 5);
  trace->open("waveform.vcd");

  bool clk = false;
  top->eval();

  // We're always ready on cmd
  top->io_mem_cmd_ready = true;

  // Read response send back on next cycle
  std::optional<uint32_t> read_resp = {};

  MemBus mem;

  while (sim_time < MAX_SIM_TIME) {
    top->clk ^= 1;
    top->reset = sim_time < RESET_TIME ? 1 : 0;
    top->eval();
    trace->dump(sim_time);

    if(top->clk == 1) { // Posedge, handle requests
      if(top->io_mem_cmd_valid) {
        if(top->io_mem_cmd_payload_write)
          mem.write(top->io_mem_cmd_payload_address, top->io_mem_cmd_payload_data, top->io_mem_cmd_payload_mask);
        else
          read_resp = { mem.read(top->io_mem_cmd_payload_address) };
      }
    } else { // Negedge, update input
      top->io_mem_rsp_valid = read_resp.has_value();
      top->io_mem_rsp_payload_data = read_resp.value_or(0);
    }

    sim_time++;
  }

  trace->close();
  delete top;

  return 0;
}
