#!/bin/sh
# This script launches the operation to apply a candidate server installation to update or revert.
# The server JVM writes the required values into the installation-manager.properties file by using InstMgrCandidateStatus.java
set -x

INSTALLATION_HOME="${1}"
WORK_DIR="${2}"
PREPARED_INSTALLATION="${WORK_DIR}/installation-manager/prepared-server"

if ! [ -d "${PREPARED_INSTALLATION}" ] || ! [ -n "$(ls -A "${PREPARED_INSTALLATION}")" ]; then
  echo "INFO: There is no a Candidate Server prepared."
  exit
fi

PROPS_FILE="${INSTALLATION_HOME}/bin/installation-manager.properties"
if ! [ -e "${PROPS_FILE}" ]; then
  echo "INFO: Installation Manager properties file not found at ${PROPS_FILE}."
  exit
fi

while IFS='=' read -r key value; do
   [ "${key:0:1}" = "#" ] && continue
   export "${key}=${value}"
done < "$PROPS_FILE"

if ! [ "${INST_MGR_STATUS}" == "PREPARED" ]; then
  echo "ERROR: The Candidate Server installation is not in the PREPARED status. The current status is ${INST_MGR_STATUS}"
  exit
fi

"${INSTALLATION_HOME}/bin/${INST_MGR_SCRIPT_NAME}" ${INST_MGR_ACTION} --dir="${INSTALLATION_HOME}" --update-dir="${PREPARED_INSTALLATION}" --yes

IM_RET=$?

case $IM_RET in

  0 | 1) #  0   Successful program execution.
    echo "INFO: The Candidate Server was successfully applied."
    rm -rf "${WORK_DIR}/installation-manager"
    echo "INST_MGR_STATUS=CLEAN" > "${PROPS_FILE}"
    ;;

  -1) # -1 Invalid arguments were given.
    echo "ERROR: The Candidate Server installation failed. Invalid arguments were provided."
    ;;

  -2) #  -2   Failed operation.
    echo "ERROR: The operation was unsuccessful. The candidate server was not installed correctly."
    ;;

  *)
    echo "ERROR: An unknown error occurred during the execution of the installation manager."
    ;;
esac
