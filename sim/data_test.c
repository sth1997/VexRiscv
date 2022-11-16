#include "bcsr.h"
#include "data.h"
#include "setutil.h"
#include <stdbool.h>
#include <stdio.h>

uint32_t buf1[100];
uint32_t buf2[100];

void print_set(const char *name, uint32_t *set, uint32_t len) {
    printf("%s Len: %d\n", name, len);
    for (int i = 0; i < len; i++) {
        printf("%d, ", set[i]);
    }
    printf("\n");
}

void print_bcsr(const char *name, uint32_t *set, uint32_t len) {
    print_set(name, set, len);
    uint32_t bcsr_len = bcsr_convert_to(set, len, 8, buf1);
    print_set("BCSR", buf1, bcsr_len);
    uint32_t back_len = bcsr_convert_from(buf1, bcsr_len, 8, buf2);
    if (!set_equal(set, len, buf2, back_len)) {
        printf("CONVERT FAILED\n");
    }
}

int main() {
    print_bcsr("Set3", set_3, set_3_len);
    print_bcsr("Set4", set_4, set_4_len);
    print_bcsr("Set34Inter", set_34_inter, set_34_inter_len);
    print_bcsr("Set34Diff", set_34_diff, set_34_diff_len);
}
