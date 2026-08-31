@echo off
setlocal

echo ======================================
echo  Compiling MainPasswordCrypt.java
echo ======================================
echo.

if "%JAVA_HOME%"=="" (
    echo ERROR: JAVA_HOME is not defined.
    exit /b 1
)

if not exist out mkdir out

echo JAVA_HOME: %JAVA_HOME%
echo Source   : ..\src\MainPasswordCrypt.java
echo Output   : out
echo.

"%JAVA_HOME%\javac.exe" ^
    --add-modules jdk.crypto.cryptoki ^
    --add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED ^
    -d out ^
    ..\src\MainPasswordCrypt.java

if errorlevel 1 (
    echo.
    echo ERROR: Compilation failed.
    exit /b 1
)

echo.
echo MainPasswordCrypt.java compiled successfully.

endlocal