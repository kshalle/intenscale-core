#!/bin/bash

mv BSMBench/Make/MkFlags BSMBench/Make/MkFlags.old
mv BSMBench/Bench/bsmbench BSMBench/Bench/bsmbench_old

mv bsmbench_balance bsmbench_balance.old
mv bsmbench_comms bsmbench_comms.old
mv bsmbench_compute bsmbench_compute.old
mv bsmbench_balance_1cpu bsmbench_balance_1cpu.old
mv bsmbench_comms_1cpu bsmbench_comms_1cpu.old
mv bsmbench_compute_1cpu bsmbench_compute_1cpu.old

if [ -z "$NO_MPI" ]
then
cp $1 BSMBench/Make/MkFlags

echo "" >> BSMBench/Make/MkFlags
echo "##########################################################################" >> BSMBench/Make/MkFlags
echo "# DO NOT EDIT BEYOND THIS POINT" >> BSMBench/Make/MkFlags
echo "##########################################################################" >> BSMBench/Make/MkFlags
echo "" >> BSMBench/Make/MkFlags

echo NG = 2 >> BSMBench/Make/MkFlags
echo REPR = REPR_ADJOINT >> BSMBench/Make/MkFlags
echo "MACRO += -DWITH_MPI" >> BSMBench/Make/MkFlags

cat BSMBench/Make/MkFlags.proto >> BSMBench/Make/MkFlags

cd BSMBench
make clean
cd Bench
make
cp bsmbench ../../bsmbench_comms

cd ../..

mv BSMBench/Make/MkFlags BSMBench/Make/MkFlags.commsp.old
mv BSMBench/Bench/bsmbench BSMBench/Bench/bsmbench_old


fi

if [ "$2" ]
then
cp $2 BSMBench/Make/MkFlags

echo "" >> BSMBench/Make/MkFlags
echo "##########################################################################" >> BSMBench/Make/MkFlags
echo "# DO NOT EDIT BEYOND THIS POINT" >> BSMBench/Make/MkFlags
echo "##########################################################################" >> BSMBench/Make/MkFlags
echo "" >> BSMBench/Make/MkFlags

echo NG = 2 >> BSMBench/Make/MkFlags
echo REPR = REPR_ADJOINT >> BSMBench/Make/MkFlags
echo "#MACRO += -DWITH_MPI" >> BSMBench/Make/MkFlags

cat BSMBench/Make/MkFlags.proto >> BSMBench/Make/MkFlags

cd BSMBench
make clean
cd Bench
make
cp bsmbench ../../bsmbench_comms_1cpu

cd ../..

mv BSMBench/Make/MkFlags BSMBench/Make/MkFlags.comms1.old
mv BSMBench/Bench/bsmbench BSMBench/Bench/bsmbench_old

fi

if [ -z "$NO_MPI" ]
then
cp $1 BSMBench/Make/MkFlags

echo "" >> BSMBench/Make/MkFlags
echo "##########################################################################" >> BSMBench/Make/MkFlags
echo "# DO NOT EDIT BEYOND THIS POINT" >> BSMBench/Make/MkFlags
echo "##########################################################################" >> BSMBench/Make/MkFlags
echo "" >> BSMBench/Make/MkFlags

echo NG = 3 >> BSMBench/Make/MkFlags
echo REPR = REPR_FUNDAMENTAL >> BSMBench/Make/MkFlags
echo "MACRO += -DWITH_MPI" >> BSMBench/Make/MkFlags

cat BSMBench/Make/MkFlags.proto >> BSMBench/Make/MkFlags

cd BSMBench
make clean
cd Bench
make
cp bsmbench ../../bsmbench_balance

cd ../..

mv BSMBench/Make/MkFlags BSMBench/Make/MkFlags.balancep.old
mv BSMBench/Bench/bsmbench BSMBench/Bench/bsmbench_old
fi

if [ "$2" ]
then
cp $2 BSMBench/Make/MkFlags

echo "" >> BSMBench/Make/MkFlags
echo "##########################################################################" >> BSMBench/Make/MkFlags
echo "# DO NOT EDIT BEYOND THIS POINT" >> BSMBench/Make/MkFlags
echo "##########################################################################" >> BSMBench/Make/MkFlags
echo "" >> BSMBench/Make/MkFlags

echo NG = 3 >> BSMBench/Make/MkFlags
echo REPR = REPR_FUNDAMENTAL >> BSMBench/Make/MkFlags
echo "#MACRO += -DWITH_MPI" >> BSMBench/Make/MkFlags

cat BSMBench/Make/MkFlags.proto >> BSMBench/Make/MkFlags

cd BSMBench
make clean
cd Bench
make
cp bsmbench ../../bsmbench_balance_1cpu

cd ../..

mv BSMBench/Make/MkFlags BSMBench/Make/MkFlags.balance1.old
mv BSMBench/Bench/bsmbench BSMBench/Bench/bsmbench_old
fi

if [ -z "$NO_MPI" ]
then
cp $1 BSMBench/Make/MkFlags

echo "" >> BSMBench/Make/MkFlags
echo "##########################################################################" >> BSMBench/Make/MkFlags
echo "# DO NOT EDIT BEYOND THIS POINT" >> BSMBench/Make/MkFlags
echo "##########################################################################" >> BSMBench/Make/MkFlags
echo "" >> BSMBench/Make/MkFlags

echo NG = 6 >> BSMBench/Make/MkFlags
echo REPR = REPR_FUNDAMENTAL >> BSMBench/Make/MkFlags
echo "MACRO += -DWITH_MPI" >> BSMBench/Make/MkFlags


cat BSMBench/Make/MkFlags.proto >> BSMBench/Make/MkFlags

cd BSMBench
make clean
cd Bench
make
cp bsmbench ../../bsmbench_compute

cd ../..

mv BSMBench/Make/MkFlags BSMBench/Make/MkFlags.computep.old
mv BSMBench/Bench/bsmbench BSMBench/Bench/bsmbench_old
fi

if [ "$2" ]
then
cp $2 BSMBench/Make/MkFlags

echo "" >> BSMBench/Make/MkFlags
echo "##########################################################################" >> BSMBench/Make/MkFlags
echo "# DO NOT EDIT BEYOND THIS POINT" >> BSMBench/Make/MkFlags
echo "##########################################################################" >> BSMBench/Make/MkFlags
echo "" >> BSMBench/Make/MkFlags

echo NG = 6 >> BSMBench/Make/MkFlags
echo REPR = REPR_FUNDAMENTAL >> BSMBench/Make/MkFlags
echo "#MACRO += -DWITH_MPI" >> BSMBench/Make/MkFlags

cat BSMBench/Make/MkFlags.proto >> BSMBench/Make/MkFlags

cd BSMBench
make clean
cd Bench
make
cp bsmbench ../../bsmbench_compute_1cpu

cd ../..

mv BSMBench/Make/MkFlags BSMBench/Make/MkFlags.compute1.old
mv BSMBench/Bench/bsmbench BSMBench/Bench/bsmbench_old
fi