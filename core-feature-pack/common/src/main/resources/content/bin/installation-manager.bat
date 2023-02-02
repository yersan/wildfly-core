@echo off
:: Invokes the installation manager tool to apply the prepared server.

set INSTALLATION_HOME=%1
set WORK_DIR=%2

"%INSTALLATION_HOME%/bin/prospero.sh" update apply --dir="%INSTALLATION_HOME%" --update-dir="%WORK_DIR%/installation-manager/prepared-server"

if %errorlevel% equ 0 (
    echo INFO: The Candidate Server was successfully applied.
    del /F /Q "%WORK_DIR%/installation-manager/prepared-server"

    exit /B 0
)
if %errorlevel% equ 1 (
    echo ERROR: The operation was unsuccessful. The candidate server was not installed correctly.

    exit /B 1
)
if %errorlevel% equ 2 (
    echo ERROR: The Candidate Server installation failed. Invalid arguments were provided.

    exit /B 2
)

echo ERROR: An unknown error occurred during the execution of the installation manager.

exit /B 3

