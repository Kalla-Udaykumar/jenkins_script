#!/bin/bash
export REPORTS_DIR=${WORKSPACE}/abi/upload
cd  ${WORKSPACE}/abi
mkdir upload
#cd ${WORKSPACE}/abi/depends
cd ${WORKSPACE}/abi/ese_linux_next_next_cov
bzip2 -d mender-initramfs-intel-corei7-64-20230411060513.rootfs.cpio.bz2 
mv mender-initramfs-intel-corei7-64-20230411060513.rootfs.cpio mender.cpio
#cp -r "linux-intel-iot-lts-6.1-kernelsrc.config" ${WORKSPACE}/abi/ese_linux_next_next 
#cp -r "mender.cpio"  ${WORKSPACE}/abi/ese_linux_next_next
#cd ${WORKSPACE}/abi/ese_linux_next_next
mv linux-intel-iot-lts-6.1-kernelsrc.config .config
sed -i -e '/CONFIG_EXTRA_FIRMWARE/d' .config
sed -i -e '/CONFIG_EXTRA_FIRMWARE_DIR/d' .config
sed -i -e 's#CONFIG_INITRAMFS_SOURCE=.*#CONFIG_INITRAMFS_SOURCE="./mender.cpio"#' .config
sed -i -e '/CONFIG_MODULE_COMPRESS/d' .config
echo "CONFIG_MODULE_COMPRESS_NONE=y" >> .config
bash -c "echo "" | make tarxz-pkg LOCALVERSION= -j8"

cp -r ${WORKSPACE}/abi/ese_linux_next_next_cov/arch/x86/boot/bzImage ${REPORTS_DIR}
cp -r ${WORKSPACE}/abi/ese_linux_next_next_cov/*.tar.xz ${REPORTS_DIR}
