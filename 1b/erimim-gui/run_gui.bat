@echo off
setlocal

rem === Percorsi (adatta BASE se la tua cartella di lavoro e' diversa) ===
rem BASE deve contenere sia "src" che "erimim-gui" come sottocartelle,
rem esattamente come nel repo (1b/src e 1b/erimim-gui).
set "BASE=C:\Users\anton\Desktop\Claude_Workspace"
set "GUI=%BASE%\erimim-gui"
set "SRC=%BASE%\src"
set "OUT=%GUI%\out"
set "JDK_BIN=C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot\bin"

cd /d "%GUI%"

echo Pulizia build precedente...
if exist "%OUT%" rd /s /q "%OUT%"
mkdir "%OUT%"

echo Compilazione dai sorgenti originali (%SRC%)...
"%JDK_BIN%\javac.exe" -d "%OUT%" -cp "%BASE%" "%SRC%\*.java" "%GUI%\GameWindow.java" "%GUI%\EntityTracker.java" "%GUI%\EntityInspectorDialog.java"
if errorlevel 1 (
    echo.
    echo Compilazione FALLITA: controlla gli errori sopra prima di avviare la GUI.
    pause
    exit /b 1
)

echo Avvio GameWindow...
"%JDK_BIN%\java.exe" -cp "%OUT%" GameWindow > "%GUI%\run.log" 2>&1
