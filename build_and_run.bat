@echo off
title Build and Run NOBG
color 0A

echo ===================================================
echo   NOBG - DANG BUILD VA CAI DAT UNG DUNG...
echo ===================================================
echo.

:: Chay lenh build cua Gradle
call gradlew.bat installDebug

:: Kiem tra xem build co thanh cong khong
if %ERRORLEVEL% equ 0 (
    echo.
    echo ===================================================
    echo   BUILD THANH CONG! DANG MO APP TREN DIEN THOAI...
    echo ===================================================
    adb shell am start -n com.nobg.app/.MainActivity
    echo.
    echo Hoan tat! Ban co the tat cua so nay.
) else (
    echo.
    color 0C
    echo ===================================================
    echo   CO LOI XAY RA TRONG QUA TRINH BUILD!
    echo ===================================================
)

echo.
pause
