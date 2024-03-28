#!/bin/bash

#export PATH=${PATH}:/usr/local/anaconda/bin && mkdir ${WORKSPACE}/upload
cd ${WORKSPACE}/abi/mips
#export TENSORFLOW_CPU_LIB_PATH=/opt/intel/tf_lib
#export Torch_DIR=/opt/intel/ptlibs/libtorch/share/cmake/Torch
#export PYTORCH_CPU_LIB_PATH=/opt/intel/ptlibs/libtorch
#export TorchVision_DIR=/opt/intel/ptlibs/torchvision-install/share/cmake/TorchVision
#export TORCHVISION_CPU_LIB_PATH=/opt/intel/ptlibs/torchvision-install
#export OpenCV_INSTALL_DIR=/opt/intel/openvino_2022/opencv/build/install
export OpenCV_DIR=/opt/intel/openvino_2022/opencv/build/install/cmake
#export LD_LIBRARY_PATH=/opt/intel/openvino_2022/opencv/build/install/lib
#export PYTHONPATH=/opt/intel/openvino_2022/opencv/build/install/python
source /opt/intel/openvino_2022/setupvars.sh
source /opt/intel/oneapi/setvars.sh
mkdir build
cd build
cmake .. -G "Unix Makefiles" -DCMAKE_BUILD_TYPE=Release -DBUILD_TEST=1 -DCMAKE_INSTALL_PREFIX=${WORKSPACE}/abi/mips-binary
make -j\$(nproc)
make install
