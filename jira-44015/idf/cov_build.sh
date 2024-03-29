#!/bin/bash

export PATH=${PATH}:/usr/local/anaconda/bin
# cd ${WORKSPACE}/mips
export TENSORFLOW_CPU_LIB_PATH=/opt/intel/tf_lib
export Torch_DIR=/opt/intel/ptlibs/libtorch/share/cmake/Torch
export PYTORCH_CPU_LIB_PATH=/opt/intel/ptlibs/libtorch
export TorchVision_DIR=/opt/intel/ptlibs/torchvision-install/share/cmake/TorchVision
export TORCHVISION_CPU_LIB_PATH=/opt/intel/ptlibs/torchvision-install
export OpenCV_INSTALL_DIR=/opt/intel/openvino_2022/opencv/build/install
export OpenCV_DIR=/opt/intel/openvino_2022/opencv/build/install/cmake
export LD_LIBRARY_PATH=/opt/intel/openvino_2022/opencv/build/install/lib
export PYTHONPATH=/opt/intel/openvino_2022/opencv/build/install/python
mkdir build
cd build
source /opt/intel/openvino_2022/setupvars.sh
source /opt/intel/oneapi/setvars.sh
cmake /build/cje/workspace/iotgdevops01/NON_PROD/UKALLA/44015-QA-Pipeline-2/mips/CMakeLists.txt -G "Unix Makefiles" -DCMAKE_BUILD_TYPE=Release -DBUILD_TEST=1 -DCMAKE_INSTALL_PREFIX=${WORKSPACE}/mips-binary
# make -j8
cd ${WORKSPACE}/mips/build/
ls -la | grep Makefile
make install
