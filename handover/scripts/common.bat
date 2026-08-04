@echo off

if /I "%~1"=="resolve" goto resolve
if /I "%~1"=="resolve-java" goto resolve_java

>&2 echo error: unsupported common.bat command
exit /b 2

:resolve_java
setlocal EnableExtensions EnableDelayedExpansion
set "OUTPUT_NAME=%~2"
set "JAVA_PATH="

for %%J in (
  "%JAVA_HOME%\bin\java.exe"
  "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe"
  "%ProgramFiles%\Android\Android Studio\jre\bin\java.exe"
  "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\java.exe"
) do if not defined JAVA_PATH if exist "%%~J" set "JAVA_PATH=%%~fJ"

if not defined JAVA_PATH if exist "%ProgramFiles%\Java" (
  for /f "delims=" %%D in ('dir /b /ad /o-n "%ProgramFiles%\Java\jdk-*" 2^>nul') do (
    if not defined JAVA_PATH if exist "%ProgramFiles%\Java\%%D\bin\java.exe" set "JAVA_PATH=%ProgramFiles%\Java\%%D\bin\java.exe"
  )
)

if not defined JAVA_PATH if exist "%ProgramFiles%\Eclipse Adoptium" (
  for /f "delims=" %%D in ('dir /b /ad /o-n "%ProgramFiles%\Eclipse Adoptium\jdk-*" 2^>nul') do (
    if not defined JAVA_PATH if exist "%ProgramFiles%\Eclipse Adoptium\%%D\bin\java.exe" set "JAVA_PATH=%ProgramFiles%\Eclipse Adoptium\%%D\bin\java.exe"
  )
)

if not defined JAVA_PATH (
  for /f "delims=" %%J in ('where java.exe 2^>nul') do if not defined JAVA_PATH set "JAVA_PATH=%%J"
)

if defined JAVA_PATH (
  endlocal & set "%OUTPUT_NAME%=%JAVA_PATH%"
  exit /b 0
)

>&2 echo error: Java was not found.
>&2 echo The handover no longer needs Android SDK Build-Tools, but it does need Java 17 or newer.
>&2 echo Install Android Studio or a Java 17 JDK, then retry. No Android environment variables are required.
endlocal
exit /b 1

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
>&2 echo Install Android SDK Platform-Tools in Android Studio:
>&2 echo   Tools ^> SDK Manager ^> SDK Tools ^> Android SDK Platform-Tools
>&2 echo.
>&2 echo The script searched PATH, ANDROID_SDK_ROOT, ANDROID_HOME, and:
>&2 echo   %%LOCALAPPDATA%%\Android\Sdk
>&2 echo.
>&2 echo If the SDK is elsewhere, run:
>&2 echo   set "ANDROID_SDK_ROOT=C:\absolute\path\to\Android\Sdk"
endlocal
exit /b 1
