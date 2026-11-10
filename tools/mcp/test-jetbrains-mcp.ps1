<#
.SYNOPSIS
    End-to-end smoke test for tools\mcp\jetbrains-mcp.cmd.

.DESCRIPTION
    Spawns the stdio launcher the same way an MCP client does, then:
    1. sends initialize and notifications/initialized,
    2. sends tools/list and prints the tool names,
    3. optionally calls one read-only tool (get_project_modules) to prove that
       tool calls reach the open SolariaMC project.
    Exits 0 on success and 1 on any failure or timeout.
#>
[CmdletBinding()]
param(
    [int]$TimeoutSeconds = 60,
    [switch]$SkipToolCall
)

$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'jetbrains-mcp.cmd'

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $env:ComSpec
$psi.Arguments = "/d /c `"`"$launcher`"`""
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

# Windows PowerShell 5.1 encodes child stdin with Console.InputEncoding, which
# defaults to UTF-8 *with* BOM here. The BOM breaks parsing of the first JSON-RPC
# line, so switch to BOM-less UTF-8 while the child is spawned.
$previousInputEncoding = [Console]::InputEncoding
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding $false
try {
    $proc = [System.Diagnostics.Process]::Start($psi)
} finally {
    [Console]::InputEncoding = $previousInputEncoding
}
$stderrTask = $proc.StandardError.ReadToEndAsync()
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

function Send-Message($obj) {
    $json = $obj | ConvertTo-Json -Depth 10 -Compress
    $proc.StandardInput.WriteLine($json)
    $proc.StandardInput.Flush()
}

function Receive-Response([int]$Id) {
    while ((Get-Date) -lt $deadline) {
        $lineTask = $proc.StandardOutput.ReadLineAsync()
        while (-not $lineTask.Wait(250)) {
            if ((Get-Date) -ge $deadline) { throw "Timed out waiting for response id=$Id" }
        }
        $line = $lineTask.Result
        if ($null -eq $line) { throw "Launcher closed stdout before response id=$Id" }
        if (-not $line.Trim()) { continue }
        $msg = $line | ConvertFrom-Json
        if ($msg.PSObject.Properties['id'] -and $msg.id -eq $Id) {
            if ($msg.PSObject.Properties['error'] -and $msg.error) { throw "Error for id=${Id}: $($msg.error | ConvertTo-Json -Compress)" }
            return $msg.result
        }
    }
    throw "Timed out waiting for response id=$Id"
}

$exitCode = 0
try {
    Send-Message @{ jsonrpc = '2.0'; id = 1; method = 'initialize'; params = @{
        protocolVersion = '2024-11-05'; capabilities = @{}; clientInfo = @{ name = 'solaria-smoke-test'; version = '1.0' } } }
    $init = Receive-Response 1
    Write-Output "initialize OK -> server: $($init.serverInfo.name) $($init.serverInfo.version), protocol $($init.protocolVersion)"
    Send-Message @{ jsonrpc = '2.0'; method = 'notifications/initialized' }

    Send-Message @{ jsonrpc = '2.0'; id = 2; method = 'tools/list'; params = @{} }
    $tools = @((Receive-Response 2).tools)
    Write-Output "tools/list OK -> $($tools.Count) tools"
    Write-Output ("  " + (($tools | ForEach-Object name | Sort-Object) -join ', '))

    if (-not $SkipToolCall -and ($tools | Where-Object name -eq 'get_project_modules')) {
        Send-Message @{ jsonrpc = '2.0'; id = 3; method = 'tools/call'; params = @{ name = 'get_project_modules'; arguments = @{} } }
        $call = Receive-Response 3
        $text = (@($call.content) | Where-Object type -eq 'text' | ForEach-Object text) -join "`n"
        if ($text.Length -gt 400) { $text = $text.Substring(0, 400) + '...' }
        Write-Output "tools/call get_project_modules OK ->"
        Write-Output "  $text"
    }
} catch {
    Write-Output "FAILED: $($_.Exception.Message)"
    $exitCode = 1
} finally {
    try { $proc.StandardInput.Close() } catch { }
    if (-not $proc.WaitForExit(5000)) { try { & taskkill.exe /T /F /PID $proc.Id 2>&1 | Out-Null } catch { } }
    if ($stderrTask.Wait(2000) -and $stderrTask.Result.Trim()) {
        $err = $stderrTask.Result.Trim()
        if ($exitCode -ne 0 -or $VerbosePreference -ne 'SilentlyContinue') { Write-Output "--- launcher stderr ---`n$err" }
    }
}
exit $exitCode
