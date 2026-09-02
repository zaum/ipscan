@echo off
setlocal
set JAVA_HOME=C:\Users\peter\scoop\apps\openjdk21\current
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "I:\APPLICATIONS\Angry IP Scanner\ipscan"

set GRADLE_CACHE=%USERPROFILE%\.gradle\caches\modules-2\files-2.1
set FX_BASE=%GRADLE_CACHE%\org.openjfx\javafx-base\21\a7e334fbb619859e1010cad06ddcf16e774c87a0\javafx-base-21-win.jar
set FX_CTRL=%GRADLE_CACHE%\org.openjfx\javafx-controls\21\76e295d84578a3d9688e092aae3f2ff1cf3e83a9\javafx-controls-21-win.jar
set FX_GFX=%GRADLE_CACHE%\org.openjfx\javafx-graphics\21\8083d97ea236079c82cf8e3463b62990340346d4\javafx-graphics-21-win.jar
set JNA=%GRADLE_CACHE%\net.java.dev.jna\jna\5.9.0\8f503e6d9b500ceff299052d6be75b38c7257758\jna-5.9.0.jar

set FX_MODULE_PATH=%FX_BASE%;%FX_CTRL%;%FX_GFX%
set CP=build\classes\java\main;build\resources\main;%JNA%

java ^
  --module-path "%FX_MODULE_PATH%" --add-modules javafx.controls,javafx.graphics,javafx.base ^
  --add-opens java.base/java.net=ALL-UNNAMED ^
  -Dfile.encoding=UTF-8 ^
  -cp "%CP%" ^
  net.azib.ipscan.Main

endlocal
