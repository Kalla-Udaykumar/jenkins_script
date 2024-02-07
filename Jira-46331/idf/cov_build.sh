#!/bin/bash -xe
                        
  cd ${WORKSPACE}/abi/gvp_obs_cov && \
  ls -lrt && \
  cmake --version && \
  mkdir build && \
  cd build && \
  cmake .. && \
  make
