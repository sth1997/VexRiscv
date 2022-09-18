#include <stdint.h>

extern void _bss_start();
extern void _bss_end();
extern void _fw_start();
extern void _fw_end();

volatile uint8_t *const SERIAL_OUTPUT = (void *) 0xF0000000ul;

void serial_putchar(const char c) {
  *SERIAL_OUTPUT = c;
}

void serial_putstr(const char *str) {
  for(const char *c = str; *c != '\0'; ++c) serial_putchar(*c);
}

void start() {
  // Clear bss
  for(uint32_t i = (uint32_t) _bss_start; i < (uint32_t) _bss_end; i += 4) *((uint32_t *) i) = 0;
  serial_putstr("Hello, world!");
}
