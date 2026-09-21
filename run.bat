@echo off
setlocal EnableExtensions EnableDelayedExpansion
rem ============================================================================
rem  PDF OCR extractor
rem
rem  Reads every PDF in  input_document\  , runs OCR, and writes one text file per PDF
rem  into  output_document\  (same name, .txt).
rem
rem    run.bat                    convert the PDFs in input_document
rem    run.bat --force            convert again even if the .txt already exists
rem    run.bat --lang eng+deu     other/more languages (downloaded on first use)
rem    run.bat --mode auto        use a PDF's own text where it has any, OCR the rest
rem    run.bat --help             all options
rem    build.bat                  (re)build the jar after changing the source
rem
rem  Any option you type is passed straight to the jar. Environment: JAVA_OPTS (e.g. -Xmx4g)
rem ============================================================================

cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo ERROR: Java was not found. Install JDK 21 or newer and open a new terminal. See README.md.
    exit /b 2
)
set "JAVA_MAJOR="
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "RAW=%%~V"
    for /f "tokens=1 delims=." %%M in ("!RAW!") do set "JAVA_MAJOR=%%M"
)
if defined JAVA_MAJOR if !JAVA_MAJOR! LSS 21 (
    echo ERROR: Java 21 or newer is required ^(found !JAVA_MAJOR!^). See README.md.
    exit /b 2
)

if not exist "target\pdf-ocr-extractor.jar" (
    echo The jar has not been built yet; building it now ^(first time only, needs internet^)...
    call "%~dp0build.bat" skiptests || exit /b 2
)

if not exist "input_document" mkdir "input_document"
if not exist "output_document" mkdir "output_document"

java %JAVA_OPTS% -jar "target\pdf-ocr-extractor.jar" %*
set "RC=%ERRORLEVEL%"

if "%RC%"=="0" (
    echo.
    echo Done. Text files are in: %~dp0output_document
) else if "%RC%"=="1" (
    echo.
    echo Finished, but some files failed. See the messages above or logs\pdf-ocr.log
) else (
    echo.
    echo The run could not start. See the messages above.
)
exit /b %RC%
