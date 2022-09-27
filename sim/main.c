#include "uart.h"

int a[] = {1, 2, 3};
int b[] = {2, 3, 4, 5};
int c[10] = {0};
int cnt = 0;

#define UART      ((Uart_Reg*)(0xF0000000))

void print(const char*str){
	while(*str){
		uart_write(UART,*str);
		str++;
	}
}

void println(const char*str){
	print(str);
	uart_write(UART,'\n');
}

Uart_Config config = {
  .clockDivider = 54,
  .dataLength = 8,
  .parity = NONE,
  .stop = ONE
};

char* itoa(int value, char* result, int base) {
    char* ptr = result, *ptr1 = result, tmp_char;
    int tmp_value;

    do {
        tmp_value = value;
        value /= base;
        *ptr++ = "zyxwvutsrqponmlkjihgfedcba9876543210123456789abcdefghijklmnopqrstuvwxyz" [35 + (tmp_value - value * base)];
    } while ( value );

    // Apply negative sign
    if (tmp_value < 0) *ptr++ = '-';
    *ptr-- = '\0';
    while(ptr1 < ptr) {
        tmp_char = *ptr;
        *ptr--= *ptr1;
        *ptr1++ = tmp_char;
    }
    return result;
}

char buf[20];

int main() {
  uart_applyConfig(UART, &config);

  __builtin_set_load(0, a, 3);
  __builtin_set_load(1, b, 4);
  __builtin_set_load(2, c, 0);
  __builtin_set_diff(2, 1, 0);

  print("Diff count: ");
  int count = __builtin_set_count(2);
  println(itoa(count, buf, 10));
  for(int i = 0;i<count;i++){
    print(itoa(c[i], buf, 10));
    print(" ");
  }
  println("");
}

void irqCallback() {}