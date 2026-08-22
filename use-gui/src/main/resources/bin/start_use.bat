@echo off

REM Start script for Windows.
REM Copyright (c) 2001-2025

if "%OS%"=="Windows_NT" @setlocal

rem CONFIGURATION
rem Add -Xss20m to VMARGS when using the generator
set VMARGS=

set BIN_DIR=%~dp0
set USE_HOME=%BIN_DIR%..
set USE_JAR="%USE_HOME%\lib\use-gui.jar"
set JAVAFX_LIB="%USE_HOME%\lib\javafx-sdk-21.0.5\lib"
REM Native SAT solvers for the Kodkod Model Validator plugin (see
REM kk-modelvalidator/vendored-solvers/README.md). Only Linux x64 binaries are vendored
REM today (lib\plugins\modelValidatorPlugin\x64) -- this flag is harmless if that
REM directory has no Windows-usable libraries yet; DefaultSAT4J/LightSAT4J (pure Java)
REM still work regardless.
set SOLVER_LIB_DIR="%USE_HOME%\lib\plugins\modelValidatorPlugin\x64"

IF NOT EXIST %USE_JAR% (
	echo Cannot find USE executable. Please provide correct path to use.jar.
	goto end
)

REM Check if first argument is "jfx" (/I makes the comparison case-insensitive)
IF /I "%1"=="-jfx" (
    java %VMARGS% -Djava.library.path=%SOLVER_LIB_DIR% --module-path %JAVAFX_LIB% --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.graphics,javafx.swing --add-opens javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED --add-exports javafx.base/com.sun.javafx.event=ALL-UNNAMED -jar %USE_JAR% -nr %*
) ELSE (
    java %VMARGS% -Djava.library.path=%SOLVER_LIB_DIR% -jar %USE_JAR% -nr %*
)

if "%OS%"=="Windows_NT" @endlocal

:mainEnd
rem echo exit code:  %ERRORLEVEL%

:end
