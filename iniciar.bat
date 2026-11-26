@echo off
chcp 65001 >nul
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo Necesitas instalar Node.js primero: https://nodejs.org  ^(version LTS^)
  pause
  exit /b 1
)
if not exist node_modules (
  echo Instalando dependencias por primera vez...
  call npm install --no-audit --no-fund
)
start "" cmd /c "timeout /t 3 >nul & start http://localhost:3000"
node server.js
pause
