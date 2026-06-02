@echo off
set ROOT_DIR=%~dp0

call "%ROOT_DIR%backend\gradlew.bat" -p "%ROOT_DIR%backend" %*
