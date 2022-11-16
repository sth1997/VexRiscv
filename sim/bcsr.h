#include <stdint.h>

static void bcsr_foreach(uint32_t *set, uint32_t len, uint32_t bitmap_len,
                         void (*func)(uint32_t)) {
    for (uint32_t i = 0; i < len; i++) {
        uint32_t value = set[i], index = value >> bitmap_len,
                 bitmap = value - (index << bitmap_len),
                 start = index * bitmap_len;
        for (uint32_t bit = 0; bit < bitmap_len; bit++) {
            if (bitmap & (1 << bit)) {
                func(start + bit);
            }
        }
    }
}

static uint32_t bcsr_convert_to(uint32_t *set, uint32_t len,
                                uint32_t bitmap_len, uint32_t *new_set) {
    uint32_t last_index = 0, last_bitmap = 0, new_count = 0;
    for (uint32_t i = 0; i < len; i++) {
        uint32_t value = set[i], index = value / bitmap_len,
                 offset = value % bitmap_len;
        if (index > last_index) {
            if (last_bitmap != 0) {
                new_set[new_count++] = (last_index << bitmap_len) | last_bitmap;
            }
            last_index = index;
            last_bitmap = 0;
        }
        last_bitmap |= 1 << offset;
    }
    if (last_bitmap != 0) {
        new_set[new_count++] = (last_index << bitmap_len) | last_bitmap;
    }
    return new_count;
}

static uint32_t bcsr_convert_from(uint32_t *set, uint32_t len,
                                  uint32_t bitmap_len, uint32_t *new_set) {
    uint32_t new_len = 0;
    for (uint32_t i = 0; i < len; i++) {
        uint32_t value = set[i], index = value >> bitmap_len,
                 bitmap = value - (index << bitmap_len),
                 start = index * bitmap_len;
        for (uint32_t bit = 0; bit < bitmap_len; bit++) {
            if (bitmap & (1 << bit)) {
                new_set[new_len++] = start + bit;
            }
        }
    }
    return new_len;
}
