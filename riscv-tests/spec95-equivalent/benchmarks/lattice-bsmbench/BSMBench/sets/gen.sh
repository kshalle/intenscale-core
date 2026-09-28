#!/bin/bash

cp template.bsmbench balance-$1.bsmbench
cp template.bsmbench comms-$1.bsmbench
cp template.bsmbench compute-$1.bsmbench

sed -i "s/NPT/$2/g;s/NPX/$3/g;s/NPY/$4/g;s/NPZ/$5/g;s/NFIELDS/$6/g;s/BGFLOPS/31900000000.0/g;s/DFLOPS/1603071681.0/g;s/SFLOPS/102763008.0/g;s/MFLOPS/100663296.0/g;s/MASS/-0.835/g;s/SET/$7/g" balance-$1.bsmbench
sed -i "s/NPT/$2/g;s/NPX/$3/g;s/NPY/$4/g;s/NPZ/$5/g;s/NFIELDS/$6/g;s/BGFLOPS/28450000000.0/g;s/DFLOPS/861500864.5/g;s/SFLOPS/102763008.0/g;s/MFLOPS/100663296.0/g;s/MASS/-1.05/g;s/SET/$7/g" comms-$1.bsmbench
sed -i "s/NPT/$2/g;s/NPX/$3/g;s/NPY/$4/g;s/NPZ/$5/g;s/NFIELDS/$6/g;s/BGFLOPS/43320000000.0/g;s/DFLOPS/5394683970.0/g;s/SFLOPS/203426304.0/g;s/MFLOPS/201326592.0/g;s/MASS/-0.8183/g;s/SET/$7/g" compute-$1.bsmbench
