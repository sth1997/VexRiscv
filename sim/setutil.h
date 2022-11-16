#include <stdint.h>
#include <stdbool.h>

static bool set_equal(uint32_t *set1, uint32_t set1_len, uint32_t *set2,
               uint32_t set2_len) {
    if (set1_len != set2_len)
        return false;
    for (int i = 0; i < set1_len; i++)
        if (set1[i] != set2[i])
            return false;
    return true;
}