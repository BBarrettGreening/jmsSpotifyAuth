@echo off

REM Script to set Spotify environment variables
REM Usage: set-env.bat [client_id] [client_secret]

if "%~1"=="" (
    echo Spotify Client ID is required
    echo Usage: set-env.bat [client_id] [client_secret]
    exit /b 1
)

if "%~2"=="" (
    echo Spotify Client Secret is required
    echo Usage: set-env.bat [client_id] [client_secret]
    exit /b 1
)

echo Setting Spotify environment variables...
set SPOTIFY_CLIENT_ID=%~1
set SPOTIFY_CLIENT_SECRET=%~2

echo Environment variables set successfully!
echo SPOTIFY_CLIENT_ID: %SPOTIFY_CLIENT_ID%
echo SPOTIFY_CLIENT_SECRET: %SPOTIFY_CLIENT_SECRET%

echo.
echo You can now run the application with: gradlew bootRun

exit /b 0
