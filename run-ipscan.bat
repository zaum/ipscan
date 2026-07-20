@echo off
setlocal
set JAVA_HOME=C:\Users\peter\scoop\apps\openjdk21\current
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "I:\Angry IP Scanner\ipscan"
set PLUGIN_JAR=..\ipscan-tasmota-plugin\build\libs\ipscan-tasmota-plugin-1.0.0.jar
set SWT=C:\Users\peter\.gradle\caches\modules-2\files-2.1\org.eclipse.platform\org.eclipse.swt.win32.win32.x86_64\3.134.0\978f1c41438ebab37caf2e8d8446b420bf61c2a\org.eclipse.swt.win32.win32.x86_64-3.134.0.jar
set JNA=C:\Users\peter\.gradle\caches\modules-2\files-2.1\net.java.dev.jna\jna\5.9.0\8f503e6d9b500ceff299052d6be75b38c7257758\jna-5.9.0.jar
java -Dipscan.plugins=org.angryip.plugins.tasmota.TasmotaFetcher -cp "build\classes\java\main;config;resources;%SWT%;%JNA%;%PLUGIN_JAR%" net.azib.ipscan.Main
endlocal
