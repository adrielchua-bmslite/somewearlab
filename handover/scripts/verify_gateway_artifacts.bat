@echo off
setlocal EnableExtensions EnableDelayedExpansion

for %%I in ("%~dp0..\..") do set "REPO_ROOT=%%~fI"
set "TOOL_JAR=!REPO_ROOT!\handover\dist\somewear-handover-tools.jar"
if "%~1"=="" (
  set "APK_DIR=!REPO_ROOT!\build\signed-splits-v2"
) else (
  for %%I in ("%~1") do set "APK_DIR=%%~fI"
)
set "AAR=!REPO_ROOT!\somewear-gateway-sdk\dist\somewear-gateway-sdk-0.1.0.aar"

if not exist "!TOOL_JAR!" (
  >&2 echo error: portable handover tool is missing: !TOOL_JAR!
  exit /b 1
)

call "%~dp0common.bat" resolve-java JAVA_EXE
if errorlevel 1 exit /b 1

"!JAVA_EXE!" -jar "!TOOL_JAR!" verify "!REPO_ROOT!" "!APK_DIR!" "!AAR!"
exit /b !ERRORLEVEL!
