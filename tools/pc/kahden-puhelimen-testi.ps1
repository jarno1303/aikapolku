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
    foreach ($s in $laitteet) {
        Write-Host "Asennetaan: $(Nimi $s)"
        & $adb -s $s install -r $apk | Out-Host
        # Lokit tyhjiksi ja suurempi puskuri, jotta koko testi mahtuu.
        & $adb -s $s logcat -G 8M 2>$null | Out-Null
        & $adb -s $s logcat -c 2>$null | Out-Null
        $wifi = (& $adb -s $s shell dumpsys wifi 2>$null | Select-String -Pattern 'mWifiInfo SSID' | Select-Object -First 1 | Out-String).Trim()
        Write-Host "  verkko: $wifi"
    }
    Write-Host ""
    Write-Host "Valmis. Tarkista yltä, että molemmat ovat samassa wifissä (sama SSID). Tee testi listan mukaan ja kirjaa kohtien kellonajat." -ForegroundColor Green
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
