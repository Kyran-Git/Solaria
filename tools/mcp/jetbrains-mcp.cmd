@echo off
rem ---------------------------------------------------------------------------
rem  JetBrains IDE MCP server - stdio launcher for the Solaria workspace.
rem
rem  MCP clients spawn this file and talk JSON-RPC over stdin/stdout. It finds
rem  the running IntelliJ IDEA MCP server (port can change between restarts),
rem  then runs the IDE's own stdio<->SSE bridge with the IDE's bundled Java.
rem
rem  Usage:   jetbrains-mcp.cmd            (stdio MCP server)
rem           jetbrains-mcp.cmd --check    (print diagnostics and exit)
rem  See tools\mcp\README.md for overrides.
rem ---------------------------------------------------------------------------
setlocal

set "RESOLVER=%~dp0resolve-jetbrains-mcp.ps1"

if /i "%~1"=="--check" (
    powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%RESOLVER%" -Check <NUL
    exit /b %ERRORLEVEL%
)

rem stdin of the resolver is NUL so it can never consume the client's JSON-RPC stream.
for /f "usebackq tokens=1,* delims==" %%A in (`powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%RESOLVER%" ^<NUL`) do set "%%A=%%B"

if not defined IJ_JAVA (
    echo [jetbrains-mcp] Failed to resolve the JetBrains MCP server. Run "%~f0 --check" for details. 1>&2
    exit /b 1
)

"%IJ_JAVA%" -classpath "%IJ_CP%" %IJ_MAIN%
exit /b %ERRORLEVEL%
