@echo off
setlocal

REM --- 1. НАСТРОЙТЕ ВАШИ ПУТИ ЗДЕСЬ ---
REM (Я взял их из ваших логов. Они должны быть верными)
set "JAVA_21_HOME=C:\Users\alex1\.jdks\corretto-21.0.9"
set "JFX_JMODS=C:\Users\alex1\Downloads\openjfx-21.0.2_windows-x64_bin-jmods\javafx-jmods-21.0.2"
set "PROJECT_DIR=%~dp0"
set "TARGET_DIR=%PROJECT_DIR%target"
set "DIST_DIR=%PROJECT_DIR%dist"

REM --- ИСПРАВЛЕНИЕ: Прямой путь к Maven, который использует ваша IDE ---
set "MVN_CMD=C:\Program Files\JetBrains\IntelliJ IDEA 2024.2.1\plugins\maven\lib\maven3\bin\mvn.cmd"

echo ===================================================
echo           DEB OR DEAD - MVP BUILDER (Ручной режим)
echo ===================================================
echo.

REM --- 2. СБОРКА MAVEN (СОЗДАНИЕ JAR И LIBS) ---
echo [1/5] Building project with Maven...
call "%MVN_CMD%" clean package
if %errorlevel% neq 0 (
    echo MAVEN FAILED! Build aborted.
    goto :error
)

REM --- 3. ОЧИСТКА СТАРЫХ СБОРОK ---
echo [2/5] Cleaning old builds...
if exist "%TARGET_DIR%\dodJRE" (
    echo Deleting old JRE...
    rmdir /S /Q "%TARGET_DIR%\dodJRE"
)
if exist "%DIST_DIR%\DepOrDead_RU" (
    echo Deleting old RU build...
    rmdir /S /Q "%DIST_DIR%\DepOrDead_RU"
)
if exist "%DIST_DIR%\DepOrDead_EN" (
    echo Deleting old EN build...
    rmdir /S /Q "%DIST_DIR%\DepOrDead_EN"
)

REM --- 4. JLINK (ИСПРАВЛЕННАЯ ВЕРСИЯ: Включаем Jackson) ---
echo [3/5] Creating custom JRE (with JavaFX and Jackson)...
call "%JAVA_21_HOME%\bin\jlink.exe" --module-path "%JAVA_21_HOME%\jmods;%JFX_JMODS%;%TARGET_DIR%\libs" --add-modules java.base,javafx.controls,javafx.fxml,javafx.graphics,com.fasterxml.jackson.databind --output "%TARGET_DIR%\dodJRE" --strip-debug --compress=2 --no-header-files --no-man-pages
if %errorlevel% neq 0 (
    echo JLINK FAILED!
    goto :error
)

REM --- 5. JPACKAGE (РУССКАЯ ВЕРСИЯ) (Ваша команда) ---
echo [4/5] Packaging Russian (RU) version...
call "%JAVA_21_HOME%\bin\jpackage.exe" --type app-image --name DepOrDead_RU --arguments "ru" --input "%TARGET_DIR%" --main-jar DepOrDeadMVP-1.0-SNAPSHOT.jar --main-class yermakov.oleksii.Main --runtime-image "%TARGET_DIR%\dodJRE" --dest "%DIST_DIR%" --win-console
if %errorlevel% neq 0 (
    echo JPACKAGE (RU) FAILED!
    goto :error
)

REM --- 6. JPACKAGE (АНГЛИЙСКАЯ ВЕРСИЯ) (Ваша команда) ---
echo [5/5] Packaging English (EN) version...
call "%JAVA_21_HOME%\bin\jpackage.exe" --type app-image --name DepOrDead_EN --arguments "en" --input "%TARGET_DIR%" --main-jar DepOrDeadMVP-1.0-SNAPSHOT.jar --main-class yermakov.oleksii.Main --runtime-image "%TARGET_DIR%\dodJRE" --dest "%DIST_DIR%" --win-console
if %errorlevel% neq 0 (
    echo JPACKAGE (EN) FAILED!
    goto :error
)

echo.
echo ===================================================
echo BUILD SUCCESSFUL!
echo Applications are ready in the 'dist' folder.
echo ===================================================
goto :eof

:error
echo.
echo ===================================================
echo BUILD FAILED!
echo ===================================================

:eof
pause
endlocal