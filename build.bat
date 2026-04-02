@echo off
SET JAVA_HOME=C:\Users\felix\.jdks\ms-21.0.8
SET PATH=%JAVA_HOME%\bin;%PATH%
C:\tools\maven\apache-maven-3.9.14\bin\mvn.cmd clean package > build_output.txt 2>&1
echo Build exit code: %ERRORLEVEL% >> build_output.txt
