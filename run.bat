@echo off
REM ============================================================================
REM  auto-instargram launcher (Windows)
REM
REM  This file is intentionally ASCII-only.
REM  cmd.exe reads .bat files using the system ANSI codepage (e.g. CP949 on
REM  Korean Windows), not UTF-8. Non-ASCII text in a UTF-8 .bat file gets split
REM  mid-character and cmd tries to run the fragments as commands -- which is
REM  exactly what happened before this was fixed.
REM
REM  So all human-facing output lives in run.ps1, which PowerShell reads as
REM  UTF-8 correctly on every locale.
REM ============================================================================

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1"
if errorlevel 1 (
  echo.
  echo Startup failed. See the messages above.
  pause
  exit /b 1
)
