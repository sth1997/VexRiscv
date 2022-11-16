#include "bcsr.h"
#include "data.h"
#include "print.h"
#include "uart.h"
#include <stdbool.h>

#define UART ((Uart_Reg *)(0xF0000000))

Uart_Config config = {
    .clockDivider = 54, .dataLength = 8, .parity = NONE, .stop = ONE};

char buf[20];

void put_char(char c) { uart_write(UART, c); }

void setup() { uart_applyConfig(UART, &config); }

void print_number(uint32_t num) {
    print(itoa(num, buf, 10));
    put_char(' ');
}

bool set_equal(uint32_t *set1, uint32_t set1_len, uint32_t *set2,
               uint32_t set2_len) {
    if (set1_len != set2_len) {
        print("set len is not equal ");
        print_number(set1_len);
        print_number(set2_len);
        println("");
        return false;
    }
    for (int i = 0; i < set1_len; i++)
        if (set1[i] != set2[i]) {
            print("set ele is not equal");
            print_number(i);
            print_number(set1[i]);
            print_number(set2[i]);
            println("");
            return false;
        }
    return true;
}

void print_set(const char *set_name, uint32_t set_id, uint32_t *set) {
    print(set_name);
    print(" Len: ");
    int len = __builtin_set_count(set_id);
    print_number(len);
    print("Elements: ");
    for (int i = 0; i < len; i++) {
        print_number(set[i]);
    }
    println("");
}

uint32_t set_out[100];

int main() {
    setup();

    __builtin_set_load(0, set_3_bcsr8, set_3_bcsr8_len);
    __builtin_set_load(1, set_4_bcsr8, set_4_bcsr8_len);
    __builtin_set_load(2, set_out, 0);

    __builtin_set_diff(2, 0, 1);
    print("DIFF ");
    if (set_equal(set_34_diff_bcsr8, set_34_diff_bcsr8_len, set_out,
                  __builtin_set_count(2))) {
        println("Passed");
    } else {
        println("Failed");
    }

    __builtin_set_inter(2, 0, 1);
    print("INTER ");
    if (set_equal(set_34_inter_bcsr8, set_34_inter_bcsr8_len, set_out,
                  __builtin_set_count(2))) {
        println("Passed");
    } else {
        println("Failed");
    }
}

void irqCallback() {}