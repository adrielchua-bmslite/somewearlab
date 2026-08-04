@echo off
setlocal EnableExtensions EnableDelayedExpansion

if "%~4"=="" goto usage
if not "%~5"=="" goto usage

for %%I in ("%~1") do set "INPUT_DIR=%%~fI"
for %%I in ("%~2") do set "OUTPUT_DIR=%%~fI"
for %%I in ("%~3") do set "KEYSTORE=%%~fI"
set "KEY_ALIAS=%~4"
for %%I in ("%~dp0..\..") do set "REPO_ROOT=%%~fI"
set "TOOL_JAR=!REPO_ROOT!\handover\dist\somewear-handover-tools.jar"

if not exist "!TOOL_JAR!" (
  >&2 echo error: portable handover tool is missing: !TOOL_JAR!
  exit /b 1
)

call "%~dp0common.bat" resolve-java JAVA_EXE
if errorlevel 1 exit /b 1

"!JAVA_EXE!" -jar "!TOOL_JAR!" resign "!INPUT_DIR!" "!OUTPUT_DIR!" "!KEYSTORE!" "!KEY_ALIAS!"
exit /b !ERRORLEVEL!

:usage
>&2 echo usage: %~nx0 INPUT_APK_DIR OUTPUT_APK_DIR KEYSTORE KEY_ALIAS
exit /b 2
