# AikaPolku: kääntää haaran erillisessä tarkistuskansiossa, ajaa testit ja kopioi APK:n.
# Oma työkansio (AikaPolku) ei muutu: haara haetaan GitHubista kansioon AikaPolku-tarkistus.
# Lokit ja testitulokset tallentuvat kansioon AikaPolku\tarkistukset\<aika>, josta Claude lukee ne.
#
# Aja (PowerShell):
#   powershell -ExecutionPolicy Bypass -File "$env:USERPROFILE\AndroidStudioProjects\AikaPolku\tarkistukset\aja-aikapolku.ps1" -branch <haara>
# Lisäksi lint (hitaampi, ennen testattavaa versiota):
#   ... aja-aikapolku.ps1 -branch <haara> -taysi
# Vain osa testeistä (nimen osa, tähti = mitä tahansa):
#   ... aja-aikapolku.ps1 -branch <haara> -testit "*DayChanges*"
# Ilman testejä (pelkkä APK):
#   ... aja-aikapolku.ps1 -branch <haara> -eiTesteja
param(
    [string]$branch = "main",
    [string]$testit = "",
    [switch]$eiTesteja,
    [switch]$taysi
)
$repo  = "$env:USERPROFILE\AndroidStudioProjects\AikaPolku"
$wt    = "$env:USERPROFILE\AndroidStudioProjects\AikaPolku-tarkistus"
$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$logs  = "$repo\tarkistukset\$stamp"
$out   = "$env:USERPROFILE\Desktop\AikaPolku-APK"
New-Item -ItemType Directory -Force $logs, $out | Out-Null
$alku = Get-Date

# Java 17-23 (Android Studion oma jbr käy).
function Get-JavaMajor($dir) {
    $release = Join-Path $dir "release"
    if (-not (Test-Path (Join-Path $dir "bin\java.exe")) -or -not (Test-Path $release)) { return 0 }
    $line = Select-String -Path $release -Pattern '^JAVA_VERSION="([0-9]+)' | Select-Object -First 1
    if ($line) { return [int]$line.Matches[0].Groups[1].Value } else { return 0 }
}
function Find-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    foreach ($root in @("$env:ProgramFiles\Android", "${env:ProgramFiles(x86)}\Android", "$env:LOCALAPPDATA\Programs")) {
        if (Test-Path $root) {
            Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
                ForEach-Object { $candidates += (Join-Path $_.FullName "jbr") }
        }
    }
    foreach ($root in @("$env:USERPROFILE\.jdks", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft")) {
        if (Test-Path $root) {
            Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | ForEach-Object { $candidates += $_.FullName }
        }
    }
    $usable = $candidates | Where-Object { $_ } | Select-Object -Unique |
        ForEach-Object { [pscustomobject]@{ Dir = $_; Major = (Get-JavaMajor $_) } } |
        Where-Object { $_.Major -ge 17 -and $_.Major -le 23 }
    $best = $usable | Sort-Object @{ Expression = { $_.Major -eq 21 }; Descending = $true }, @{ Expression = { $_.Major }; Descending = $true } |
        Select-Object -First 1
    if ($best) { return $best.Dir }
    return $null
}
$java = Find-Java
if (-not $java) {
    Write-Host "VIRHE: sopivaa Javaa (17-23) ei löytynyt. Avaa projekti kerran Android Studiossa." -ForegroundColor Yellow
    "exit=1 java puuttuu" | Out-File "$logs\tulos.txt" -Encoding utf8
    exit 1
}
$env:JAVA_HOME = $java

# Haara erilliseen tarkistuskansioon (oma työkansio ei muutu).
Set-Location $repo
Write-Host "Haetaan haara $branch..."
git fetch -q origin $branch
if ($LASTEXITCODE -ne 0) { Write-Host "VIRHE: haaran haku epäonnistui"; "exit=1 fetch" | Out-File "$logs\tulos.txt" -Encoding utf8; exit 1 }
if (Test-Path "$wt\.git") {
    git -C $wt checkout -q --detach -f "origin/$branch"
} else {
    git worktree add -q --detach $wt "origin/$branch"
}
if ($LASTEXITCODE -ne 0) { Write-Host "VIRHE: tarkistuskansion valmistelu epäonnistui"; "exit=1 worktree" | Out-File "$logs\tulos.txt" -Encoding utf8; exit 1 }
Copy-Item "$repo\android\local.properties" "$wt\android\local.properties" -Force
$sha = (git -C $wt rev-parse --short HEAD).Trim()
Write-Host ("Versio: " + (git -C $wt log --oneline -1))

# Gradlen tehtävät. --tests koskee sitä edeltävää testitehtävää.
$tasks = ":app:assembleDebug"
if (-not $eiTesteja) {
    $tasks += " :app:testDebugUnitTest"
    foreach ($t in ($testit -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })) { $tasks += " --tests $t" }
}
if ($taysi) { $tasks += " :app:lintDebug" }
$android = "$wt\android"
$log = "$logs\aikapolku.log"
Remove-Item -Recurse -Force "$android\app\build\test-results" -ErrorAction SilentlyContinue

function Invoke-Build {
    # Gradle omana prosessinaan: "cmd /c" voi jäädä odottamaan Gradlen taustaprosessia (Aallon kokemus 12.11.).
    $cmdFile = Join-Path $logs "gradle.cmd"
    Set-Content $cmdFile "@call gradlew.bat $tasks --continue --console=plain > `"$log`" 2>&1`r`n@exit /b %ERRORLEVEL%" -Encoding ascii
    $p = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "`"$cmdFile`"" -WorkingDirectory $android -NoNewWindow -PassThru
    $null = $p.Handle
    $p.WaitForExit()
    return $p.ExitCode
}
Write-Host "Käännös: $tasks"
Write-Host "Loki: $log"
$code = Invoke-Build
if ($code -ne 0 -and (Select-String -Path $log -Pattern "used by another process|Unable to delete|käytössä toisessa prosessissa" -Quiet)) {
    Write-Host "Tiedosto lukossa: pysäytetään Gradle ja yritetään uudelleen..." -ForegroundColor Yellow
    Copy-Item $log "$log.eka-yritys" -Force
    Push-Location $android; cmd /c "gradlew.bat --stop > nul 2>&1"; Pop-Location
    $code = Invoke-Build
}
Set-Location $repo

$kesto = [int]((Get-Date) - $alku).TotalSeconds
"exit=$code kesto=${kesto}s haara=$branch sha=$sha tehtavat=$tasks" | Out-File "$logs\tulos.txt" -Encoding utf8
New-Item -ItemType Directory -Force "$logs\testit" | Out-Null
Copy-Item "$android\app\build\test-results\testDebugUnitTest\*.xml" "$logs\testit" -ErrorAction SilentlyContinue
if ($taysi) { Copy-Item "$android\app\build\reports\lint-results-debug.*" $logs -ErrorAction SilentlyContinue }

$apk = "$android\app\build\outputs\apk\debug\app-debug.apk"
$min = [math]::Floor($kesto / 60); $s = $kesto % 60
if ($code -eq 0 -and (Test-Path $apk)) {
    $nimi = ($branch -replace '^claude/', '' -replace '[^A-Za-z0-9._-]', '-')
    $name = "AikaPolku-$nimi-$sha.apk"
    Copy-Item $apk "$out\$name" -Force
    Copy-Item $apk "$out\UUSIN-AikaPolku.apk" -Force
    Get-ChildItem "$out\AikaPolku-*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -Skip 8 | Remove-Item -ErrorAction SilentlyContinue
    "apk $name" | Out-File "$logs\tulos.txt" -Append -Encoding utf8
    Write-Host "VALMIS ($min min $s s). APK: $out\$name (sama myös UUSIN-AikaPolku.apk)" -ForegroundColor Green
} else {
    Write-Host "Käännös ei mennyt läpi (exit=$code, $min min $s s). Kerro Claudelle, se lukee lokin kansiosta $logs" -ForegroundColor Yellow
}
exit $code
