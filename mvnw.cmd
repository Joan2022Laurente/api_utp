@echo off
setlocal

set "NETBEANS_MAVEN=C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"

if exist "%NETBEANS_MAVEN%" (
    call "%NETBEANS_MAVEN%" %*
) else (
    mvn %*
)
