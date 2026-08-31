@echo off
setlocal EnableExtensions EnableDelayedExpansion

echo ======================================
echo  Testing MainPasswordCrypt.java
echo ======================================
echo.

if "%JAVA_HOME%"=="" (
    echo ERROR: JAVA_HOME is not defined.
    exit /b 1
)

rem ========================================
rem Test passwords
rem ========================================

set "PASSWORD=MinhaSenha123!"
set "WRONG_PASSWORD=MinhaSenhaErrada123!"

rem ========================================
rem Temporary output
rem ========================================

set "TEMP_OUTPUT=%TEMP%\pico-hsm-encrypt-%RANDOM%.txt"

rem ========================================
rem STEP 1
rem ========================================

echo --------------------------------------
echo  STEP 1 - Generate encrypted verifier
echo --------------------------------------
echo.

"%JAVA_HOME%\java.exe" ^
    --add-modules jdk.crypto.cryptoki ^
    --add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED ^
    -cp out ^
    MainPasswordCrypt ^
    encrypt ^
    "%PASSWORD%" > "%TEMP_OUTPUT%"

if errorlevel 1 (
    echo.
    echo ERROR: Encrypt failed.
    del "%TEMP_OUTPUT%" >nul 2>&1
    exit /b 1
)

type "%TEMP_OUTPUT%"

rem ========================================
rem Capture generated values
rem ========================================

for /f "tokens=1,* delims==" %%A in ('findstr /b "SALT_BASE64=" "%TEMP_OUTPUT%"') do (
    set "SALT_BASE64=%%B"
)

for /f "tokens=1,* delims==" %%A in ('findstr /b "CIPHERTEXT_BASE64=" "%TEMP_OUTPUT%"') do (
    set "CIPHERTEXT_BASE64=%%B"
)

del "%TEMP_OUTPUT%" >nul 2>&1

rem ========================================
rem Validate captured values
rem ========================================

if not defined SALT_BASE64 (
    echo.
    echo ERROR: SALT_BASE64 was not generated.
    exit /b 1
)

if not defined CIPHERTEXT_BASE64 (
    echo.
    echo ERROR: CIPHERTEXT_BASE64 was not generated.
    exit /b 1
)

echo.
echo Values captured successfully.
echo.
echo SALT_BASE64=!SALT_BASE64!
echo CIPHERTEXT_BASE64=!CIPHERTEXT_BASE64!

rem ========================================
rem STEP 2 - MATCH
rem ========================================

echo.
echo --------------------------------------
echo  STEP 2 - Correct password
echo  Expected result: MATCH
echo --------------------------------------
echo.
echo Pico-HSM will request the PIN now.
echo.

"%JAVA_HOME%\java.exe" ^
    --add-modules jdk.crypto.cryptoki ^
    --add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED ^
    -cp out ^
    MainPasswordCrypt ^
    verify ^
    "%PASSWORD%" ^
    "!SALT_BASE64!" ^
    "!CIPHERTEXT_BASE64!"

set "MATCH_EXIT=!ERRORLEVEL!"

if not "!MATCH_EXIT!"=="0" (
    echo.
    echo ERROR: Expected MATCH but exit code was !MATCH_EXIT!.
    exit /b 1
)

echo.
echo MATCH test completed successfully.

rem ========================================
rem STEP 3 - NO_MATCH
rem ========================================

echo.
echo --------------------------------------
echo  STEP 3 - Wrong password
echo  Expected result: NO_MATCH
echo --------------------------------------
echo.
echo Pico-HSM will request the PIN again.
echo.

"%JAVA_HOME%\java.exe" ^
    --add-modules jdk.crypto.cryptoki ^
    --add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED ^
    -cp out ^
    MainPasswordCrypt ^
    verify ^
    "%WRONG_PASSWORD%" ^
    "!SALT_BASE64!" ^
    "!CIPHERTEXT_BASE64!"

set "NO_MATCH_EXIT=!ERRORLEVEL!"

rem MainPasswordCrypt returns 3 for NO_MATCH

if "!NO_MATCH_EXIT!"=="3" (
    echo.
    echo NO_MATCH received as expected.
) else (
    echo.
    echo ERROR: Expected exit code 3 for NO_MATCH.
    echo Received: !NO_MATCH_EXIT!
    exit /b 1
)

echo.
echo ======================================
echo  ALL PASSWORD CRYPT TESTS PASSED
echo ======================================
echo.
echo Correct password : MATCH
echo Wrong password   : NO_MATCH
echo HSM decrypt      : OK
echo PKCS#11          : OK
echo.

endlocal
exit /b 0