@echo off
setlocal EnableExtensions EnableDelayedExpansion

for %%I in ("%~dp0..\..") do set "REPO_ROOT=%%~fI"
if "%~1"=="" (
  set "APK_DIR=!REPO_ROOT!\build\signed-splits-v2"
  set "CHECK_COMMITTED_HASHES=1"
) else (
  for %%I in ("%~1") do set "APK_DIR=%%~fI"
  set "CHECK_COMMITTED_HASHES=0"
)
set "AAR=!REPO_ROOT!\somewear-gateway-sdk\dist\somewear-gateway-sdk-0.1.0.aar"

call "%~dp0common.bat" resolve apksigner APKSIGNER
if errorlevel 1 exit /b 1
call "%~dp0common.bat" resolve aapt2 AAPT2
if errorlevel 1 exit /b 1

if not exist "!AAR!" (
  >&2 echo error: SDK AAR is missing: !AAR!
  exit /b 1
)

if "!CHECK_COMMITTED_HASHES!"=="1" (
  for /f "usebackq tokens=1,*" %%H in ("!REPO_ROOT!\handover\SHA256SUMS") do (
    set "EXPECTED_HASH=%%H"
    set "RELATIVE_PATH=%%I"
    set "RELATIVE_PATH=!RELATIVE_PATH:/=\!"
    call "%~dp0common.bat" sha256 "!REPO_ROOT!\!RELATIVE_PATH!" ACTUAL_HASH
    if errorlevel 1 exit /b 1
    if /I not "!ACTUAL_HASH!"=="!EXPECTED_HASH!" (
      >&2 echo error: SHA-256 mismatch: !RELATIVE_PATH!
      exit /b 1
    )
  )
  echo committed_hashes=OK
) else (
  echo committed_hashes=SKIPPED ^(custom APK directory^)
)

set "EXPECTED_SIGNER="
call :verify_apk "com.somewearlabs.swtak.plugin.apk" || exit /b 1
call :verify_apk "config.arm64_v8a.apk" || exit /b 1
call :verify_apk "config.en.apk" || exit /b 1
call :verify_apk "config.fr.apk" || exit /b 1
call :verify_apk "config.mdpi.apk" || exit /b 1

set "BASE_APK=!APK_DIR!\com.somewearlabs.swtak.plugin.apk"
set "MANIFEST_OUTPUT=%TEMP%\somewear-manifest-%RANDOM%-%RANDOM%.txt"
call "!AAPT2!" dump xmltree --file AndroidManifest.xml "!BASE_APK!" >"!MANIFEST_OUTPUT!" 2>nul
if errorlevel 1 (
  del /q "!MANIFEST_OUTPUT!" >nul 2>&1
  >&2 echo error: could not inspect the base APK manifest
  exit /b 1
)
findstr /c:"com.somewearlabs.gateway.SomewearGatewayProvider" "!MANIFEST_OUTPUT!" >nul || (
  del /q "!MANIFEST_OUTPUT!" >nul 2>&1
  >&2 echo error: base APK does not declare SomewearGatewayProvider
  exit /b 1
)
findstr /c:"com.somewearlabs.swtak.plugin.somewear.gateway" "!MANIFEST_OUTPUT!" >nul || (
  del /q "!MANIFEST_OUTPUT!" >nul 2>&1
  >&2 echo error: base APK has the wrong provider authority
  exit /b 1
)
del /q "!MANIFEST_OUTPUT!" >nul 2>&1

call "%~dp0common.bat" sha256 "!AAR!" AAR_HASH
if errorlevel 1 exit /b 1
echo !AAR_HASH!  somewear-gateway-sdk-0.1.0.aar
echo signer_sha256=!EXPECTED_SIGNER!
echo provider=com.somewearlabs.gateway.SomewearGatewayProvider
echo authority=com.somewearlabs.swtak.plugin.somewear.gateway
echo verification=OK
exit /b 0

:verify_apk
set "APK_NAME=%~1"
set "APK_PATH=!APK_DIR!\!APK_NAME!"
if not exist "!APK_PATH!" (
  >&2 echo error: gateway split is missing: !APK_PATH!
  exit /b 1
)
call "!APKSIGNER!" verify "!APK_PATH!" >nul 2>&1
if errorlevel 1 (
  >&2 echo error: APK signature verification failed: !APK_NAME!
  exit /b 1
)
call "%~dp0common.bat" signer "!APKSIGNER!" "!APK_PATH!" CURRENT_SIGNER
if errorlevel 1 exit /b 1
if not defined EXPECTED_SIGNER (
  set "EXPECTED_SIGNER=!CURRENT_SIGNER!"
) else if /I not "!CURRENT_SIGNER!"=="!EXPECTED_SIGNER!" (
  >&2 echo error: gateway split signer mismatch: !APK_NAME!
  exit /b 1
)
call "%~dp0common.bat" sha256 "!APK_PATH!" APK_HASH
if errorlevel 1 exit /b 1
echo !APK_HASH!  !APK_NAME!
exit /b 0
