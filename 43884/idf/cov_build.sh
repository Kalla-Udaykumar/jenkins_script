#!/bin/bash

cd ${WORKSPACE}/abi/ese_linux_next_next_cov
pwd
ls -la
#/drivers/net/ethernet/intel/igc
make M="drivers/net/ethernet/intel/igc"
# make
