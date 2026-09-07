@echo off
setlocal enabledelayedexpansion

echo ==============================
echo Avvio build GUI
echo ==============================


rem ==========================================
rem Percorsi (calcolati rispetto alla posizione dello script)
rem ==========================================

rem GUI = cartella in cui si trova QUESTO script (erimim-gui)
set "GUI=%~dp0"
for %%A in ("%GUI%.") do set "GUI=%%~fA"

rem BASE = cartella padre di GUI (cioe' "1b", che contiene sia src che erimim-gui)
for %%A in ("%GUI%\..") do set "BASE=%%~fA"

set "SRC=%BASE%\src"
set "OUT=%GUI%\out"

echo BASE=%BASE%
echo SRC=%SRC%
echo GUI=%GUI%
echo OUT=%OUT%

if not exist "%SRC%\world.java" (
    echo.
    echo ERRORE: non trovo "%SRC%\world.java"
    echo Controlla che questo script si trovi dentro la cartella "erimim-gui",
    echo allo stesso livello della cartella "src" ^(entrambe dentro "1b"^).
    pause
    exit /b 1
)


rem ==========================================
rem Ricerca JDK (javac)
rem ==========================================

set "JAVAC_EXE="

rem 1) javac gia' nel PATH?
where javac >nul 2>nul
if not errorlevel 1 (
    for /f "delims=" %%J in ('where javac') do (
        if not defined JAVAC_EXE set "JAVAC_EXE=%%J"
    )
)

rem 2) JAVA_HOME impostata?
if not defined JAVAC_EXE (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_EXE=%JAVA_HOME%\bin\javac.exe"
    )
)

rem 3) cerca automaticamente in "C:\Program Files\Java\jdk-*"
if not defined JAVAC_EXE (
    for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Java\jdk-*" 2^>nul') do (
        if not defined JAVAC_EXE (
            if exist "C:\Program Files\Java\%%D\bin\javac.exe" (
                set "JAVAC_EXE=C:\Program Files\Java\%%D\bin\javac.exe"
            )
        )
    )
)

if not defined JAVAC_EXE (
    echo.
    echo JDK non trovato ^(javac.exe^).
    echo Installa un JDK oppure imposta JAVA_HOME, poi riprova.
    pause
    exit /b 1
)

for %%J in ("%JAVAC_EXE%") do set "JDK_BIN=%%~dpJ"
echo JDK trovato: %JAVAC_EXE%



rem ==========================================
rem Pulizia
rem ==========================================

if exist "%OUT%" rd /s /q "%OUT%"

mkdir "%OUT%"



rem ==========================================
rem Compilazione
rem ==========================================

echo Compilazione...


"%JDK_BIN%javac.exe" -d "%OUT%" -cp "%BASE%" "%SRC%\brain.java" "%SRC%\entity.java" "%SRC%\Entity_manager.java" "%SRC%\world.java" "%GUI%\GameWindow.java" "%GUI%\EntityTracker.java" "%GUI%\EntityInspectorDialog.java"


if errorlevel 1 (
    echo.
    echo COMPILAZIONE FALLITA
    pause
    exit /b 1
)



echo Compilazione OK


dir "%OUT%"


rem ==========================================
rem Avvio
rem ==========================================

echo Avvio GameWindow...


cd /d "%GUI%"


"%JDK_BIN%java.exe" -cp "%OUT%" GameWindow


pause