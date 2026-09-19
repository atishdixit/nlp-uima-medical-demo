@echo off
setlocal EnableExtensions EnableDelayedExpansion
rem ============================================================================
rem  Medical chart NLP demo (Apache UIMA + Spring Boot + MySQL)
rem
rem    run.bat            start MySQL (Docker), build, run app + UI on http://localhost:8080
rem    run.bat h2         same with in-memory H2 - no Docker, no MySQL needed
rem    run.bat dev        UI developer mode: backend (H2) + Angular live-reload on :4200
rem    run.bat test       backend tests (no Docker needed)
rem    run.bat uitest     Angular UI tests (needs Node 20.19+ on PATH)
rem    run.bat db         only start MySQL in Docker
rem    run.bat stop       stop the MySQL container (data is kept)
rem    run.bat reset      stop MySQL and DELETE its data volume
rem    run.bat help       this text
rem
rem  The Angular UI is built into the app by Maven (-Pui). Maven downloads its own Node, so
rem  nothing needs installing. Set SKIP_UI=1 to start the API only (faster, no UI).
rem ============================================================================

cd /d "%~dp0"
set "MODE=%~1"
if "%MODE%"=="" set "MODE=mysql"

if /i "%MODE%"=="help"  goto :help
if /i "%MODE%"=="/?"    goto :help
if /i "%MODE%"=="-h"    goto :help

call :check_java   || exit /b 1
call :find_maven   || exit /b 1

set "UI=-Pui"
if "%SKIP_UI%"=="1" set "UI="

if /i "%MODE%"=="test"   goto :test
if /i "%MODE%"=="uitest" goto :uitest
if /i "%MODE%"=="dev"    goto :dev
if /i "%MODE%"=="h2"     goto :h2
if /i "%MODE%"=="stop"   goto :stop
if /i "%MODE%"=="reset"  goto :reset
if /i "%MODE%"=="db"     goto :db
if /i "%MODE%"=="mysql"  goto :mysql

echo Unknown option "%MODE%".
goto :help_fail

:test
echo === Running backend tests (H2 in MySQL mode, no external services) ===
call %MVN% -B test
exit /b %ERRORLEVEL%

:uitest
call :check_node || exit /b 1
echo === Running Angular UI tests ===
pushd frontend
if not exist node_modules call npm ci || (popd & exit /b 1)
set "CI=1"
call npm test -- --watch=false
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%

:dev
call :check_node || exit /b 1
echo === UI developer mode ===
echo Starting the backend (H2, API only) in a new window...
set "SKIP_UI=1"
start "MedNLP backend (H2)" cmd /k ""%~f0" h2"
pushd frontend
if not exist node_modules call npm ci || (popd & exit /b 1)
echo Angular dev server with live reload: http://localhost:4200  ^(API calls are proxied to :8080^)
call npm start
popd
exit /b %ERRORLEVEL%

:h2
echo === Starting with in-memory H2 (data is lost on exit) ===
if defined UI ( echo The first run also builds the UI; this takes a couple of minutes. )
echo App + UI:  http://localhost:8080      Swagger UI: http://localhost:8080/swagger-ui.html
call %MVN% -B %UI% spring-boot:run "-Dspring-boot.run.profiles=h2"
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
if defined UI ( echo The first run also builds the UI; this takes a couple of minutes. )
echo App + UI:  http://localhost:8080      Swagger UI: http://localhost:8080/swagger-ui.html
call %MVN% -B %UI% spring-boot:run
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

:check_node
where node >nul 2>nul
if errorlevel 1 (
    echo ERROR: Node.js was not found. This mode needs Node 20.19+ ^(or 22.12+^) on PATH. See SETUP.md.
    echo        "run.bat h2" and "run.bat" do NOT need it: Maven downloads its own Node to build the UI.
    exit /b 1
)
exit /b 0

:help
echo.
echo   run.bat          start MySQL ^(Docker^), build and run app + UI -^> http://localhost:8080
echo   run.bat h2       same with in-memory H2 ^(no Docker / MySQL needed^)
echo   run.bat dev      UI developer mode: backend + Angular live reload on :4200 ^(needs Node^)
echo   run.bat test     run the backend tests
echo   run.bat uitest   run the Angular UI tests ^(needs Node^)
echo   run.bat db       only start MySQL in Docker
echo   run.bat stop     stop the MySQL container ^(data kept^)
echo   run.bat reset    stop MySQL and delete its data volume
echo   set SKIP_UI=1    start without building the UI ^(API only, faster^)
echo.
exit /b 0

:help_fail
call :help
exit /b 1
