@echo off
title Mo Chrome Debug
echo ============================================================
echo   KHOI CHAY GOOGLE CHROME DEBUG CHO SHOPEE AFFILIATE FINDER
echo ============================================================
echo.
echo Luu y: Vui long dong tat ca cac cua so Chrome khac dang mo tren may truoc khi chay.
echo.

set "CHROME_PATH=C:\Program Files\Google\Chrome\Application\chrome.exe"
if not exist "%CHROME_PATH%" set "CHROME_PATH=C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
if not exist "%CHROME_PATH%" set "CHROME_PATH=%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe"

if not exist "%CHROME_PATH%" (
    echo [LOI] Khong tim thay Google Chrome cai dat tren may cua ban!
    echo Vui long lien he ho tro hoac tu chay Chrome qua shortcut.
    echo.
    pause
    exit /b
)

echo Dang mo Chrome...
start "" "%CHROME_PATH%" --remote-debugging-port=9222 --disable-blink-features=AutomationControlled --user-data-dir="%USERPROFILE%\chrome-debug-profile"
echo.
echo Mo Chrome Debug thanh cong! Bạn hay thuc hien dang nhap Shopee Affiliate
echo va vao trang Tim Kiem San Pham (Product Offer) tren cua so nay truoc khi chay App.
echo.
timeout /t 5
