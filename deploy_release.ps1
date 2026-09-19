.\gradlew.bat assembleRelease; if ($?) { adb install -r .\app\build\outputs\apk\release\app-release.apk; adb shell am start -n com.velocity.browser/.MainActivity }
