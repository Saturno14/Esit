@echo off
setlocal

rem Avvia la simulazione completa di Esit: compila ed esegue il motore grafico
rem (motore-grafico\src\main\java\com\saturno14\esit\graphics\Main.java), che a sua volta apre
rem la finestra 3D e la console dei comandi. La simulazione vera e propria parte solo scrivendo
rem "NewStart" o "Load" nella console, come deciso.
rem
rem Si puo' lanciare da qualsiasi posizione (doppio click compreso): usa %~dp0 per trovare
rem la cartella motore-grafico rispetto alla posizione di questo script, non alla cartella
rem da cui viene lanciato.

set ESIT_ROOT=%~dp0
set MOTORE_DIR=%ESIT_ROOT%motore-grafico

where mvn >nul 2>nul
if errorlevel 1 (
    echo [avvia] Maven ^(mvn^) non trovato nel PATH. Installalo o apri un terminale dove "mvn" funziona.
    pause
    exit /b 1
)

if not exist "%MOTORE_DIR%\pom.xml" (
    echo [avvia] Non trovo %MOTORE_DIR%\pom.xml -- lo script deve stare nella root di Esit, accanto a "motore-grafico".
    pause
    exit /b 1
)

echo [avvia] Compilo ed eseguo il motore grafico...
pushd "%MOTORE_DIR%"
call mvn -q compile exec:java
set EXITCODE=%ERRORLEVEL%
popd

if not %EXITCODE%==0 (
    echo [avvia] Il motore grafico si e' chiuso con un errore ^(codice %EXITCODE%^).
    pause
)

endlocal
