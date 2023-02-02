#!/bin/sh
set -x

INSTALLATION_HOME="${1}"
WORK_DIR="${2}"

"${INSTALLATION_HOME}/bin/prospero.sh" update apply --dir="${INSTALLATION_HOME}" --update-dir="${WORK_DIR}/installation-manager/prepared-server"

IM_RET=$?

case $IM_RET in

  0) #  0   Successful program execution.
    echo "INFO: The Candidate Server was successfully applied."
    rm -rf "${WORK_DIR}/installation-manager"
    ;;

  1) #  1   Failed operation.
    echo "ERROR: The operation was unsuccessful. The candidate server was not installed correctly."
    ;;

  2) # 2 Invalid arguments were given.
    echo "ERROR: The Candidate Server installation failed. Invalid arguments were provided."
    ;;

  *)
    echo "ERROR: An unknown error occurred during the execution of the installation manager."
    ;;
esac


