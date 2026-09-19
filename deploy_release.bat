@echo off
call .\gradlew.bat assembleRelease
if %ERRORLEVEL% EQU 0 (
    adb install -r .\app\build\outputs\apk\release\app-release.apk
    adb shell am start -n com.velocity.browser/.MainActivity
)
