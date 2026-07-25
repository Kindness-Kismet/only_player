@echo off
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot"
set "ANDROID_HOME=E:\Android\Sdk"
set "ANDROID_SDK_ROOT=E:\Android\Sdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d E:\Downloads\only_player_src
call gradlew.bat :app:assembleDebug -PabiFilter=arm64-v8a --stacktrace --no-daemon
exit /b %ERRORLEVEL%
