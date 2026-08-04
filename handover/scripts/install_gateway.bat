@echo off
setlocal EnableExtensions EnableDelayedExpansion

if "%~1"=="" goto usage
if not "%~3"=="" goto usage

for %%I in ("%~1") do set "APK_DIR=%%~fI"
set "DEVICE_SERIAL=%~2"
set "ADB_TARGET="
if defined DEVICE_SERIAL set "ADB_TARGET=-s !DEVICE_SERIAL!"

call "%~dp0common.bat" resolve adb ADB
if errorlevel 1 exit /b 1

call :require_apk "com.somewearlabs.swtak.plugin.apk" || exit /b 1
call :require_apk "config.arm64_v8a.apk" || exit /b 1
call :require_apk "config.en.apk" || exit /b 1
call :require_apk "config.fr.apk" || exit /b 1
call :require_apk "config.mdpi.apk" || exit /b 1

echo Installing the complete gateway split set. Existing packages are not uninstalled automatically.
call "!ADB!" !ADB_TARGET! install-multiple -r ^
  "!APK_DIR!\com.somewearlabs.swtak.plugin.apk" ^
  "!APK_DIR!\config.arm64_v8a.apk" ^
  "!APK_DIR!\config.en.apk" ^
  "!APK_DIR!\config.fr.apk" ^
  "!APK_DIR!\config.mdpi.apk"
if errorlevel 1 exit /b 1

call "!ADB!" !ADB_TARGET! shell pm grant com.somewearlabs.swtak.plugin android.permission.BLUETOOTH_SCAN >nul 2>&1
call "!ADB!" !ADB_TARGET! shell pm grant com.somewearlabs.swtak.plugin android.permission.BLUETOOTH_CONNECT >nul 2>&1
call "!ADB!" !ADB_TARGET! shell pm grant com.somewearlabs.swtak.plugin android.permission.BLUETOOTH_ADVERTISE >nul 2>&1
call "!ADB!" !ADB_TARGET! shell pm grant com.somewearlabs.swtak.plugin android.permission.ACCESS_FINE_LOCATION >nul 2>&1

call "!ADB!" !ADB_TARGET! shell pm path com.somewearlabs.swtak.plugin | findstr /b /c:"package:" >nul
if errorlevel 1 (
  >&2 echo error: gateway package was not found after installation
  exit /b 1
)

echo package=com.somewearlabs.swtak.plugin
echo installation=OK
echo Now install SC3 signed with the same certificate and call somewear.info^(^).
exit /b 0

:require_apk
if not exist "!APK_DIR!\%~1" (
  >&2 echo error: gateway split is missing: !APK_DIR!\%~1
  exit /b 1
)
exit /b 0

:usage
>&2 echo usage: %~nx0 APK_DIR [DEVICE_SERIAL]
exit /b 2
