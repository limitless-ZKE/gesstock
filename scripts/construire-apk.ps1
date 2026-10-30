# Construit l'application Android de GesStock et la depose, par defaut dans le dossier "GesStock-Android" du Bureau :
#   GesStock.apk    l'application a installer sur les telephones et tablettes Android (8.0 ou plus)
#   LISEZ-MOI.txt   comment l'installer et la relier au PC serveur
# L'application n'embarque aucune page : elle affiche l'interface servie par le PC serveur (scripts\construire-pour-le-reseau.cmd).
# Options :
#   -InstallerOutils   la premiere fois sur un PC : telecharge les outils Android (environ 300 Mo) dans %LOCALAPPDATA%\Android\Sdk
#   -Destination <dossier>
# Cle de signature : %USERPROFILE%\.gesstock\android, creee au premier passage. Une mise a jour de l'application ne s'installe
# par-dessus l'ancienne que si elle est signee avec la MEME cle : SAUVEGARDEZ ce dossier (il contient aussi son mot de passe).
# Prerequis : Java 17 (le meme que pour GesStock) et une connexion Internet au premier passage (outils et Gradle).
# Texte volontairement sans accents : ce fichier est lu par PowerShell 5.1 sans connaitre son encodage.
param(
    [string]$Destination = (Join-Path ([Environment]::GetFolderPath('Desktop')) 'GesStock-Android'),
    [switch]$InstallerOutils
)

$ErrorActionPreference = 'Stop'
$projet = Join-Path (Split-Path -Parent $PSScriptRoot) 'android'
$modeles = Join-Path $PSScriptRoot 'android'

# Outils Android en ligne de commande : version et empreinte SHA-256 publiees par Google.
$OutilsUrl = 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip'
$OutilsSha256 = '4d6931209eebb1bfb7c7e8b240a6a3cb3ab24479ea294f3539429574b1eec862'
$Composants = @('platform-tools', 'platforms;android-35', 'build-tools;34.0.0')

function Etape([string]$texte) {
    Write-Host ''
    Write-Host "=== $texte"
}

function Echec([string]$texte) {
    Write-Host ''
    Write-Host "ECHEC : $texte" -ForegroundColor Red
    exit 1
}

# --- 1. Java 17 -------------------------------------------------------------------------------------------------------
Etape '1/5  Java'
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) { Echec 'Java est introuvable. Installez Java 17 (le meme que pour GesStock), puis relancez.' }
$version = cmd /c "java -version 2>&1" | Select-Object -First 1
$majeur = 0
if ("$version" -match 'version "(\d+)') { $majeur = [int]$Matches[1] }
if ($majeur -lt 17) { Echec "Java 17 ou plus est necessaire (trouve : $version)." }
$keytool = Join-Path (Split-Path $java.Source) 'keytool.exe'
if (-not (Test-Path $keytool) -and $env:JAVA_HOME) { $keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe' }
if (-not (Test-Path $keytool)) { Echec 'keytool (fourni avec Java) est introuvable : installez un JDK 17 complet.' }
Write-Host "OK : $version"

# --- 2. Outils Android -------------------------------------------------------------------------------------------------
Etape '2/5  Outils Android'
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$sdkmanager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
if ($InstallerOutils) {
    if (-not (Test-Path $sdkmanager)) {
        $zip = Join-Path $env:TEMP 'gesstock-outils-android.zip'
        Write-Host 'Telechargement des outils Android (150 Mo)...'
        & curl.exe -sS -L --retry 3 -o $zip $OutilsUrl
        if ($LASTEXITCODE -ne 0) { Echec 'telechargement des outils Android impossible (connexion Internet ?).' }
        $empreinte = (Get-FileHash -Algorithm SHA256 $zip).Hash.ToLowerInvariant()
        if ($empreinte -ne $OutilsSha256) {
            Remove-Item $zip
            Echec "le fichier telecharge n'est pas celui de Google (empreinte $empreinte)."
        }
        $extrait = Join-Path $env:TEMP 'gesstock-outils-android'
        if (Test-Path $extrait) { Remove-Item -Recurse -Force $extrait }
        New-Item -ItemType Directory -Force $extrait | Out-Null
        & "$env:SystemRoot\System32\tar.exe" -xf $zip -C $extrait
        if ($LASTEXITCODE -ne 0) { Echec 'decompression des outils Android impossible.' }
        New-Item -ItemType Directory -Force (Join-Path $sdk 'cmdline-tools') | Out-Null
        Move-Item (Join-Path $extrait 'cmdline-tools') (Join-Path $sdk 'cmdline-tools\latest')
        Remove-Item $zip
        Remove-Item -Recurse -Force $extrait
    }
    Write-Host 'Acceptation des licences Android...'
    $oui = Join-Path $env:TEMP 'gesstock-oui.txt'
    Set-Content -Path $oui -Value (@('y') * 40) -Encoding ASCII
    $licences = Start-Process -FilePath $sdkmanager -ArgumentList "--sdk_root=`"$sdk`"", '--licenses' -RedirectStandardInput $oui `
        -RedirectStandardOutput (Join-Path $env:TEMP 'gesstock-licences.txt') -NoNewWindow -Wait -PassThru
    Remove-Item $oui
    if ($licences.ExitCode -ne 0) { Echec 'les licences Android n''ont pas pu etre acceptees.' }
    Write-Host 'Installation de la plateforme Android et des outils de construction (150 Mo)...'
    & $sdkmanager "--sdk_root=$sdk" @Composants
    if ($LASTEXITCODE -ne 0) { Echec 'installation des composants Android impossible.' }
}
if (-not (Test-Path (Join-Path $sdk 'licenses'))) {
    Echec "outils Android absents ($sdk). Relancez avec :  scripts\construire-apk.cmd -InstallerOutils"
}
$env:ANDROID_HOME = $sdk
Write-Host "OK : $sdk"

# --- 3. Cle de signature -----------------------------------------------------------------------------------------------
Etape '3/5  Cle de signature'
$dossierCle = Join-Path $env:USERPROFILE '.gesstock\android'
$proprietes = Join-Path $dossierCle 'cle.properties'
if (Test-Path $proprietes) {
    Write-Host "OK : cle existante dans $dossierCle"
} else {
    New-Item -ItemType Directory -Force $dossierCle | Out-Null
    $octets = New-Object byte[] 18
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($octets)
    $motDePasse = [Convert]::ToBase64String($octets) -replace '[+/=]', 'x'
    $magasin = Join-Path $dossierCle 'gesstock-android.jks'
    & $keytool -genkeypair -noprompt -keystore $magasin -storetype PKCS12 -alias gesstock -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $motDePasse -keypass $motDePasse -dname 'CN=GesStock'
    if ($LASTEXITCODE -ne 0) { Echec 'creation de la cle de signature impossible.' }
    Set-Content -Path $proprietes -Encoding ASCII -Value @('fichier=gesstock-android.jks', 'alias=gesstock', "motDePasse=$motDePasse")
    Write-Host "Cle creee dans $dossierCle"
    Write-Host 'SAUVEGARDEZ ce dossier : sans lui, une nouvelle version ne s''installera plus par-dessus l''application.' -ForegroundColor Yellow
}

# --- 4. Tests et construction ------------------------------------------------------------------------------------------
Etape '4/5  Tests et construction (plusieurs minutes la premiere fois : Gradle telecharge ses outils)'
Push-Location $projet
try {
    # Construction propre : une ressource renommee peut sinon rester introuvable dans une construction incrementale.
    & (Join-Path $projet 'gradlew.bat') --no-daemon --console=plain clean test assembleRelease
    if ($LASTEXITCODE -ne 0) { Echec 'la construction a echoue (voir les messages ci-dessus).' }
} finally {
    Pop-Location
}
$apk = Join-Path $projet 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { Echec "application construite introuvable : $apk" }

# --- 5. Depot ----------------------------------------------------------------------------------------------------------
Etape '5/5  Depot'
New-Item -ItemType Directory -Force $Destination | Out-Null
Copy-Item $apk (Join-Path $Destination 'GesStock.apk') -Force
Copy-Item (Join-Path $modeles 'LISEZ-MOI.txt') (Join-Path $Destination 'LISEZ-MOI.txt') -Force
Write-Host ''
Write-Host "TERMINE : $Destination"
Write-Host '  GesStock.apk    l''application a installer sur les telephones et tablettes Android'
Write-Host '  LISEZ-MOI.txt   comment l''installer et la relier au PC serveur'
