// See LICENSE for license details.

#include "coh_checker.h"

int thread_entry(int cid, int nc) {
  init_or_wait(NULL);
  threadIdx = hartId;
  coh_checker_main();
  
  if(!hartId)
    printf("done at cycle %d\n",read_csr(mcycle));
  return 0;
}



