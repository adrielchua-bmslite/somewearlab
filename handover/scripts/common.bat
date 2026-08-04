@echo off

if /I "%~1"=="resolve" goto resolve
if /I "%~1"=="sha256" goto sha256
if /I "%~1"=="signer" goto signer

>&2 echo error: unsupported common.bat command
exit /b 2

:resolve
setlocal EnableExtensions EnableDelayedExpansion
set "TOOL_NAME=%~2"
set "OUTPUT_NAME=%~3"
set "TOOL_PATH="

if not defined TOOL_NAME (
  >&2 echo error: missing Android tool name
  endlocal
  exit /b 2
)

for %%E in ("" ".exe" ".bat") do (
  if not defined TOOL_PATH (
    for /f "delims=" %%P in ('where "!TOOL_NAME!%%~E" 2^>nul') do if not defined TOOL_PATH set "TOOL_PATH=%%P"
  )
)

set "SDK_ROOT_1=%ANDROID_SDK_ROOT%"
set "SDK_ROOT_2=%ANDROID_HOME%"
set "SDK_ROOT_3=%LOCALAPPDATA%\Android\Sdk"
set "SDK_ROOT_4=%USERPROFILE%\AppData\Local\Android\Sdk"

for %%R in ("!SDK_ROOT_1!" "!SDK_ROOT_2!" "!SDK_ROOT_3!" "!SDK_ROOT_4!") do (
  if not defined TOOL_PATH if not "%%~R"=="" if exist "%%~R" (
    for %%E in ("" ".exe" ".bat") do (
      if /I "!TOOL_NAME!"=="adb" (
        if not defined TOOL_PATH if exist "%%~R\platform-tools\!TOOL_NAME!%%~E" set "TOOL_PATH=%%~R\platform-tools\!TOOL_NAME!%%~E"
      ) else (
        if exist "%%~R\build-tools" (
          for /f "delims=" %%D in ('dir /b /ad /o-n "%%~R\build-tools" 2^>nul') do (
            if not defined TOOL_PATH if exist "%%~R\build-tools\%%D\!TOOL_NAME!%%~E" set "TOOL_PATH=%%~R\build-tools\%%D\!TOOL_NAME!%%~E"
          )
        )
      )
    )
  )
)

if defined TOOL_PATH (
  endlocal & set "%OUTPUT_NAME%=%TOOL_PATH%"
  exit /b 0
)

>&2 echo error: %TOOL_NAME% was not found.
>&2 echo.
>&2 echo Install these components in Android Studio:
>&2 echo   Tools ^> SDK Manager ^> SDK Tools
>&2 echo   - Android SDK Build-Tools
>&2 echo   - Android SDK Platform-Tools
>&2 echo.
>&2 echo The script searched PATH, ANDROID_SDK_ROOT, ANDROID_HOME, and:
>&2 echo   %%LOCALAPPDATA%%\Android\Sdk
>&2 echo.
>&2 echo If the SDK is elsewhere, run:
>&2 echo   set "ANDROID_SDK_ROOT=C:\absolute\path\to\Android\Sdk"
endlocal
exit /b 1

:sha256
setlocal EnableExtensions
set "FILE_PATH=%~2"
set "OUTPUT_NAME=%~3"
set "FILE_HASH="

if not exist "%FILE_PATH%" (
  >&2 echo error: file not found: %FILE_PATH%
  endlocal
  exit /b 1
)

for /f "skip=1 tokens=* delims=" %%H in ('certutil -hashfile "%FILE_PATH%" SHA256 2^>nul') do if not defined FILE_HASH set "FILE_HASH=%%H"
set "FILE_HASH=%FILE_HASH: =%"
if not defined FILE_HASH (
  >&2 echo error: could not calculate SHA-256: %FILE_PATH%
  endlocal
  exit /b 1
)

endlocal & set "%OUTPUT_NAME%=%FILE_HASH%"
exit /b 0

:signer
setlocal EnableExtensions EnableDelayedExpansion
set "APKSIGNER_PATH=%~2"
set "APK_PATH=%~3"
set "OUTPUT_NAME=%~4"
set "CERT_OUTPUT=%TEMP%\somewear-apksigner-%RANDOM%-%RANDOM%.txt"
set "SIGNER_HASH="

call "%APKSIGNER_PATH%" verify --print-certs "%APK_PATH%" >"!CERT_OUTPUT!" 2>nul
if errorlevel 1 (
  del /q "!CERT_OUTPUT!" >nul 2>&1
  >&2 echo error: APK signature verification failed: %APK_PATH%
  endlocal
  exit /b 1
)

for /f "tokens=2 delims=:" %%S in ('findstr /c:"SHA-256 digest:" "!CERT_OUTPUT!"') do if not defined SIGNER_HASH set "SIGNER_HASH=%%S"
for /f "tokens=*" %%S in ("!SIGNER_HASH!") do set "SIGNER_HASH=%%S"
del /q "!CERT_OUTPUT!" >nul 2>&1

if not defined SIGNER_HASH (
  >&2 echo error: could not read APK signer: %APK_PATH%
  endlocal
  exit /b 1
)

endlocal & set "%OUTPUT_NAME%=%SIGNER_HASH%"
exit /b 0
