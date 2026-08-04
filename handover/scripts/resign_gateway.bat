@echo off
setlocal EnableExtensions EnableDelayedExpansion

if "%~4"=="" goto usage
if not "%~5"=="" goto usage

for %%I in ("%~1") do set "INPUT_DIR=%%~fI"
for %%I in ("%~2") do set "OUTPUT_DIR=%%~fI"
for %%I in ("%~3") do set "KEYSTORE=%%~fI"
set "KEY_ALIAS=%~4"

if not exist "!KEYSTORE!" (
  >&2 echo error: keystore does not exist: !KEYSTORE!
  exit /b 1
)
if not defined GATEWAY_KEYSTORE_PASSWORD (
  >&2 echo error: set GATEWAY_KEYSTORE_PASSWORD
  exit /b 1
)
if not defined GATEWAY_KEY_PASSWORD set "GATEWAY_KEY_PASSWORD=!GATEWAY_KEYSTORE_PASSWORD!"

call "%~dp0common.bat" resolve zipalign ZIPALIGN
if errorlevel 1 exit /b 1
call "%~dp0common.bat" resolve apksigner APKSIGNER
if errorlevel 1 exit /b 1

call :preflight "com.somewearlabs.swtak.plugin.apk" || exit /b 1
call :preflight "config.arm64_v8a.apk" || exit /b 1
call :preflight "config.en.apk" || exit /b 1
call :preflight "config.fr.apk" || exit /b 1
call :preflight "config.mdpi.apk" || exit /b 1

if not exist "!OUTPUT_DIR!" mkdir "!OUTPUT_DIR!"
if errorlevel 1 (
  >&2 echo error: could not create output directory: !OUTPUT_DIR!
  exit /b 1
)

set "TEMP_DIR=%TEMP%\somewear-handover-%RANDOM%-%RANDOM%"
mkdir "!TEMP_DIR!" || exit /b 1
set "EXPECTED_SIGNER="

call :sign_one "com.somewearlabs.swtak.plugin.apk" || goto failed
call :sign_one "config.arm64_v8a.apk" || goto failed
call :sign_one "config.en.apk" || goto failed
call :sign_one "config.fr.apk" || goto failed
call :sign_one "config.mdpi.apk" || goto failed

rmdir /s /q "!TEMP_DIR!" >nul 2>&1
echo signer_sha256=!EXPECTED_SIGNER!
echo output=!OUTPUT_DIR!
echo Re-sign SC3 with this same keystore and alias before installation.
exit /b 0

:preflight
if not exist "!INPUT_DIR!\%~1" (
  >&2 echo error: gateway split is missing: !INPUT_DIR!\%~1
  exit /b 1
)
if exist "!OUTPUT_DIR!\%~1" (
  >&2 echo error: refusing to overwrite: !OUTPUT_DIR!\%~1
  exit /b 1
)
exit /b 0

:sign_one
set "APK_NAME=%~1"
set "INPUT_APK=!INPUT_DIR!\!APK_NAME!"
set "ALIGNED_APK=!TEMP_DIR!\!APK_NAME!"
set "OUTPUT_APK=!OUTPUT_DIR!\!APK_NAME!"

call "!ZIPALIGN!" -p -f 4 "!INPUT_APK!" "!ALIGNED_APK!"
if errorlevel 1 exit /b 1
call "!APKSIGNER!" sign --ks "!KEYSTORE!" --ks-key-alias "!KEY_ALIAS!" --ks-pass env:GATEWAY_KEYSTORE_PASSWORD --key-pass env:GATEWAY_KEY_PASSWORD --out "!OUTPUT_APK!" "!ALIGNED_APK!"
if errorlevel 1 exit /b 1
call "!APKSIGNER!" verify "!OUTPUT_APK!" >nul 2>&1
if errorlevel 1 exit /b 1
call "%~dp0common.bat" signer "!APKSIGNER!" "!OUTPUT_APK!" CURRENT_SIGNER
if errorlevel 1 exit /b 1

if not defined EXPECTED_SIGNER (
  set "EXPECTED_SIGNER=!CURRENT_SIGNER!"
) else if /I not "!CURRENT_SIGNER!"=="!EXPECTED_SIGNER!" (
  >&2 echo error: re-signed split signer mismatch: !APK_NAME!
  exit /b 1
)
echo signed !APK_NAME!
exit /b 0

:failed
rmdir /s /q "!TEMP_DIR!" >nul 2>&1
>&2 echo error: gateway re-signing failed
exit /b 1

:usage
>&2 echo usage: %~nx0 INPUT_APK_DIR OUTPUT_APK_DIR KEYSTORE KEY_ALIAS
exit /b 2
