@echo off
setlocal
set JAVA_HOME=C:\Users\peter\AppData\Local\Temp\opencode\jdk21\jdk-21.0.11+10
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "I:\Angry IP Scanner\ipscan"
set SWT=C:\Users\peter\.gradle\caches\modules-2\files-2.1\org.eclipse.platform\org.eclipse.swt.win32.win32.x86_64\3.134.0\978f1c41438ebab37caf2e8d8446b420bf61c2a\org.eclipse.swt.win32.win32.x86_64-3.134.0.jar
set JNA=C:\Users\peter\.gradle\caches\modules-2\files-2.1\net.java.dev.jna\jna\5.9.0\8f503e6d9b500ceff299052d6be75b38c7257758\jna-5.9.0.jar
java -cp "build\classes\java\main;config;resources;%SWT%;%JNA%" net.azib.ipscan.Main
endlocal
