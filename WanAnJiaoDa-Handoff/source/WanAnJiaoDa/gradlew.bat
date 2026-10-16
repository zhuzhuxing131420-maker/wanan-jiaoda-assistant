@echo off
setlocal
set DIR=%~dp0
if "%JAVA_HOME%"=="" (set JAVA_EXE=java.exe) else (set JAVA_EXE=%JAVA_HOME%\bin\java.exe)
"%JAVA_EXE%" -Dorg.gradle.appname=WanAnJiaoDa -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
