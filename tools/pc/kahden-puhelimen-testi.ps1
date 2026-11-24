# AikaPolku: kahden puhelimen testi (paikan laite + asukkaan puhelin)
#
# Käyttö (molemmat puhelimet USB:llä PC:ssä, USB-virheenkorjaus sallittu):
#   1) Asennus ja lokien nollaus:
#      powershell -ExecutionPolicy Bypass -File "$env:USERPROFILE\AndroidStudioProjects\AikaPolku\tarkistukset\kahden-puhelimen-testi.ps1" -vaihe asenna
#   2) Tee testi listan mukaan (puhelimet voi irrottaa USB:stä testin ajaksi).
#   3) Lokien keruu testin jälkeen (puhelimet taas USB:llä):
#      powershell -ExecutionPolicy Bypass -File "$env:USERPROFILE\AndroidStudioProjects\AikaPolku\tarkistukset\kahden-puhelimen-testi.ps1" -vaihe lokit
#
# Lokit tallentuvat kansioon tarkistukset\kaksi-puhelinta-<aika>, josta Claude lukee ne.

param(
    [ValidateSet('asenna', 'lokit')]
    [string]$vaihe = 'asenna'
)

$ErrorActionPreference = 'Continue'
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

if ($vaihe -eq 'asenna') {
    $apk = "$env:USERPROFILE\Desktop\AikaPolku-APK\UUSIN-AikaPolku.apk"
    if (-not (Test-Path $apk)) { Write-Host "VIRHE: APK puuttuu ($apk). Aja ensin aja-aikapolku.ps1." -ForegroundColor Yellow; exit 1 }
    $epaonnistui = @()
    foreach ($s in $laitteet) {
        $nimi = Nimi $s
        Write-Host "Asennetaan: $nimi"
        $ok = $false
        foreach ($yritys in 1..2) {
            # Koko vastaus talteen (myös virheteksti), jotta syy näkyy.
            $vastaus = (& $adb -s $s install -r $apk 2>&1 | Out-String).Trim()
            if ($vastaus -match 'Success') { $ok = $true; break }
            Write-Host "  Asennus ei onnistunut (yritys $yritys): $vastaus" -ForegroundColor Yellow
            if ($vastaus -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
                Write-Host "  Syy: puhelimessa on eri allekirjoituksella tehty AikaPolku. Poista se puhelimesta ja aja asennus uudelleen." -ForegroundColor Yellow
                break
            }
            if ($yritys -eq 1) {
                Write-Host "  Avaa puhelimen näytön lukitus ja hyväksy asennus puhelimessa, jos se kysyy (OnePlus: 'Asenna USB:n kautta')." -ForegroundColor Cyan
                Write-Host "  Yritetään uudelleen 15 sekunnin kuluttua..."
                Start-Sleep -Seconds 15
            }
        }
        if ($ok) { Write-Host "  Asennettu." -ForegroundColor Green } else { $epaonnistui += $nimi }
        # Lokit tyhjiksi ja suurempi puskuri, jotta koko testi mahtuu.
        & $adb -s $s logcat -G 8M 2>$null | Out-Null
        & $adb -s $s logcat -c 2>$null | Out-Null
        # Wi-Fi: uudemmissa Androideissa cmd wifi status, vanhemmissa dumpsys.
        $ssid = (& $adb -s $s shell cmd wifi status 2>$null | Out-String)
        $m = [regex]::Match($ssid, 'SSID:\s*"?([^",]+)"?')
        if (-not $m.Success) {
            $ssid = (& $adb -s $s shell dumpsys wifi 2>$null | Select-String -Pattern 'mWifiInfo SSID' | Select-Object -First 1 | Out-String)
            $m = [regex]::Match($ssid, 'SSID:\s*"?([^",]+)"?')
        }
        if ($m.Success) { Write-Host "  Wi-Fi: $($m.Groups[1].Value)" } else { Write-Host "  Wi-Fi: ei yhteyttä tai ei saatu luettua – tarkista, että Wi-Fi on päällä" -ForegroundColor Yellow }
    }
    Write-Host ""
    if ($epaonnistui.Count -gt 0) {
        Write-Host "Asennus epäonnistui: $($epaonnistui -join ', '). Kerro Claudelle yllä näkyvä virheteksti." -ForegroundColor Yellow
        exit 1
    }
    Write-Host "Valmis. Tarkista yltä, että molemmissa on sama Wi-Fi. Tee testi listan mukaan ja kirjaa kohtien kellonajat." -ForegroundColor Green
    exit 0
}

# Lokit
$aika = Get-Date -Format 'yyyyMMdd-HHmm'
$kansio = "$projekti\tarkistukset\kaksi-puhelinta-$aika"
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
