@echo off
setlocal

rem This script launches the operation to apply a candidate server installation to update or revert.
rem The server JVM writes the required values into the installation-manager.properties file by using InstMgrCandidateStatus.java

set INSTALLATION_HOME=%~1
set WORK_DIR=%~2
set PREPARED_INSTALLATION=%WORK_DIR%\installation-manager\prepared-server

if not exist "%PREPARED_INSTALLATION%" (
    echo INFO: There is no a Candidate Server prepared.

    goto EOF
)

set PROPS_FILE=%INSTALLATION_HOME%\bin\installation-manager.properties
if not exist "%PROPS_FILE%" (
    echo INFO: Installation Manager properties file not found at %PROPS_FILE%.

    goto EOF
)

for /F "usebackq tokens=1* eol=# delims==" %%G IN ("%PROPS_FILE%") do (set %%G=%%H)

if "%INST_MGR_STATUS%" neq "PREPARED" (
    echo ERROR: The Candidate Server installation is not in the PREPARED status. The current status is %INST_MGR_STATUS%

    goto EOF
)

call "%INSTALLATION_HOME%\bin\%INST_MGR_SCRIPT_NAME%" %INST_MGR_ACTION% --dir="%INSTALLATION_HOME%" --update-dir="%PREPARED_INSTALLATION%"

set IM_RESULT=%errorlevel%

set "_tmp="
if %IM_RESULT% equ 0 set "_tmp=1"
if %IM_RESULT% equ 1 set "_tmp=1"

if _tmp equ 1 (
    echo INFO: The Candidate Server was successfully applied.
    rmdir /S /Q "%PREPARED_INSTALLATION%"
    echo INST_MGR_STATUS=CLEAN > %PROPS_FILE%
    goto EOF
)
if %IM_RESULT% equ -1 (
    echo ERROR: The Candidate Server installation failed. Invalid arguments were provided.
    goto EOF
)
if %IM_RESULT% equ -2 (
    echo ERROR: The operation was unsuccessful. The candidate server was not installed correctly.
    goto EOF
)

echo ERROR: An unknown error occurred during the execution of the installation manager.

:EOF

