#include <iostream>
#include <queue>

#include "VSetChip.h"
#include "verilated.h"
#include "verilated_vcd_c.h"

#define MAX_SIM_TIME 10000
vluint64_t sim_time = 0;

VSetChip *top = nullptr;

int main(int argc, char *argv[]) {
  Verilated::commandArgs(argc, argv);

  Verilated::traceEverOn(true);
  VerilatedVcdC *trace = new VerilatedVcdC;

  top = new VSetChip;

  top->trace(trace, 5);
  trace->open("waveform.vcd");

  // std::uint32_t insns[] = {
  //     // 0x93655500,
  //     // 0x00556593,
  //     // 0x9365c500,
  //     // 0x13662500,
  //     // 0x9366a500,
  //     // 0x1367f500,
  //     0x01458593,
  //     0x06460613,
  //     0x00b5760b,
  //     0x00556593,
  //     0x00000013,
  // };
  // std::uint32_t data[200] = {0};
  // data[0] = 1;
  // data[1] = 2;
  // data[2] = 3;
  // data[3] = -1;
  // data[20] = 2;
  // data[21] = 4;
  // data[22] = 5;
  // data[23] = -1;

  // std::queue<std::uint32_t> next_insns;
  // std::queue<std::uint32_t> next_data;

  top->eval();
  top->clk ^= 1;
  top->reset = 1;
  top->eval();
  top->reset = 0;

  while (sim_time < MAX_SIM_TIME) {
    // if (sim_time == 10) {
    //   top->reset = 1;
    // } else {
    //   top->reset = 0;
    // }
    top->clk ^= 1;
    // top->clk ^= 1;
    top->eval();
    trace->dump(sim_time);
    sim_time++;

    // if (!next_insns.empty()) {
    //   top->iBus_rsp_payload_inst = next_insns.front();
    //   top->iBus_rsp_valid = true;
    //   top->iBus_cmd_ready = true;
    //   next_insns.pop();
    // } else {
    //   top->iBus_rsp_valid = false;
    //   top->iBus_cmd_ready = false;
    // }

    // if (!next_data.empty()) {
    //   top->dBus_rsp_data = next_data.front();
    //   top->dBus_rsp_ready = true;
    //   top->dBus_cmd_ready = true;
    //   next_data.pop();
    // } else {
    //   top->dBus_rsp_ready = false;
    //   top->dBus_cmd_ready = false;
    // }

    // if (top->iBus_cmd_valid) {
    //   // std::cout << "inst: " << top->iBus_cmd_payload_pc << std::endl;
    //   if (top->iBus_cmd_payload_pc >= sizeof(insns)) {
    //     // top->iBus_rsp_payload_inst = 0x00000013;
    //     next_insns.push(0x00000013);
    //   } else {
    //     // top->iBus_rsp_payload_inst = insns[top->iBus_cmd_payload_pc/4];
    //     next_insns.push(insns[top->iBus_cmd_payload_pc / 4]);
    //   }
    //   // top->iBus_rsp_valid = true;
    //   // top->iBus_cmd_ready = true;
    // }

    // if (top->dBus_cmd_valid) {
    //   // std::cout << "data: " << top->dBus_cmd_payload_address << std::endl;
    //   if (top->dBus_cmd_payload_wr) {
    //     // write
    //     data[top->dBus_cmd_payload_address / 4] = top->dBus_cmd_payload_data;
    //     top->dBus_cmd_ready = true;
    //     top->dBus_rsp_ready = true;
    //   } else {
    //     // read
    //     if (top->dBus_cmd_payload_address >= sizeof(data)) {
    //       next_data.push(0);
    //     } else {
    //       next_data.push(data[top->dBus_cmd_payload_address / 4]);
    //     }
    //   }
    // }
  }

  trace->close();
  delete top;

  // for (int i = 0; i <= 110; i++) {
  //   std::cout << data[i] << std::endl;
  // }

  return 0;
}