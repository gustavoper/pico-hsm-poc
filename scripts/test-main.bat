@echo off
setlocal

echo ======================================
echo  Testing Main.java
echo ======================================
echo.

if "%JAVA_HOME%"=="" (
    echo ERROR: JAVA_HOME is not defined.
    exit /b 1
)

set "PASSWORD=MinhaSenha123!"
set "WRONG_PASSWORD=MinhaSenhaErrada123!"
set "TEMP_OUTPUT=%TEMP%\pico-hsm-signature-%RANDOM%-%RANDOM%.txt"

echo --------------------------------------
echo  TEST 1 - Generate signature
echo --------------------------------------
echo.

"%JAVA_HOME%\java.exe" ^
    -cp out ^
    Main generate "%PASSWORD%" "%TEMP_OUTPUT%"

set "GENERATE_EXIT=%ERRORLEVEL%"

if not "%GENERATE_EXIT%"=="0" (
    echo.
    echo ERROR: Signature generation failed.
    del "%TEMP_OUTPUT%" >nul 2>&1
    exit /b 1
)

if not exist "%TEMP_OUTPUT%" (
    echo.
    echo ERROR: Signature output file was not generated.
    exit /b 1
)

set /p "BASE64_SIGNATURE=" < "%TEMP_OUTPUT%"

del "%TEMP_OUTPUT%" >nul 2>&1

if not defined BASE64_SIGNATURE (
    echo.
    echo ERROR: SIGNATURE_BASE64 was not generated.
    exit /b 1
)

echo.
echo --------------------------------------
echo  TEST 2 - Correct password
echo  Expected: MATCH
echo --------------------------------------
echo.

"%JAVA_HOME%\java.exe" ^
    -cp out ^
    Main verify "%PASSWORD%" "%BASE64_SIGNATURE%"

set "MATCH_EXIT=%ERRORLEVEL%"

if not "%MATCH_EXIT%"=="0" (
    echo.
    echo ERROR: Expected MATCH but exit code was %MATCH_EXIT%.
    exit /b 1
)

echo.
echo --------------------------------------
echo  TEST 3 - Wrong password
echo  Expected: NO MATCH
echo --------------------------------------
echo.

"%JAVA_HOME%\java.exe" ^
    -cp out ^
    Main verify "%WRONG_PASSWORD%" "%BASE64_SIGNATURE%"

set "NO_MATCH_EXIT=%ERRORLEVEL%"

if not "%NO_MATCH_EXIT%"=="3" (
    echo.
    echo ERROR: Expected NO_MATCH with exit code 3, but received %NO_MATCH_EXIT%.
    exit /b 1
)

echo.
echo ======================================
echo  Main tests finished
echo ======================================

endlocal
exit /b 0
