$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$testRoot = Join-Path $repoRoot 'build/player-audit/browser-harness'
New-Item -ItemType Directory -Force $testRoot | Out-Null
$browserPath = @(
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $browserPath) { throw 'Chrome or Edge is required for the offline script harness' }
$source = [IO.File]::ReadAllText((Join-Path $repoRoot 'data/src/main/java/me/yummydroid/app/data/AllohaSessionCapture.kt'))
$match = [regex]::Match($source, '(?s)ALLOHA_SESSION_CAPTURE_SCRIPT = """(.*?)"""\.trimIndent')
if (-not $match.Success) { throw 'Production capture script was not found' }
$script = $match.Groups[1].Value.Replace('${''$''}', '$')
$harness = [IO.File]::ReadAllText((Join-Path $repoRoot 'data/src/test/resources/alloha-session-capture-harness.js'))
$html = '<!doctype html><html><body><script>' + $harness + '</script><script>' + $script + '</script><script>' +
    'runCaptureHarness().then(() => { document.body.dataset.testResult = "pass"; }, error => { document.body.dataset.testResult = "fail"; document.body.append(String(error.stack)); });' +
    '</script></body></html>'
$testPath = Join-Path $testRoot 'test.html'
[IO.File]::WriteAllText($testPath, $html)
$profilePath = Join-Path $testRoot 'profile'
$arguments = @('--headless', '--disable-gpu', '--disable-background-networking', '--no-first-run',
    '--disable-component-update', '--disable-default-apps', '--host-resolver-rules=MAP * 0.0.0.0',
    "--user-data-dir=$profilePath", '--virtual-time-budget=2000', '--dump-dom', ([uri]$testPath).AbsoluteUri)
$resultPath = Join-Path $testRoot 'result.html'
$process = Start-Process -FilePath $browserPath -ArgumentList ($arguments | ForEach-Object { '"' + $_ + '"' }) `
    -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput $resultPath -RedirectStandardError (Join-Path $testRoot 'stderr.log')
if ($process.ExitCode -ne 0) { throw "Browser test process exited with $($process.ExitCode)" }
$output = Get-Content -LiteralPath $resultPath
if (($output -join "`n") -notmatch '<body data-test-result="pass"') {
    throw "Capture script test failed. See $testRoot/result.html"
}
Write-Output 'Alloha capture script: PASS (offline browser, fake HTTP/WebSocket only)'
