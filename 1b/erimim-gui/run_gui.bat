@echo off
setlocal

echo ==============================
echo Avvio build GUI
echo ==============================


rem ==========================================
rem Percorsi
rem ==========================================

set "BASE=%~dp0..\.."

for %%A in ("%BASE%") do set "BASE=%%~fA"


set "GUI=%BASE%\erimim-gui"
set "SRC=%BASE%\src"
set "OUT=%GUI%\out"


echo BASE=%BASE%
echo SRC=%SRC%
echo GUI=%GUI%
echo OUT=%OUT%


rem ==========================================
rem JDK
rem ==========================================

set "JDK_BIN=C:\Program Files\Java\jdk-25\bin"


if not exist "%JDK_BIN%\javac.exe" (
    echo JDK non trovato
    pause
    exit /b 1
)



rem ==========================================
rem Pulizia
rem ==========================================

if exist "%OUT%" rd /s /q "%OUT%"

mkdir "%OUT%"



rem ==========================================
rem Compilazione
rem ==========================================

echo Compilazione...


"%JDK_BIN%\javac.exe" -d "%OUT%" -cp "%BASE%" "%SRC%\brain.java" "%SRC%\entity.java" "%SRC%\Entity_manager.java" "%SRC%\world.java" "%GUI%\GameWindow.java" "%GUI%\EntityTracker.java" "%GUI%\EntityInspectorDialog.java"


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


"%JDK_BIN%\java.exe" -cp "%OUT%" GameWindow


pause