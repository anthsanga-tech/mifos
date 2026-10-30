@echo off
setlocal
REM Gradle 8.12 runs on JDK 21; the project toolchain still compiles/runs the app as Java 25 (same as Fineract).
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Zulu\zulu-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT"
set "GRADLE_HOME=%~dp0.gradle-dist\gradle-8.12.1"
"%GRADLE_HOME%\bin\gradle.bat" %*
