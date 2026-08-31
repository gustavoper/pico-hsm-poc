@echo off
setlocal

echo ======================================
echo  Compiling Main.java
echo ======================================
echo.

if "%JAVA_HOME%"=="" (
    echo ERROR: JAVA_HOME is not defined.
    exit /b 1
)

if not exist out mkdir out

echo JAVA_HOME: %JAVA_HOME%
echo Source   : src\Main.java
echo Output   : out
echo.

"%JAVA_HOME%\javac.exe" ^
    -d out ^
    ..\src\Main.java

if errorlevel 1 (
    echo.
    echo ERROR: Compilation failed.
    exit /b 1
)

echo.
echo Main.java compiled successfully.

endlocal