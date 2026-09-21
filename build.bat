@echo off
setlocal EnableExtensions
rem Builds target\pdf-ocr-extractor.jar (one self-contained jar) and runs the tests.
rem   build.bat            build with tests
rem   build.bat skiptests  build without running the tests (faster)
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo ERROR: Java was not found. Install JDK 21 or newer. See README.md.
    exit /b 2
)

set "MVN_ARGS=-B -q clean package"
if /i "%~1"=="skiptests" set "MVN_ARGS=-B -q -DskipTests clean package"

echo Building pdf-ocr-extractor.jar ...
call "%~dp0mvnw.cmd" %MVN_ARGS%
if errorlevel 1 (
    echo BUILD FAILED. Run  mvnw.cmd clean package  to see the details.
    exit /b 2
)
echo Built: %~dp0target\pdf-ocr-extractor.jar
exit /b 0
