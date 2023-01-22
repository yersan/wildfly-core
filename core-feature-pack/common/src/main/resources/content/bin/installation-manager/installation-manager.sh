#!/bin/sh
set -x

JBOSS_HOME="${1}"
WORK_DIR="${2}"

source $JBOSS_HOME/bin/installation-manager/installation-manager-lib.sh

run_installation_manager

rm -rf $WORK_DIR/installation-manager
