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

    top->eval();
    top->io_asyncReset = 1;
    for (int i = 0; i < 100; i++) {
        top->io_mainClk ^= 1;
        top->eval();
    }
    top->io_asyncReset = 0;

    while (sim_time < MAX_SIM_TIME) {
        top->io_mainClk ^= 1;
        top->eval();
        trace->dump(sim_time);
        sim_time++;
    }

    trace->close();
    delete top;

    return 0;
}
