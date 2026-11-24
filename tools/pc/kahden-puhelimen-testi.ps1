# AikaPolku: kahden laitteen testi (paikan laite + asukkaan puhelin)
#
# AUTOMAATTINEN (suositus): kääntää, asentaa ja ajaa testin kummassakin laitteessa yhtä aikaa.
#   powershell -ExecutionPolicy Bypass -File "$env:USERPROFILE\AndroidStudioProjects\AikaPolku\tarkistukset\aja-aikapolku.ps1" -branch <haara> -kaksiLaitetta
#   (kutsuu tätä skriptiä: -vaihe automaatti). Molemmat laitteet USB:llä ja samassa Wi-Fissä.
#   Paikan laite tunnistetaan tabletiksi; muuten anna -paikka <sarjanumero>.
#   Laitteiden omat tiedot otetaan talteen ja palautetaan testin lopuksi.
#
# KÄSIN:
#   1) Asennus ja lokien nollaus: ... kahden-puhelimen-testi.ps1 -vaihe asenna
#   2) Tee testi listan mukaan (HANDOFF.md).
#   3) Lokien keruu: ... kahden-puhelimen-testi.ps1 -vaihe lokit
#
# Tulokset ja lokit: tarkistukset\kaksi-puhelinta-<aika> (tai -kansio), josta Claude lukee ne.

param(
    [ValidateSet('asenna', 'lokit', 'automaatti')]
    [string]$vaihe = 'asenna',
    [string]$apk = "$env:USERPROFILE\Desktop\AikaPolku-APK\UUSIN-AikaPolku.apk",
    [string]$testApk = '',
    [string]$kansio = '',
    [string]$paikka = ''
)

$ErrorActionPreference = 'Continue'
# Lokien ä ja ö oikein (adb:n tuloste UTF-8).
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}
$projekti = "$env:USERPROFILE\AndroidStudioProjects\AikaPolku"
$paketti = 'fi.aikapolku.app'

# adb Android SDK:sta (kuten aja-aikapolku.ps1).
$sdk = $null
$lp = Get-Content "$projekti\local.properties" -ErrorAction SilentlyContinue | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
if ($lp) { $sdk = ($lp -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\' }
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { Write-Host "VIRHE: adb puuttuu ($adb)" -ForegroundColor Yellow; exit 1 }

& $adb start-server 2>$null | Out-Null
$laitteet = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' } | ForEach-Object { ($_ -split "`t")[0] })
if ($laitteet.Count -lt 2) {
    Write-Host "Puhelimia yhteydessä: $($laitteet.Count). Kytke molemmat USB:llä, avaa näytön lukitus ja salli USB-virheenkorjaus." -ForegroundColor Yellow
    & $adb devices -l
    exit 1
}

function Nimi($sarja) {
    $malli = (& $adb -s $sarja shell getprop ro.product.model 2>$null | Out-String).Trim()
    return "$malli ($sarja)"
}

# Asennus: koko vastaus talteen, uusi yritys puhelimen hyväksynnän jälkeen (OnePlus: "Asenna USB:n kautta").
function Asenna($s, $tiedosto, [switch]$testi) {
    $nimi = Nimi $s
    foreach ($yritys in 1..2) {
        if ($testi) { $vastaus = (& $adb -s $s install -r -t $tiedosto 2>&1 | Out-String).Trim() }
        else { $vastaus = (& $adb -s $s install -r $tiedosto 2>&1 | Out-String).Trim() }
        if ($vastaus -match 'Success') { return $true }
        Write-Host "  Asennus ei onnistunut ($nimi, yritys $yritys): $vastaus" -ForegroundColor Yellow
        if ($vastaus -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
            Write-Host "  Syy: laitteessa on eri allekirjoituksella tehty AikaPolku. Poista se ja aja uudelleen." -ForegroundColor Yellow
            return $false
        }
        if ($yritys -eq 1) {
            Write-Host "  Avaa laitteen näytön lukitus ja hyväksy asennus, jos laite kysyy. Uusi yritys 15 s kuluttua..." -ForegroundColor Cyan
            Start-Sleep -Seconds 15
        }
    }
    return $false
}

function WiFi($s) {
    $ssid = (& $adb -s $s shell cmd wifi status 2>$null | Out-String)
    $m = [regex]::Match($ssid, 'SSID:\s*"?([^",]+)"?')
    if (-not $m.Success) {
        $ssid = (& $adb -s $s shell dumpsys wifi 2>$null | Select-String -Pattern 'mWifiInfo SSID' | Select-Object -First 1 | Out-String)
        $m = [regex]::Match($ssid, 'SSID:\s*"?([^",]+)"?')
    }
    if ($m.Success) { return $m.Groups[1].Value.Trim() } else { return '' }
}

if ($vaihe -eq 'asenna') {
    if (-not (Test-Path $apk)) { Write-Host "VIRHE: APK puuttuu ($apk). Aja ensin aja-aikapolku.ps1." -ForegroundColor Yellow; exit 1 }
    $epaonnistui = @()
    foreach ($s in $laitteet) {
        $nimi = Nimi $s
        Write-Host "Asennetaan: $nimi"
        if (Asenna $s $apk) { Write-Host "  Asennettu." -ForegroundColor Green } else { $epaonnistui += $nimi }
        # Lokit tyhjiksi ja suurempi puskuri, jotta koko testi mahtuu.
        & $adb -s $s logcat -G 8M 2>$null | Out-Null
        & $adb -s $s logcat -c 2>$null | Out-Null
        $w = WiFi $s
        if ($w) { Write-Host "  Wi-Fi: $w" } else { Write-Host "  Wi-Fi: ei yhteyttä tai ei saatu luettua – tarkista, että Wi-Fi on päällä" -ForegroundColor Yellow }
    }
    Write-Host ""
    if ($epaonnistui.Count -gt 0) {
        Write-Host "Asennus epäonnistui: $($epaonnistui -join ', '). Kerro Claudelle yllä näkyvä virheteksti." -ForegroundColor Yellow
        exit 1
    }
    Write-Host "Valmis. Tarkista yltä, että molemmissa on sama Wi-Fi. Tee testi listan mukaan ja kirjaa kohtien kellonajat." -ForegroundColor Green
    exit 0
}

if ($vaihe -eq 'automaatti') {
    if (-not $testApk -or -not (Test-Path $testApk) -or -not (Test-Path $apk)) {
        Write-Host "VIRHE: APK:t puuttuvat. Aja: aja-aikapolku.ps1 -branch <haara> -kaksiLaitetta" -ForegroundColor Yellow
        exit 1
    }
    if (-not $kansio) { $kansio = "$projekti\tarkistukset\kaksi-puhelinta-$(Get-Date -Format 'yyyyMMdd-HHmm')" }
    New-Item -ItemType Directory -Force -Path $kansio | Out-Null
    $yhteenveto = "$kansio\kaksi-laitetta.txt"

    # Roolit: tabletti on paikan laite (tai -paikka <sarjanumero>).
    if (-not $paikka) {
        $tabletit = @($laitteet | Where-Object { (& $adb -s $_ shell getprop ro.build.characteristics 2>$null | Out-String) -match 'tablet' })
        if ($tabletit.Count -eq 1) { $paikka = $tabletit[0] }
    }
    if (-not $paikka -or $laitteet -notcontains $paikka) {
        Write-Host "VIRHE: paikan laitetta ei tunnistettu. Anna -paikka <sarjanumero>:" -ForegroundColor Yellow
        & $adb devices -l
        exit 1
    }
    if ($laitteet.Count -ne 2) {
        Write-Host "VIRHE: kytke täsmälleen kaksi laitetta (nyt $($laitteet.Count))." -ForegroundColor Yellow
        & $adb devices -l
        exit 1
    }
    $asukas = @($laitteet | Where-Object { $_ -ne $paikka })[0]
    Write-Host "Paikan laite:     $(Nimi $paikka)"
    Write-Host "Asukkaan puhelin: $(Nimi $asukas)"

    foreach ($s in @($paikka, $asukas)) {
        Write-Host "Asennetaan: $(Nimi $s)"
        if (-not (Asenna $s $apk) -or -not (Asenna $s $testApk -testi)) {
            "TULOS=VIRHE asennus $(Nimi $s)" | Out-File $yhteenveto -Encoding utf8
            Write-Host "Asennus epäonnistui. Kerro Claudelle yllä näkyvä virheteksti." -ForegroundColor Yellow
            exit 1
        }
        & $adb -s $s logcat -G 8M 2>$null | Out-Null
        & $adb -s $s logcat -c 2>$null | Out-Null
    }
    # Näytöt päälle ja auki koko testin ajaksi (palautetaan lopuksi).
    $stayon = @{}
    foreach ($s in @($paikka, $asukas)) {
        $stayon[$s] = (& $adb -s $s shell settings get global stay_on_while_plugged_in 2>$null | Out-String).Trim()
        & $adb -s $s shell svc power stayon usb 2>$null | Out-Null
        & $adb -s $s shell input keyevent KEYCODE_WAKEUP 2>$null | Out-Null
        & $adb -s $s shell wm dismiss-keyguard 2>$null | Out-Null
    }
    Start-Sleep -Seconds 2
    foreach ($s in @($paikka, $asukas)) {
        $lukko = (& $adb -s $s shell dumpsys window 2>$null | Select-String -Pattern 'isKeyguardShowing=true|mKeyguardShowing=true|KeyguardShowing=true' -Quiet)
        if ($lukko) {
            Write-Host "Avaa näytön lukitus laitteessa $(Nimi $s) ja paina Enter." -ForegroundColor Cyan
            [void](Read-Host)
        }
    }
    $wPaikka = WiFi $paikka; $wAsukas = WiFi $asukas
    Write-Host "Wi-Fi: paikan laite '$wPaikka', asukkaan puhelin '$wAsukas'"
    if (-not $wPaikka -or $wPaikka -ne $wAsukas) {
        Write-Host "HUOM: laitteet eivät näytä olevan samassa Wi-Fissä. Testi ajetaan silti." -ForegroundColor Yellow
    }

    $runner = 'fi.aikapolku.app.test/androidx.test.runner.AndroidJUnitRunner'
    Write-Host "Testi käynnissä (enintään noin 20 min). Pidä laitteet USB:llä ja näytöt auki; älä käytä laitteita testin aikana."
    $alku = Get-Date
    $pP = Start-Process -FilePath $adb -ArgumentList @('-s', $paikka, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'kaksi', 'true', '-e', 'class', 'fi.aikapolku.app.KaksiLaitettaPaikkaDeviceTest', $runner) `
        -RedirectStandardOutput "$kansio\paikka-instrument.txt" -RedirectStandardError "$kansio\paikka-virhe.txt" -NoNewWindow -PassThru
    Start-Sleep -Seconds 8
    $pA = Start-Process -FilePath $adb -ArgumentList @('-s', $asukas, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'kaksi', 'true', '-e', 'class', 'fi.aikapolku.app.KaksiLaitettaAsukasDeviceTest', $runner) `
        -RedirectStandardOutput "$kansio\asukas-instrument.txt" -RedirectStandardError "$kansio\asukas-virhe.txt" -NoNewWindow -PassThru
    $null = $pP.Handle; $null = $pA.Handle
    $raja = 25 * 60 * 1000
    $okA = $pA.WaitForExit($raja)
    $jaljella = [math]::Max(60000, $raja - [int]((Get-Date) - $alku).TotalMilliseconds)
    $okP = $pP.WaitForExit($jaljella)
    if (-not $okA) { try { $pA.Kill() } catch {} }
    if (-not $okP) { try { $pP.Kill() } catch {} }
    $kesto = [int]((Get-Date) - $alku).TotalSeconds
    foreach ($s in @($paikka, $asukas)) {
        $v = $stayon[$s]
        if ($v -match '^\d+$') { & $adb -s $s shell settings put global stay_on_while_plugged_in $v 2>$null | Out-Null }
        else { & $adb -s $s shell svc power stayon false 2>$null | Out-Null }
    }

    foreach ($pari in @(@('paikka', $paikka), @('asukas', $asukas))) {
        & $adb -s $pari[1] logcat -d -v time -s AikaPolkuKaksi:* 2>$null | Out-File "$kansio\$($pari[0])-kohdat.txt" -Encoding utf8
        & $adb -s $pari[1] logcat -d -v time 2>$null | Out-File "$kansio\$($pari[0])-logcat.txt" -Encoding utf8
    }
    $tP = Get-Content "$kansio\paikka-instrument.txt" -Raw -ErrorAction SilentlyContinue
    $tA = Get-Content "$kansio\asukas-instrument.txt" -Raw -ErrorAction SilentlyContinue
    $paikkaOk = $okP -and ($tP -match 'OK \(1 test\)')
    $asukasOk = $okA -and ($tA -match 'OK \(1 test\)')
    function Koodi($tiedosto) {
        $m = Select-String -Path $tiedosto -Pattern 'KOODI (\d{3} \d{3})' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($m) { return $m.Matches[0].Groups[1].Value } else { return '' }
    }
    $koodiP = Koodi "$kansio\paikka-kohdat.txt"
    $koodiA = Koodi "$kansio\asukas-kohdat.txt"
    $koodiOk = $koodiP -and ($koodiP -eq $koodiA)

    $rivit = @(
        "TULOS=$(if ($paikkaOk -and $asukasOk -and $koodiOk) { 'OK' } else { 'VIRHE' }) kesto=${kesto}s",
        "paikan laite: $(Nimi $paikka) $(if ($paikkaOk) { 'OK' } elseif (-not $okP) { 'AIKA LOPPUI' } else { 'VIRHE' })",
        "asukkaan puhelin: $(Nimi $asukas) $(if ($asukasOk) { 'OK' } elseif (-not $okA) { 'AIKA LOPPUI' } else { 'VIRHE' })",
        "koodi: paikan laite '$koodiP', asukkaan puhelin '$koodiA' $(if ($koodiOk) { 'SAMA' } else { 'EI TÄSMÄÄ' })",
        "Wi-Fi: '$wPaikka' / '$wAsukas'"
    )
    $rivit | Out-File $yhteenveto -Encoding utf8
    Write-Host ""
    $rivit | ForEach-Object { Write-Host $_ }
    Get-Content "$kansio\paikka-kohdat.txt", "$kansio\asukas-kohdat.txt" -ErrorAction SilentlyContinue |
        Where-Object { $_ -match ': (OK|VIRHE) ' } | ForEach-Object { ($_ -split 'AikaPolkuKaksi\(\s*\d+\):\s*', 2)[-1] } | Out-File $yhteenveto -Append -Encoding utf8
    Write-Host ""
    if ($paikkaOk -and $asukasOk -and $koodiOk) { Write-Host "KAHDEN LAITTEEN TESTI OK. Tulokset: $kansio" -ForegroundColor Green; exit 0 }
    Write-Host "Kahden laitteen testissä virheitä. Kerro Claudelle, se lukee tulokset kansiosta $kansio" -ForegroundColor Yellow
    exit 1
}

# Lokit
if (-not $kansio) { $kansio = "$projekti\tarkistukset\kaksi-puhelinta-$(Get-Date -Format 'yyyyMMdd-HHmm')" }
New-Item -ItemType Directory -Force -Path $kansio | Out-Null
foreach ($s in $laitteet) {
    $nimi = (Nimi $s) -replace '[^A-Za-z0-9_.()-]', '_'
    Write-Host "Kerätään: $nimi"
    & $adb -s $s logcat -d -v time 2>$null | Out-File "$kansio\$nimi-logcat.txt" -Encoding utf8
    & $adb -s $s shell dumpsys package $paketti 2>$null | Select-String -Pattern 'versionName|lastUpdateTime' | Out-File "$kansio\$nimi-versio.txt" -Encoding utf8
    & $adb -s $s shell dumpsys wifi 2>$null | Select-String -Pattern 'mWifiInfo' | Select-Object -First 1 | Out-File "$kansio\$nimi-wifi.txt" -Encoding utf8
    & $adb -s $s shell dumpsys alarm 2>$null | Select-String -Pattern $paketti | Out-File "$kansio\$nimi-halytykset.txt" -Encoding utf8
}
Write-Host "Lokit: $kansio" -ForegroundColor Green
Write-Host "Kerro Claudelle, mitkä kohdat eivät toimineet ja mihin aikaan."
