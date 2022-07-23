// graph
int G[5][5] = {0};
int tmp[10] = {0};

void clear() {
  for (int i = 0; i < 10; i++) {
    tmp[i] = 0;
  }
}

int main() {
  G[0][0] = 1; G[0][1] = 4; G[0][2] = -1;
  G[1][0] = 0; G[1][1] = 2; G[1][2] = 3; G[1][3] = 4; G[1][4] = -1;
  G[2][0] = 1; G[2][1] = 3; G[2][2] = 4; G[2][3] = -1;
  G[3][0] = 1; G[3][1] = 2; G[3][2] = 4; G[3][3] = -1;
  G[4][0] = 0; G[4][1] = 1; G[4][2] = 2; G[4][3] = 3; G[4][4] = -1;

  int cnt = 0;

  for (int u = 0; u <= 4; u++) {
    int Nu = __builtin_set_count(G[u]);
    for (int i = 0; i < Nu; i++) {
	  int v = G[u][i];
	  clear();
	  __builtin_set_inter(tmp, G[u], G[v]);
	  cnt += __builtin_set_count(tmp);
    }
  }

  return cnt;
}

void irqCallback() {}
