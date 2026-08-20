[CmdletBinding()]
param(
    [ValidateSet("launch", "player-controls", "cast-dialog", "cast-receiver-gui")]
    [string]$Scenario = "launch",

    [ValidateSet("phone", "tv", "both")]
    [string]$Target = "both",

    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$KeepOpen
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$packageName = "me.yummydroid.app"
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$artifactRoot = Join-Path $env:TEMP "yummydroid-smoke\$runId"
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

if (-not (Test-Path -LiteralPath $adb)) {
    throw "ADB was not found: $adb"
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory)] [string]$Serial,
        [Parameter(Mandatory)] [string[]]$Arguments,
        [switch]$AllowFailure
    )

    if ($Serial -notmatch '^emulator-\d+$') {
        throw "Only Android Emulator serials are allowed: $Serial"
    }
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $adb -s $Serial @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "ADB failed with exit code ${exitCode}: adb -s $Serial $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return @($output)
}

function Get-Emulators {
    $result = @()
    foreach ($line in (& $adb devices -l)) {
        if ($line -notmatch '^(emulator-\d+)\s+device\b') {
            continue
        }
        $serial = $Matches[1]
        $characteristics = (Invoke-Adb -Serial $serial -Arguments @(
            "shell", "getprop", "ro.build.characteristics"
        )) -join ""
        $productName = (Invoke-Adb -Serial $serial -Arguments @(
            "shell", "getprop", "ro.product.name"
        )) -join ""
        $role = if (
            $characteristics -match '(^|,)tv(,|$)' -or
            $productName -match 'atv|(^|_)tv(_|$)'
        ) { "tv" } else { "phone" }
        $result += [pscustomobject]@{ Serial = $serial; Role = $role }
    }
    return $result
}

function Select-Emulators {
    param([Parameter(Mandatory)] [object[]]$Devices)

    $roles = if ($Target -eq "both") { @("phone", "tv") } else { @($Target) }
    $selected = foreach ($role in $roles) {
        $device = $Devices | Where-Object Role -eq $role | Select-Object -First 1
        if ($null -eq $device) {
            throw "No running '$role' emulator was found."
        }
        $device
    }
    return @($selected)
}

function Build-DebugApk {
    if ($SkipBuild) {
        return
    }
    Push-Location $repoRoot
    try {
        & .\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1 --console=plain
        if ($LASTEXITCODE -ne 0) {
            throw "Debug build failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

function Resolve-DebugApk {
    $outputDirectory = Join-Path $repoRoot "app\build\outputs\apk\debug"
    $metadataPath = Join-Path $outputDirectory "output-metadata.json"
    if (-not (Test-Path -LiteralPath $metadataPath)) {
        throw "output-metadata.json was not found. Run without -SkipBuild."
    }
    $metadata = Get-Content -Raw -LiteralPath $metadataPath | ConvertFrom-Json
    $outputFile = @($metadata.elements)[0].outputFile
    $apkPath = Join-Path $outputDirectory $outputFile
    if (-not (Test-Path -LiteralPath $apkPath)) {
        throw "The APK from output-metadata.json was not found: $apkPath"
    }
    return $apkPath
}

function Install-App {
    param(
        [Parameter(Mandatory)] [object]$Device,
        [Parameter(Mandatory)] [string]$ApkPath
    )

    Invoke-Adb -Serial $Device.Serial -Arguments @("shell", "am", "force-stop", $packageName) | Out-Null
    if (-not $SkipInstall) {
        Invoke-Adb -Serial $Device.Serial -Arguments @("install", "-r", $ApkPath) | Out-Null
    }
}

function Wait-Until {
    param(
        [Parameter(Mandatory)] [scriptblock]$Condition,
        [Parameter(Mandatory)] [int]$TimeoutSeconds,
        [Parameter(Mandatory)] [string]$FailureMessage
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        if (& $Condition) {
            return
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw $FailureMessage
}

function Start-App {
    param(
        [Parameter(Mandatory)] [object]$Device,
        [string[]]$ActivityArguments = @()
    )

    Invoke-Adb -Serial $Device.Serial -Arguments @("logcat", "-c") | Out-Null
    $arguments = @(
        "shell", "am", "start", "-n", "$packageName/.MainActivity"
    ) + $ActivityArguments
    Invoke-Adb -Serial $Device.Serial -Arguments $arguments | Out-Null
    Wait-Until -TimeoutSeconds 12 -FailureMessage "The app did not start on $($Device.Serial)." -Condition {
        $appPid = (Invoke-Adb -Serial $Device.Serial -Arguments @(
            "shell", "pidof", $packageName
        ) -AllowFailure) -join ""
        return -not [string]::IsNullOrWhiteSpace($appPid)
    }
}

function Assert-NoCrash {
    param([Parameter(Mandatory)] [object]$Device)

    $crashLog = (Invoke-Adb -Serial $Device.Serial -Arguments @(
        "logcat", "-d", "-b", "crash", "AndroidRuntime:E", "*:S"
    ) -AllowFailure) -join "`n"
    if ($crashLog -match [regex]::Escape($packageName)) {
        throw "A crash was detected on $($Device.Serial):`n$crashLog"
    }
}

function Get-ScreenSize {
    param([Parameter(Mandatory)] [object]$Device)

    $sizeOutput = (Invoke-Adb -Serial $Device.Serial -Arguments @(
        "shell", "wm", "size"
    )) -join "`n"
    if ($sizeOutput -notmatch '(\d+)x(\d+)') {
        throw "Could not resolve the screen size for $($Device.Serial): $sizeOutput"
    }
    return [pscustomobject]@{ Width = [int]$Matches[1]; Height = [int]$Matches[2] }
}

function Get-UiHierarchy {
    param([Parameter(Mandatory)] [object]$Device)

    $remotePath = "/sdcard/yummydroid-smoke-ui.xml"
    $localDirectory = Join-Path $artifactRoot $Device.Serial
    New-Item -ItemType Directory -Force -Path $localDirectory | Out-Null
    $localPath = Join-Path $localDirectory "ui-current.xml"
    Invoke-Adb -Serial $Device.Serial -Arguments @(
        "shell", "uiautomator", "dump", "--compressed", $remotePath
    ) | Out-Null
    Invoke-Adb -Serial $Device.Serial -Arguments @("pull", $remotePath, $localPath) | Out-Null
    return [xml](Get-Content -Raw -LiteralPath $localPath -Encoding UTF8)
}

function Convert-Bounds {
    param([Parameter(Mandatory)] [System.Xml.XmlElement]$Node)

    $bounds = $Node.GetAttribute("bounds")
    if ($bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
        return $null
    }
    return [pscustomobject]@{
        Left = [int]$Matches[1]
        Top = [int]$Matches[2]
        Right = [int]$Matches[3]
        Bottom = [int]$Matches[4]
        Width = [int]$Matches[3] - [int]$Matches[1]
        Height = [int]$Matches[4] - [int]$Matches[2]
    }
}

function Get-ClickableAncestor {
    param([Parameter(Mandatory)] [System.Xml.XmlElement]$Node)

    $current = $Node
    while ($null -ne $current -and $current.Name -eq "node") {
        if ($current.GetAttribute("clickable") -eq "true") {
            return $current
        }
        $current = $current.ParentNode
    }
    return $null
}

function Invoke-NodeTap {
    param(
        [Parameter(Mandatory)] [object]$Device,
        [Parameter(Mandatory)] [System.Xml.XmlElement]$Node
    )

    $target = Get-ClickableAncestor -Node $Node
    if ($null -eq $target) {
        $target = $Node
    }
    $bounds = Convert-Bounds -Node $target
    if ($null -eq $bounds -or $bounds.Width -le 0 -or $bounds.Height -le 0) {
        throw "The UI node has invalid bounds."
    }
    $x = [int](($bounds.Left + $bounds.Right) / 2)
    $y = [int](($bounds.Top + $bounds.Bottom) / 2)
    Invoke-Adb -Serial $Device.Serial -Arguments @("shell", "input", "tap", "$x", "$y") | Out-Null
}

function Find-UiNode {
    param(
        [Parameter(Mandatory)] [xml]$Hierarchy,
        [Parameter(Mandatory)] [string]$Pattern,
        [ValidateSet("text", "content-desc", "resource-id", "either")] [string]$Attribute = "either"
    )

    foreach ($node in $Hierarchy.SelectNodes("//node")) {
        $text = $node.GetAttribute("text")
        $description = $node.GetAttribute("content-desc")
        $resourceId = $node.GetAttribute("resource-id")
        $matches = switch ($Attribute) {
            "text" { $text -match $Pattern }
            "content-desc" { $description -match $Pattern }
            "resource-id" { $resourceId -match $Pattern }
            default { $text -match $Pattern -or $description -match $Pattern }
        }
        if ($matches) {
            return $node
        }
    }
    return $null
}

function Wait-UiNode {
    param(
        [Parameter(Mandatory)] [object]$Device,
        [Parameter(Mandatory)] [string]$Pattern,
        [ValidateSet("text", "content-desc", "resource-id", "either")] [string]$Attribute = "either",
        [int]$TimeoutSeconds = 15
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $hierarchy = Get-UiHierarchy -Device $Device
            $node = Find-UiNode -Hierarchy $hierarchy -Pattern $Pattern -Attribute $Attribute
            if ($null -ne $node) {
                return [pscustomobject]@{ Hierarchy = $hierarchy; Node = $node }
            }
        } catch {
            Assert-NoCrash -Device $Device
            if ([DateTime]::UtcNow -ge $deadline) {
                throw
            }
        }
        Start-Sleep -Milliseconds 350
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "UI node '$Pattern' did not appear on $($Device.Serial) within $TimeoutSeconds seconds."
}

function Invoke-FirstCatalogCard {
    param([Parameter(Mandatory)] [object]$Device)

    $screen = Get-ScreenSize -Device $Device
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        try {
            $hierarchy = Get-UiHierarchy -Device $Device
        } catch {
            Start-Sleep -Milliseconds 350
            continue
        }
        $candidates = foreach ($node in $hierarchy.SelectNodes('//node[@clickable="true"]')) {
            $bounds = Convert-Bounds -Node $node
            if ($null -eq $bounds) { continue }
            if ($bounds.Top -lt 80 -or $bounds.Bottom -gt ($screen.Height * 0.86)) { continue }
            if ($bounds.Width -lt ($screen.Width * 0.3) -or $bounds.Height -lt ($screen.Height * 0.15)) { continue }
            [pscustomobject]@{ Node = $node; Bounds = $bounds }
        }
        $card = $candidates | Sort-Object { $_.Bounds.Top }, { $_.Bounds.Left } | Select-Object -First 1
        if ($null -ne $card) {
            Invoke-NodeTap -Device $Device -Node $card.Node
            return
        }
        Start-Sleep -Milliseconds 350
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "The first catalog card was not found."
}

function Enter-PlayerFromDetails {
    param([Parameter(Mandatory)] [object]$Device)

    $playPattern = '^(\u0421\u043C\u043E\u0442\u0440\u0435\u0442\u044C|\u041F\u0440\u043E\u0434\u043E\u043B\u0436\u0438\u0442\u044C|Watch|Continue|\u0414\u0438\u0432\u0438\u0442\u0438\u0441\u044F|\u041F\u0440\u043E\u0434\u043E\u0432\u0436\u0438\u0442\u0438)$'
    $episodePattern = '^(\u0421\u0435\u0440\u0438\u044F|Episode|\u0421\u0435\u0440\u0456\u044F)\s*1$'
    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        Start-Sleep -Milliseconds 700
        try {
            $hierarchy = Get-UiHierarchy -Device $Device
        } catch {
            continue
        }
        $playNode = Find-UiNode -Hierarchy $hierarchy -Pattern $playPattern -Attribute text
        if ($null -eq $playNode) {
            $playNode = Find-UiNode -Hierarchy $hierarchy -Pattern $episodePattern -Attribute text
        }
        if ($null -ne $playNode) {
            Invoke-NodeTap -Device $Device -Node $playNode
            Start-Sleep -Seconds 2
            try {
                $resumeHierarchy = Get-UiHierarchy -Device $Device
                $resumeNode = Find-UiNode -Hierarchy $resumeHierarchy `
                    -Pattern '^(\u041F\u0440\u043E\u0434\u043E\u043B\u0436\u0438\u0442\u044C|Continue|\u041F\u0440\u043E\u0434\u043E\u0432\u0436\u0438\u0442\u0438)\s+\d' -Attribute text
                if ($null -ne $resumeNode) {
                    Invoke-NodeTap -Device $Device -Node $resumeNode
                }
            } catch {
            }
            return
        }
        Invoke-Adb -Serial $Device.Serial -Arguments @(
            "shell", "input", "swipe", "540", "1850", "540", "750", "300"
        ) | Out-Null
    }
    throw "The watch action was not found on the anime details screen."
}

function Save-Screenshot {
    param(
        [Parameter(Mandatory)] [object]$Device,
        [Parameter(Mandatory)] [string]$Name
    )

    $directory = Join-Path $artifactRoot $Device.Serial
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $remotePath = "/sdcard/$Name.png"
    $localPath = Join-Path $directory "$Name.png"
    Invoke-Adb -Serial $Device.Serial -Arguments @("shell", "screencap", "-p", $remotePath) | Out-Null
    Invoke-Adb -Serial $Device.Serial -Arguments @("pull", $remotePath, $localPath) | Out-Null
    return $localPath
}

function Save-Diagnostics {
    param([Parameter(Mandatory)] [object]$Device)

    $directory = Join-Path $artifactRoot $Device.Serial
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    try { Save-Screenshot -Device $Device -Name "failure" | Out-Null } catch { }
    try {
        Invoke-Adb -Serial $Device.Serial -Arguments @("logcat", "-d") -AllowFailure |
            Set-Content -LiteralPath (Join-Path $directory "logcat.txt") -Encoding UTF8
    } catch { }
    try {
        Invoke-Adb -Serial $Device.Serial -Arguments @("shell", "dumpsys", "window") -AllowFailure |
            Set-Content -LiteralPath (Join-Path $directory "window.txt") -Encoding UTF8
    } catch { }
    try { Get-UiHierarchy -Device $Device | Out-Null } catch { }
}

function Test-LaunchScenario {
    param([Parameter(Mandatory)] [object]$Device)

    Start-App -Device $Device
    Start-Sleep -Milliseconds 800
    Assert-NoCrash -Device $Device
}

function Open-PhonePlayer {
    param([Parameter(Mandatory)] [object]$Device)

    if ($Device.Role -ne "phone") {
        throw "The player UI scenario requires a phone emulator."
    }

    Start-App -Device $Device
    Invoke-FirstCatalogCard -Device $Device
    Enter-PlayerFromDetails -Device $Device
}

function Wait-PlayerControls {
    param([Parameter(Mandatory)] [object]$Device)

    try {
        return Wait-UiNode -Device $Device `
            -Pattern '(^|:)id/exo_play_pause$' -Attribute "resource-id" -TimeoutSeconds 5
    } catch {
        Assert-NoCrash -Device $Device
        Invoke-Adb -Serial $Device.Serial -Arguments @("shell", "input", "tap", "540", "1000") | Out-Null
        return Wait-UiNode -Device $Device `
            -Pattern '(^|:)id/exo_play_pause$' -Attribute "resource-id" -TimeoutSeconds 8
    }
}

function Assert-PlayerControlsLayout {
    param([Parameter(Mandatory)] [object]$Controls)

    $adjacent = Find-UiNode -Hierarchy $Controls.Hierarchy `
        -Pattern '(^|:)id/yummy_episode_previous$' -Attribute "resource-id"
    if ($null -eq $adjacent) {
        $adjacent = Find-UiNode -Hierarchy $Controls.Hierarchy `
            -Pattern '(^|:)id/yummy_episode_next$' -Attribute "resource-id"
    }
    if ($null -eq $adjacent) {
        throw "Both adjacent episode controls are missing."
    }
    $playPauseBounds = Convert-Bounds -Node $Controls.Node
    $adjacentBounds = Convert-Bounds -Node $adjacent
    if ($null -eq $playPauseBounds -or $null -eq $adjacentBounds) {
        throw "The player controls have invalid bounds."
    }
    if ($playPauseBounds.Width -gt ($adjacentBounds.Width * 1.2)) {
        throw "The play/pause control is too large: $($playPauseBounds.Width) px vs $($adjacentBounds.Width) px."
    }
}

function Test-PlayerControlsScenario {
    param([Parameter(Mandatory)] [object]$Device)

    if ($Device.Role -ne "phone") {
        Test-LaunchScenario -Device $Device
        return
    }

    Open-PhonePlayer -Device $Device
    $controls = Wait-PlayerControls -Device $Device
    Assert-PlayerControlsLayout -Controls $controls
    Assert-NoCrash -Device $Device
    $screenshot = Save-Screenshot -Device $Device -Name "player-controls"
    Write-Host "Final frame: $screenshot"
}

function Test-CastDialogScenario {
    param([Parameter(Mandatory)] [object]$Device)

    if ($Device.Role -ne "phone") {
        Test-LaunchScenario -Device $Device
        return
    }

    Open-PhonePlayer -Device $Device
    $controls = Wait-PlayerControls -Device $Device
    Assert-PlayerControlsLayout -Controls $controls
    $castNode = Find-UiNode -Hierarchy $controls.Hierarchy `
        -Pattern '(^|:)id/yummy_player_cast$' -Attribute "resource-id"
    if ($null -eq $castNode) {
        $castResult = Wait-UiNode -Device $Device `
            -Pattern '(^|:)id/yummy_player_cast$' -Attribute "resource-id" -TimeoutSeconds 8
        $castNode = $castResult.Node
    }
    Invoke-NodeTap -Device $Device -Node $castNode

    $dialogResult = Wait-UiNode -Device $Device `
        -Pattern '(^|:)id/mr_chooser_title$' -Attribute "resource-id" -TimeoutSeconds 10
    $screen = Get-ScreenSize -Device $Device
    $dialogBounds = Convert-Bounds -Node $dialogResult.Hierarchy.hierarchy.node
    if ($null -eq $dialogBounds -or $dialogBounds.Width -lt ($screen.Width * 0.75)) {
        throw "The Chromecast dialog is too narrow: $($dialogBounds.Width) px of $($screen.Width) px."
    }
    $route = Find-UiNode -Hierarchy $dialogResult.Hierarchy `
        -Pattern '(^|:)id/mr_chooser_route_name$' -Attribute "resource-id"
    $warning = Find-UiNode -Hierarchy $dialogResult.Hierarchy `
        -Pattern '(^|:)id/mr_chooser_wifi_warning_description$' -Attribute "resource-id"
    $progress = Find-UiNode -Hierarchy $dialogResult.Hierarchy `
        -Pattern '(^|:)id/mr_chooser_search_progress_bar$' -Attribute "resource-id"
    $confirmation = Find-UiNode -Hierarchy $dialogResult.Hierarchy `
        -Pattern '(^|:)id/mr_chooser_ok_button$' -Attribute "resource-id"
    if ($null -eq $route -and $null -eq $warning) {
        throw "The Chromecast dialog shows neither a receiver nor the empty-state message."
    }
    if ($null -ne $warning -and $null -eq $progress -and $null -eq $confirmation) {
        throw "The empty Chromecast dialog is neither searching nor showing its completed state."
    }
    if ($null -ne $route) {
        Write-Host "Discovered Cast receiver: $($route.GetAttribute('text'))"
    }

    Assert-NoCrash -Device $Device
    $screenshot = Save-Screenshot -Device $Device -Name "cast-dialog"
    Write-Host "Final frame: $screenshot"
    if (-not $KeepOpen) {
        Invoke-Adb -Serial $Device.Serial -Arguments @("shell", "input", "keyevent", "KEYCODE_BACK") | Out-Null
        Invoke-Adb -Serial $Device.Serial -Arguments @("shell", "input", "keyevent", "KEYCODE_BACK") | Out-Null
    }
}

function Test-CastReceiverGuiScenario {
    param([Parameter(Mandatory)] [object]$Device)

    if ($Device.Role -ne "tv") {
        Test-LaunchScenario -Device $Device
        return
    }

    Start-App -Device $Device -ActivityArguments @(
        "--es", "video_url", "https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8",
        "--es", "anime_title", "Cast receiver UI",
        "--es", "video_player", "Cast",
        "--es", "video_dubbing", "Test",
        "--es", "video_episode", "1",
        "--el", "video_id", "90001",
        "--el", "video_anime_id", "90000",
        "--ei", "video_index", "1"
    )
    $controls = Wait-PlayerControls -Device $Device
    foreach ($controlId in @("yummy_player_quality", "yummy_player_source")) {
        $control = Find-UiNode -Hierarchy $controls.Hierarchy `
            -Pattern "(^|:)id/${controlId}$" -Attribute "resource-id"
        if ($null -eq $control) {
            throw "The Cast receiver GUI is missing '$controlId'."
        }
    }
    Assert-NoCrash -Device $Device
}

$devices = @()
$selectedDevices = @()
$failed = $false

try {
    Build-DebugApk
    $apkPath = Resolve-DebugApk
    $devices = Get-Emulators
    $selectedDevices = Select-Emulators -Devices $devices

    foreach ($device in $selectedDevices) {
        Install-App -Device $device -ApkPath $apkPath
        $installStatus = if ($SkipInstall) { "using installed APK" } else { "installed $(Split-Path -Leaf $apkPath)" }
        Write-Host "[$($device.Role)] $($device.Serial): $installStatus"
    }

    foreach ($device in $selectedDevices) {
        switch ($Scenario) {
            "cast-dialog" { Test-CastDialogScenario -Device $device }
            "cast-receiver-gui" { Test-CastReceiverGuiScenario -Device $device }
            "player-controls" { Test-PlayerControlsScenario -Device $device }
            default { Test-LaunchScenario -Device $device }
        }
        Write-Host "[$($device.Role)] $($device.Serial): $Scenario OK"
    }
} catch {
    $failed = $true
    Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red
    foreach ($device in $selectedDevices) {
        Save-Diagnostics -Device $device
    }
    Write-Host "Diagnostics: $artifactRoot"
} finally {
    if (-not $KeepOpen) {
        foreach ($device in $selectedDevices) {
            Invoke-Adb -Serial $device.Serial -Arguments @(
                "shell", "am", "force-stop", $packageName
            ) -AllowFailure | Out-Null
        }
    }
}

$stopwatch.Stop()
Write-Host ("Completed in {0:N1} seconds." -f $stopwatch.Elapsed.TotalSeconds)
if ($failed) {
    exit 1
}
