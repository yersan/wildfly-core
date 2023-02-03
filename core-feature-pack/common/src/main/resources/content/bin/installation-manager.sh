#!/bin/sh
set -x

INSTALLATION_HOME="${1}"
WORK_DIR="${2}"
PREPARED_INSTALLATION="${WORK_DIR}/installation-manager/prepared-server"

if ! [ -d "${PREPARED_INSTALLATION}" ] || ! [ -n "$(ls -A "${PREPARED_INSTALLATION}")" ]; then
  echo "INFO: There is no a Candidate Server prepared."
  exit
fi

propsFile="${INSTALLATION_HOME}/bin/installation-manager.properties"
if ! [ -e "${propsFile}" ]; then
  echo "INFO: Installation Manager properties file not found at ${propsFile}."
  exit
fi

while IFS='=' read -r key value; do
   export "$key=$value"
done < "$propsFile"

"${INSTALLATION_HOME}/bin/prospero.sh" ${INST_MGR_ACTION} apply --dir="${INSTALLATION_HOME}" --update-dir="${WORK_DIR}/installation-manager/prepared-server"

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


