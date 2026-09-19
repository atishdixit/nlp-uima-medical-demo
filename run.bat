@echo off
setlocal EnableExtensions EnableDelayedExpansion
rem ============================================================================
rem  Medical chart NLP demo (Apache UIMA + Spring Boot + MySQL)
rem
rem    run.bat            start MySQL (Docker), build, run the app on http://localhost:8080
rem    run.bat h2         run with in-memory H2 - no Docker, no MySQL needed
rem    run.bat test       run the automated tests (no Docker needed)
rem    run.bat db         only start MySQL in Docker
rem    run.bat stop       stop the MySQL container (data is kept)
rem    run.bat reset      stop MySQL and DELETE its data volume
rem    run.bat help       this text
rem ============================================================================

cd /d "%~dp0"
set "MODE=%~1"
if "%MODE%"=="" set "MODE=mysql"

if /i "%MODE%"=="help"  goto :help
if /i "%MODE%"=="/?"    goto :help
if /i "%MODE%"=="-h"    goto :help

call :check_java   || exit /b 1
call :find_maven   || exit /b 1

if /i "%MODE%"=="test"  goto :test
if /i "%MODE%"=="h2"    goto :h2
if /i "%MODE%"=="stop"  goto :stop
if /i "%MODE%"=="reset" goto :reset
if /i "%MODE%"=="db"    goto :db
if /i "%MODE%"=="mysql" goto :mysql

echo Unknown option "%MODE%".
goto :help_fail

:test
echo === Running tests (H2 in MySQL mode, no external services) ===
call %MVN% -B test
exit /b %ERRORLEVEL%

:h2
echo === Starting with in-memory H2 (data is lost on exit) ===
echo Swagger UI: http://localhost:8080/swagger-ui.html
call %MVN% -B spring-boot:run "-Dspring-boot.run.profiles=h2"
exit /b %ERRORLEVEL%

:db
call :start_mysql || exit /b 1
echo MySQL is ready on localhost:%MYSQL_PORT%.
exit /b 0

:stop
call :check_docker || exit /b 1
docker compose stop mysql
exit /b %ERRORLEVEL%

:reset
call :check_docker || exit /b 1
echo This deletes the MySQL data volume (rules will be re-seeded on next start).
docker compose down -v
exit /b %ERRORLEVEL%

:mysql
call :start_mysql || exit /b 1
echo === Building and starting the application ===
echo Swagger UI: http://localhost:8080/swagger-ui.html
call %MVN% -B spring-boot:run
exit /b %ERRORLEVEL%

rem ---------------------------------------------------------------- helpers

:start_mysql
if not defined MYSQL_PORT set "MYSQL_PORT=3306"
call :check_docker || exit /b 1
echo === Starting MySQL (docker compose) ===
docker compose up -d mysql
if errorlevel 1 (
    echo ERROR: could not start the MySQL container. Is port %MYSQL_PORT% already in use?
    echo        Set MYSQL_PORT to another value, e.g.  set MYSQL_PORT=3307
    exit /b 1
)
echo Waiting for MySQL to become healthy ^(first start can take up to a minute^)...
set /a TRIES=0
:wait_loop
set "HEALTH="
for /f "delims=" %%H in ('docker inspect --format "{{.State.Health.Status}}" mednlp-mysql 2^>nul') do set "HEALTH=%%H"
if /i "!HEALTH!"=="healthy" exit /b 0
set /a TRIES+=1
if !TRIES! GEQ 60 (
    echo ERROR: MySQL did not become healthy in time. Check:  docker logs mednlp-mysql
    exit /b 1
)
rem "ping" instead of "timeout": timeout fails when stdin is redirected (CI, IDE run windows)
ping -n 4 127.0.0.1 >nul
goto :wait_loop

:check_docker
where docker >nul 2>nul
if errorlevel 1 (
    echo ERROR: Docker was not found. Install Docker Desktop, or run "run.bat h2" to use the in-memory database.
    exit /b 1
)
docker info >nul 2>nul
if errorlevel 1 (
    echo ERROR: The Docker daemon is not running. Start Docker Desktop and retry, or run "run.bat h2".
    exit /b 1
)
exit /b 0

:check_java
where java >nul 2>nul
if errorlevel 1 (
    echo ERROR: Java was not found. Install JDK 21 and make sure JAVA_HOME / PATH point to it. See SETUP.md.
    exit /b 1
)
set "JAVA_MAJOR="
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "RAW=%%~V"
    for /f "tokens=1 delims=." %%M in ("!RAW!") do set "JAVA_MAJOR=%%M"
)
if not defined JAVA_MAJOR (
    echo WARNING: could not detect the Java version; continuing.
    exit /b 0
)
if !JAVA_MAJOR! LSS 21 (
    echo ERROR: Java 21 or newer is required ^(found !JAVA_MAJOR!^). See SETUP.md.
    exit /b 1
)
exit /b 0

:find_maven
if exist "%~dp0mvnw.cmd" (
    set "MVN="%~dp0mvnw.cmd""
    exit /b 0
)
where mvn >nul 2>nul
if errorlevel 1 (
    echo ERROR: Maven was not found and there is no mvnw.cmd. Install Maven 3.9+ ^(see SETUP.md^).
    exit /b 1
)
set "MVN=mvn"
exit /b 0

:help
echo.
echo   run.bat          start MySQL ^(Docker^), build and run the app  -^> http://localhost:8080
echo   run.bat h2       run with in-memory H2 ^(no Docker / MySQL needed^)
echo   run.bat test     run the automated tests
echo   run.bat db       only start MySQL in Docker
echo   run.bat stop     stop the MySQL container ^(data kept^)
echo   run.bat reset    stop MySQL and delete its data volume
echo.
exit /b 0

:help_fail
call :help
exit /b 1
