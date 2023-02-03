@echo off
:: Invokes the installation manager tool to apply the prepared server.

set INSTALLATION_HOME=%~1
set WORK_DIR=%~2
set PREPARED_INSTALLATION=%WORK_DIR%\installation-manager\prepared-server

if not exist "%PREPARED_INSTALLATION%" (
	echo INFO: There is no a Candidate Server prepared.
	
	goto EOF
)

call "%INSTALLATION_HOME%\bin\prospero.bat" update apply --dir="%INSTALLATION_HOME%" --update-dir="%PREPARED_INSTALLATION%"

if %errorlevel% equ 0 (
    echo INFO: The Candidate Server was successfully applied.
    rmdir /S /Q "%PREPARED_INSTALLATION%"

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

:EOF

