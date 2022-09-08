int a[] = {1, 2, 3};
int b[] = {2, 3, 4, 5};
int c[10] = {0};
int cnt = 0;

int main() {
  __builtin_set_load(0, a, 3);
  __builtin_set_load(1, b, 4);
  __builtin_set_load(2, c, 0);
  __builtin_set_diff(2, 1, 0);
}

void irqCallback() {}