#!/bin/bash -xe
 
cd ${WORKSPACE}/abi/ubuntu_kernel
wget http://oak-07.jf.intel.com/ikt_kernel_deb_repo/pool/main/l/linux-6.5-rt5-mainline-tracking-rt-231025t060334z/kernel.config
#cp -r ${WORKSPACE}/kernel.config ${WORKSPACE}/abi/ubuntu_kernel/
mv kernel.config .config
scripts/config --disable DEBUG_INFO
make -j96 bindeb-pkg
