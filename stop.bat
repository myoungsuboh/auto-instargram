@echo off
REM ============================================================================
REM  auto-instargram stopper (Windows)
REM
REM  ASCII-only on purpose -- see the comment in run.bat for why.
REM  All human-facing output lives in stop.ps1.
REM ============================================================================

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop.ps1"
