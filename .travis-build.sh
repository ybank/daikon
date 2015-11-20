#!/bin/bash -v

# ROOT=$TRAVIS_BUILD_DIR
# cd $ROOT

export DAIKONDIR=`pwd`

# Same as in Jenkins; should abstract out
# make -C java very-clean
#make showvars compile daikon.jar javadoc
#make -C doc
#make -C java dcomp_rt.jar
#make -C tests all
#git clone https://github.com/codespecs/fjalar.git fjalar
make kvasir
#make -C tests/kvasir-tests nightly-summary-w-daikon

