int a[] = {1, 2, 3};
int b[] = {2, 3, 4, 5};
int c[10] = {0};
int cnt = 0;

void func() {
  __builtin_set_load(0, a, 3);
  __builtin_set_load(1, b, 4);
  __builtin_set_load(2, c, 0);
  __builtin_set_diff(2, 1, 0);

/*
  int i = 0;
  int j = 0;
  int k = 0;
  while (1) {
    if (i == 3 || j == 4) {
      break;
    }
    if (a[i] == b[j]) {
      c[k] = a[i];
      i++; j++; k++;
    } else if (a[i] < b[j]) {
      i++;
    } else {
      j++;
    }
  }
  cnt = k;
*/
}
