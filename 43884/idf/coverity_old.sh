#!/bin/bash

cd ${WORKSPACE}/abi/ese_linux_next_next_cov
bzip2 -d mender-initramfs-intel-corei7-64-20230411060513.rootfs.cpio.bz2 
mv mender-initramfs-intel-corei7-64-20230411060513.rootfs.cpio mender.cpio
mv config_kernel .config
sed -i -e '/CONFIG_EXTRA_FIRMWARE/d' .config
sed -i -e '/CONFIG_EXTRA_FIRMWARE_DIR/d' .config
sed -i -e 's#CONFIG_INITRAMFS_SOURCE=.*#CONFIG_INITRAMFS_SOURCE="./mender.cpio"#' .config
sed -i -e '/CONFIG_MODULE_COMPRESS/d' .config
echo "CONFIG_MODULE_COMPRESS_NONE=y" >> .config
bash -c "echo "" | make tarxz-pkg LOCALVERSION= -j8"

# Jatin drivers build step check
#make M=drivers/net/ethernet/intel/igc
