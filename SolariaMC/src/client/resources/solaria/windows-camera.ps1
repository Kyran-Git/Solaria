$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$camera = $null
$output = [Console]::OpenStandardOutput()
$writer = New-Object System.IO.BinaryWriter($output)
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $null = [Windows.Foundation.IAsyncAction, Windows.Foundation, ContentType=WindowsRuntime]
    # PowerShell cannot reliably select the WinRT overload from a __ComObject.
    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and -not $_.IsGenericMethod -and $_.GetParameters().Count -eq 1
    } | Select-Object -First 1
    function Wait-Action($action) {
        $task = $asTask.Invoke($null, @($action))
        $null = $task.GetAwaiter().GetResult()
    }
    $null = [Windows.Media.Capture.MediaCapture, Windows.Media.Capture, ContentType=WindowsRuntime]
    $null = [Windows.Media.Capture.MediaCaptureInitializationSettings, Windows.Media.Capture, ContentType=WindowsRuntime]
    $null = [Windows.Media.MediaProperties.ImageEncodingProperties, Windows.Media.MediaProperties, ContentType=WindowsRuntime]
    $null = [Windows.Storage.Streams.InMemoryRandomAccessStream, Windows.Storage.Streams, ContentType=WindowsRuntime]
    $settings = New-Object Windows.Media.Capture.MediaCaptureInitializationSettings
    # Video only: never initialize a microphone or record audio.
    $settings.StreamingCaptureMode = [Windows.Media.Capture.StreamingCaptureMode]::Video
    $camera = New-Object Windows.Media.Capture.MediaCapture
    Wait-Action ($camera.InitializeAsync($settings))
    $encoding = [Windows.Media.MediaProperties.ImageEncodingProperties]::CreateJpeg()
    $encoding.Width = 640
    $encoding.Height = 480
    # Pull protocol: Java requests one frame at a time. No files, network or clipboard.
    while ([Console]::ReadLine() -eq 'FRAME') {
        $stream = New-Object Windows.Storage.Streams.InMemoryRandomAccessStream
        $inputStream = $null
        $managed = $null
        try {
            Wait-Action ($camera.CapturePhotoToStreamAsync($encoding, $stream))
            if ($stream.Size -lt 1 -or $stream.Size -gt 2097152) { throw 'Frame size out of bounds' }
            $inputStream = $stream.GetInputStreamAt(0)
            $managed = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($inputStream)
            $bytes = New-Object byte[] ([int]$stream.Size)
            $offset = 0
            while ($offset -lt $bytes.Length) {
                $read = $managed.Read($bytes, $offset, $bytes.Length - $offset)
                if ($read -eq 0) { throw 'Incomplete frame' }
                $offset += $read
            }
            $writer.Write([int]$bytes.Length)
            $writer.Write($bytes)
            $writer.Flush()
        } finally {
            if ($null -ne $managed) { $managed.Dispose() }
            if ($null -ne $inputStream) { $inputStream.Dispose() }
            $stream.Dispose()
        }
    }
} catch {
    # Fixed status codes only; never emit device names, paths, frames or raw exceptions.
    $code = -1
    $errorObject = $_.Exception
    while ($null -ne $errorObject) {
        if ($errorObject.HResult -eq -2147024891) { $code = -2 }
        $errorObject = $errorObject.InnerException
    }
    $writer.Write([int]$code)
    $writer.Flush()
} finally {
    if ($null -ne $camera) { $camera.Dispose() }
    $writer.Dispose()
}