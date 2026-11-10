<#
.SYNOPSIS
    Resolves everything needed to launch the JetBrains IDE MCP stdio bridge for
    the Solaria workspace.

.DESCRIPTION
    JetBrains IDEs (2025.2+) ship a built-in MCP server (Settings > Tools > MCP
    Server). It listens on a loopback SSE endpoint whose port can change between
    IDE restarts. The IDE also ships a small stdio <-> SSE bridge
    (com.intellij.mcpserver.stdio.McpStdioRunnerKt) that MCP clients can spawn.

    At launch time this script finds:
      * the port of the running IDE MCP server (probes http://127.0.0.1:<port>/sse),
      * the install directory of that IDE (so its bundled JBR java.exe is used),
      * the classpath of the stdio bridge (cached per IDE build),
      * the project path the IDE should run tool calls against.

    Default mode prints KEY=VALUE lines on stdout for jetbrains-mcp.cmd.
    -Check prints a human-readable report instead.

    Optional environment overrides:
      IJ_MCP_SERVER_PORT          Skip discovery and use this port.
      IJ_MCP_SERVER_PROJECT_PATH  Project the IDE should use (default: <Solaria>\SolariaMC).
      SOLARIA_JETBRAINS_HOME      IDE install directory (the folder holding bin\ and lib\).
      SOLARIA_JETBRAINS_PROCESS   Preferred IDE process names, comma separated
                                  (default: idea64,idea,pycharm64,pycharm).

    All diagnostics go to stderr, so stdout stays machine-readable.
#>
[CmdletBinding()]
param(
    [switch]$Check
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$SolariaRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$CacheDir = Join-Path $PSScriptRoot '.cache'
$MainClass = 'com.intellij.mcpserver.stdio.McpStdioRunnerKt'

# Mirrors com.intellij.mcpserver.McpStdioRunnerClasspath.CLASSPATH_CLASSES.
# The bridge classpath must include the jar that holds each of these classes.
$MarkerClasses = @(
    'com/intellij/mcpserver/stdio/ClassPathMarker',
    'io/modelcontextprotocol/kotlin/sdk/server/Server',
    'io/modelcontextprotocol/kotlin/sdk/shared/AbstractTransport',
    'io/modelcontextprotocol/kotlin/sdk/client/SseClientTransport',
    'io/modelcontextprotocol/kotlin/sdk/server/StdioServerTransport',
    'io/github/oshai/kotlinlogging/KotlinLogging',
    'kotlin/KotlinVersion',
    'kotlin/internal/jdk8/JDK8PlatformImplementations',
    'kotlin/internal/jdk7/JDK7PlatformImplementations',
    'kotlinx/coroutines/CoroutineScope',
    'kotlinx/coroutines/future/FutureKt',
    'kotlin/coroutines/jvm/internal/DebugProbesKt',
    'io/ktor/client/engine/cio/CIO',
    'io/ktor/client/HttpClient',
    'io/ktor/http/HttpStatusCode',
    'io/ktor/events/Events',
    'io/ktor/serialization/ContentConverter',
    'io/ktor/sse/ServerSentEvent',
    'io/ktor/http/cio/HttpHeadersMap',
    'io/ktor/network/sockets/Socket',
    'io/ktor/utils/io/ByteReadChannel',
    'io/ktor/websocket/WebSocketSession',
    'io/ktor/network/tls/TLSConfig',
    'io/ktor/util/AttributeKey',
    'kotlinx/coroutines/Dispatchers',
    'kotlinx/io/Buffer',
    'kotlinx/io/bytestring/ByteString',
    'kotlinx/serialization/Serializable',
    'kotlinx/serialization/json/Json',
    'org/slf4j/Logger',
    'org/slf4j/jul/JDK14LoggerAdapter'
) | ForEach-Object { "$_.class" }

function Write-Diag([string]$Message) {
    [Console]::Error.WriteLine("[jetbrains-mcp] $Message")
}

function Test-McpSsePort([int]$Port) {
    # The IDE MCP server answers GET /sse with 200 + text/event-stream and keeps
    # the stream open, so only the response headers are awaited.
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object System.Net.Http.HttpClient
    try {
        $request = New-Object System.Net.Http.HttpRequestMessage 'GET', "http://127.0.0.1:$Port/sse"
        $request.Headers.Add('Accept', 'text/event-stream')
        $task = $client.SendAsync($request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead)
        if (-not $task.Wait(2500)) { return $false }
        $response = $task.Result
        $contentType = $response.Content.Headers.ContentType
        return ([int]$response.StatusCode -eq 200 -and $null -ne $contentType -and $contentType.MediaType -eq 'text/event-stream')
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-IdeHomeFromProcess([System.Diagnostics.Process]$Process) {
    try {
        $exe = $Process.Path
        if ($exe) { return (Split-Path (Split-Path $exe -Parent) -Parent) }
    } catch { }
    return $null
}

function Find-McpServer {
    $preferred = if ($env:SOLARIA_JETBRAINS_PROCESS) { @($env:SOLARIA_JETBRAINS_PROCESS -split ',' | ForEach-Object { $_.Trim() }) }
                 else { @('idea64', 'idea', 'pycharm64', 'pycharm') }

    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalAddress -in @('127.0.0.1', '::1', '0.0.0.0', '::') })

    foreach ($name in $preferred) {
        foreach ($proc in @(Get-Process -Name $name -ErrorAction SilentlyContinue)) {
            $ports = @($listeners | Where-Object { $_.OwningProcess -eq $proc.Id } |
                Select-Object -ExpandProperty LocalPort -Unique | Sort-Object -Descending)
            foreach ($port in $ports) {
                if (Test-McpSsePort $port) {
                    return [pscustomobject]@{ Port = [int]$port; Process = $proc; IdeHome = (Get-IdeHomeFromProcess $proc) }
                }
            }
        }
    }
    return $null
}

function Find-IdeHomeFallback {
    $candidates = @()
    foreach ($root in @("$env:ProgramFiles\JetBrains", "$env:LOCALAPPDATA\Programs", "$env:LOCALAPPDATA\JetBrains\Toolbox\apps")) {
        if (Test-Path $root) {
            $candidates += @(Get-ChildItem $root -Directory -Recurse -Depth 3 -ErrorAction SilentlyContinue |
                Where-Object { Test-Path (Join-Path $_.FullName 'plugins\mcpserver\lib\mcpserver.jar') })
        }
    }
    $best = $candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($best) { return $best.FullName }
    return $null
}

function Get-IdeBuild([string]$IdeHome) {
    $buildFile = Join-Path $IdeHome 'build.txt'
    if (Test-Path $buildFile) { return (Get-Content $buildFile -Raw).Trim() }
    return 'unknown'
}

function Resolve-Classpath([string]$IdeHome) {
    $build = Get-IdeBuild $IdeHome
    $cacheFile = Join-Path $CacheDir 'classpath.txt'
    $cacheKey = "$IdeHome|$build"

    if (Test-Path $cacheFile) {
        $lines = @(Get-Content $cacheFile)
        if ($lines.Count -ge 2 -and $lines[0] -eq $cacheKey) {
            $missing = @($lines[1] -split ';' | Where-Object { -not (Test-Path $_) })
            if ($missing.Count -eq 0) { return $lines[1] }
        }
    }

    Write-Diag "Building stdio bridge classpath for $IdeHome (build $build)..."
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    # Plugin jars come first so the plugin's own copies win over copies in lib\.
    $jarFiles = @()
    $jarFiles += @(Get-ChildItem (Join-Path $IdeHome 'plugins\mcpserver\lib') -Filter *.jar -Recurse -ErrorAction SilentlyContinue)
    $jarFiles += @(Get-ChildItem (Join-Path $IdeHome 'lib') -Filter *.jar -Recurse -ErrorAction SilentlyContinue)

    $remaining = New-Object 'System.Collections.Generic.HashSet[string]'
    foreach ($c in $MarkerClasses) { [void]$remaining.Add($c) }
    $selected = New-Object 'System.Collections.Generic.List[string]'

    foreach ($jar in $jarFiles) {
        if ($remaining.Count -eq 0) { break }
        try {
            $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
            try {
                $hit = $false
                foreach ($entry in $zip.Entries) {
                    if ($remaining.Remove($entry.FullName)) { $hit = $true }
                }
                if ($hit) { $selected.Add($jar.FullName) }
            } finally { $zip.Dispose() }
        } catch { }
    }

    if ($remaining.Count -gt 0) {
        throw "Could not locate these MCP bridge classes in ${IdeHome}: $($remaining -join ', ')"
    }

    $classpath = $selected -join ';'
    New-Item -ItemType Directory -Force -Path $CacheDir | Out-Null
    Set-Content -Path $cacheFile -Value @($cacheKey, $classpath) -Encoding ASCII
    return $classpath
}

try {
    # 1. Port and IDE home.
    $ideHome = $env:SOLARIA_JETBRAINS_HOME
    $port = $null
    if ($env:IJ_MCP_SERVER_PORT) {
        $port = [int]$env:IJ_MCP_SERVER_PORT
        if (-not (Test-McpSsePort $port)) {
            Write-Diag "WARNING: IJ_MCP_SERVER_PORT=$port does not answer /sse with an event stream."
        }
    } else {
        $server = Find-McpServer
        if (-not $server) {
            throw ("No running JetBrains IDE MCP server found. Open $SolariaRoot\SolariaMC in IntelliJ IDEA and enable " +
                   "Settings > Tools > MCP Server > 'Enable MCP Server', or set IJ_MCP_SERVER_PORT.")
        }
        $port = $server.Port
        if (-not $ideHome) { $ideHome = $server.IdeHome }
    }
    if (-not $ideHome) { $ideHome = Find-IdeHomeFallback }
    if (-not $ideHome -or -not (Test-Path (Join-Path $ideHome 'plugins\mcpserver\lib\mcpserver.jar'))) {
        throw "Could not find a JetBrains IDE install with the mcpserver plugin. Set SOLARIA_JETBRAINS_HOME."
    }

    # 2. Java: the IDE's bundled JBR always matches the bridge's bytecode level.
    $java = Join-Path $ideHome 'jbr\bin\java.exe'
    if (-not (Test-Path $java)) { throw "Bundled JBR not found at $java" }

    # 3. Classpath.
    $classpath = Resolve-Classpath $ideHome

    # 4. Project path the IDE runs tool calls against.
    $projectPath = if ($env:IJ_MCP_SERVER_PROJECT_PATH) { $env:IJ_MCP_SERVER_PROJECT_PATH }
                   else { Join-Path $SolariaRoot 'SolariaMC' }

    if ($Check) {
        [Console]::Out.WriteLine("JetBrains MCP for Solaria - OK")
        [Console]::Out.WriteLine("  IDE home     : $ideHome (build $(Get-IdeBuild $ideHome))")
        [Console]::Out.WriteLine("  Java         : $java")
        [Console]::Out.WriteLine("  MCP SSE URL  : http://127.0.0.1:$port/sse")
        [Console]::Out.WriteLine("  Stream URL   : http://127.0.0.1:$port/stream")
        [Console]::Out.WriteLine("  Project path : $projectPath")
        [Console]::Out.WriteLine("  Main class   : $MainClass")
        [Console]::Out.WriteLine("  Classpath    :")
        foreach ($jar in $classpath -split ';') { [Console]::Out.WriteLine("    $jar") }
        exit 0
    }

    [Console]::Out.WriteLine("IJ_JAVA=$java")
    [Console]::Out.WriteLine("IJ_CP=$classpath")
    [Console]::Out.WriteLine("IJ_MAIN=$MainClass")
    [Console]::Out.WriteLine("IJ_MCP_SERVER_PORT=$port")
    [Console]::Out.WriteLine("IJ_MCP_SERVER_PROJECT_PATH=$projectPath")
    exit 0
} catch {
    Write-Diag "ERROR: $($_.Exception.Message)"
    exit 1
}
