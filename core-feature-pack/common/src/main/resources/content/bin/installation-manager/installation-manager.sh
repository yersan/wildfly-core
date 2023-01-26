#!/bin/sh
set -x

JBOSS_HOME="${1}"
WORK_DIR="${2}"

source $JBOSS_HOME/bin/installation-manager/installation-manager-lib.sh

# @TODO log integration
run_installation_manager
IM_RET=$?

case $IM_RET in

  0) #  0   Successful program execution.
    echo "Candidate Server was successfully applied."
    rm -rf $WORK_DIR/installation-manager
    ;;

  1) #  1   Failed operation.
    echo "Operation failed. The Candidate Server was not applied correctly."
    ;;

  2) # 2 Invalid arguments were given.
    echo "Cannot apply Candidate Server. Invalid arguments were given"
    ;;

  *)
    echo "Unknown Exit code"
    ;;
esac


