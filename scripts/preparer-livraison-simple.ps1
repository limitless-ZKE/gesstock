# Prepare la LIVRAISON SIMPLE : un seul dossier avec
#   gesstock.jar            le programme (avec son interface, construit par scripts\construire-pour-le-reseau.cmd)
#   base-de-lancement.sql   la base prete a l'emploi : toutes les tables, les donnees de reference, le compte MySQL de
#                           GesStock et l'administrateur (mot de passe aleatoire, jamais ecrit dans le dossier)
#   importer-la-base.cmd    met la base dans MySQL (WampServer) une seule fois
#   demarrer.cmd            lance GesStock
#   LISEZ-MOI.txt           le mode d'emploi en une page
# La base est fabriquee par GesStock lui-meme (ses migrations Flyway + la creation du premier administrateur) dans une base
# jetable de CE PC, puis exportee : elle correspond donc toujours exactement au programme livre. A refaire apres chaque
# changement de version (nouvelle migration).
# Prerequis sur ce PC : Java 17, WampServer demarre (MySQL, compte root sans mot de passe sauf -MotDePasseRoot), le jar construit.
# Texte volontairement sans accents : ce fichier est lu par PowerShell 5.1 sans connaitre son encodage.
param(
    [string]$Destination = (Join-Path ([Environment]::GetFolderPath('Desktop')) 'GesStock-Livraison-Simple'),
    [string]$Jar = (Join-Path $PSScriptRoot '..\target\gesstock-0.0.1-SNAPSHOT.jar'),
    [string]$NomBase = 'gesstock_db',
    [string]$Utilisateur = 'gesstock',
    [string]$Admin = 'admin',
    [string]$MotDePasseAdmin = '',
    [string]$MotDePasseBase = '',
    [string]$MotDePasseRoot = '',
    [int]$Port = 8080,
    [int]$PortSupervision = 8081,
    [int]$PortEssai = 8097,
    [int]$PortSupervisionEssai = 8098
)

$ErrorActionPreference = 'Stop'
$modeles = Join-Path $PSScriptRoot 'livraison-simple'
$racine = Split-Path -Parent $PSScriptRoot

function Nouveau-MotDePasse([int]$longueur) {
    # Sans caracteres ambigus (0 O 1 l I) ; uniquement des lettres et des chiffres : sans risque dans un .cmd ou une requete SQL.
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'.ToCharArray()
    $generateur = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
    $octets = New-Object byte[] 4
    $resultat = New-Object System.Text.StringBuilder
    for ($i = 0; $i -lt $longueur; $i++) {
        do { $generateur.GetBytes($octets); $valeur = [BitConverter]::ToUInt32($octets, 0) } while ($valeur -ge (4294967296 - (4294967296 % $alphabet.Length)))
        [void]$resultat.Append($alphabet[$valeur % $alphabet.Length])
    }
    return $resultat.ToString()
}

function Trouver-Java {
    $commande = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($commande) { return $commande.Source }
    foreach ($modele in @('C:\Program Files\Eclipse Adoptium\jdk-*', 'C:\Program Files\Java\jdk-*', 'C:\Program Files\Microsoft\jdk-*')) {
        $trouve = Get-ChildItem -Path $modele -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
        if ($trouve -and (Test-Path (Join-Path $trouve.FullName 'bin\java.exe'))) { return (Join-Path $trouve.FullName 'bin\java.exe') }
    }
    throw 'Java 17 est introuvable sur ce PC.'
}

function Trouver-Outil([string]$nom) {
    $commande = Get-Command $nom -ErrorAction SilentlyContinue
    if ($commande) { return $commande.Source }
    foreach ($modele in @("C:\wamp64\bin\mysql\mysql*\bin\$nom", "C:\Program Files\MySQL\MySQL Server *\bin\$nom", "C:\xampp\mysql\bin\$nom")) {
        $trouve = Get-ChildItem -Path $modele -ErrorAction SilentlyContinue | Sort-Object FullName | Select-Object -Last 1
        if ($trouve) { return $trouve.FullName }
    }
    throw "$nom est introuvable : WampServer est-il installe ?"
}

$java = Trouver-Java
$mysql = Trouver-Outil 'mysql.exe'
$mysqldump = Trouver-Outil 'mysqldump.exe'
if (-not (Test-Path -LiteralPath $Jar)) { throw "Le programme est introuvable : $Jar (lancez d'abord scripts\construire-pour-le-reseau.cmd)." }
$Jar = (Resolve-Path -LiteralPath $Jar).Path
if ((Test-Path -LiteralPath $Destination) -and (@(Get-ChildItem -LiteralPath $Destination -Force).Count -gt 0)) {
    throw "Le dossier $Destination existe deja et n'est pas vide : supprimez-le ou choisissez une autre destination (-Destination)."
}
if ($MotDePasseAdmin -eq '') { $MotDePasseAdmin = Nouveau-MotDePasse 12 }
if ($MotDePasseBase -eq '') { $MotDePasseBase = Nouveau-MotDePasse 20 }
if ($MotDePasseAdmin.Length -lt 12) { throw 'Le mot de passe de l''administrateur doit faire 12 caracteres au moins.' }
foreach ($valeur in @($NomBase, $Utilisateur, $Admin, $MotDePasseAdmin, $MotDePasseBase)) {
    if ($valeur -notmatch '^[A-Za-z0-9_]+$') { throw "Valeur refusee '$valeur' : uniquement des lettres, des chiffres et _ (elle est ecrite dans un .cmd et dans du SQL)." }
}
if ($MotDePasseRoot -ne '') { $env:MYSQL_PWD = $MotDePasseRoot }

function Mysql-Root([string]$requete) {
    # Retourne la sortie ; un echec leve une erreur avec le message de MySQL.
    $precedent = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $sortie = & $mysql -u root -N -B -e $requete 2>&1 | ForEach-Object { "$_" }
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $precedent }
    if ($code -ne 0) { throw "MySQL a refuse la requete ($code) : $($sortie -join ' ')" }
    return ($sortie -join "`n").Trim()
}

$suffixe = (Nouveau-MotDePasse 6).ToLower()
$baseJetable = "gesstock_prep_$suffixe"
$utilisateurJetable = "prep_$suffixe"
$motDePasseJetable = Nouveau-MotDePasse 20
$journaux = Join-Path $env:TEMP "gesstock-prep-$suffixe"
New-Item -ItemType Directory -Path $journaux -Force | Out-Null
$options = Join-Path $journaux 'dump.cnf'
$dumpBrut = Join-Path $journaux 'dump.sql'
$processus = $null

Write-Host "Fabrication de la base dans une base jetable ($baseJetable) ..."
try {
    [void](Mysql-Root 'SELECT 1')
    [void](Mysql-Root "CREATE DATABASE $baseJetable CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
    [void](Mysql-Root "CREATE USER '$utilisateurJetable'@'localhost' IDENTIFIED BY '$motDePasseJetable'")
    [void](Mysql-Root "GRANT ALL PRIVILEGES ON $baseJetable.* TO '$utilisateurJetable'@'localhost'")

    $env:SPRING_PROFILES_ACTIVE = 'prod'
    $env:SERVER_PORT = "$PortEssai"
    $env:MANAGEMENT_PORT = "$PortSupervisionEssai"
    $env:DB_URL = "jdbc:mysql://localhost:3306/${baseJetable}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
    $env:DB_USERNAME = $utilisateurJetable
    $env:DB_PASSWORD = $motDePasseJetable
    $env:CORS_ALLOWED_ORIGINS = "http://localhost:$PortEssai"
    $env:COOKIES_SECURE = 'false'
    $env:APP_ADMIN_USERNAME = $Admin
    $env:APP_ADMIN_PASSWORD = $MotDePasseAdmin
    $processus = Start-Process -FilePath $java -ArgumentList @('-Xmx512m', '-jar', "`"$Jar`"") -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $journaux 'gesstock.out.log') -RedirectStandardError (Join-Path $journaux 'gesstock.err.log')

    $pret = $false
    $limite = (Get-Date).AddMinutes(8)
    while ((Get-Date) -lt $limite) {
        if ($processus.HasExited) { throw "GesStock s'est arrete pendant la preparation (code $($processus.ExitCode)) : voir $journaux" }
        $precedent = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
        $nombre = & $mysql -u root -N -B -e "SELECT COUNT(*) FROM $baseJetable.utilisateurs WHERE username='$Admin'" 2>$null
        $ErrorActionPreference = $precedent
        if ("$nombre".Trim() -eq '1') { $pret = $true; break }
        Start-Sleep -Seconds 3
    }
    if (-not $pret) { throw "Le premier administrateur n'a pas ete cree a temps : voir $journaux" }
    Start-Sleep -Seconds 3
    Stop-Process -Id $processus.Id -Force
    $processus.WaitForExit(20000) | Out-Null

    Write-Host 'Exportation ...'
    Set-Content -LiteralPath $options -Encoding ASCII -Value @('[client]', 'host=localhost', "user=$utilisateurJetable", "password=`"$motDePasseJetable`"")
    $precedent = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    & $mysqldump "--defaults-extra-file=$options" --single-transaction --no-tablespaces --default-character-set=utf8mb4 --skip-dump-date "--result-file=$dumpBrut" $baseJetable 2>&1 | ForEach-Object { "$_" } | Out-Null
    $codeDump = $LASTEXITCODE
    $ErrorActionPreference = $precedent
    if ($codeDump -ne 0) { throw "mysqldump a echoue (code $codeDump)." }
} finally {
    if ($processus -and -not $processus.HasExited) { Stop-Process -Id $processus.Id -Force -ErrorAction SilentlyContinue }
    $precedent = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    & $mysql -u root -N -B -e "DROP DATABASE IF EXISTS $baseJetable" 2>$null | Out-Null
    & $mysql -u root -N -B -e "DROP USER IF EXISTS '$utilisateurJetable'@'localhost'" 2>$null | Out-Null
    $ErrorActionPreference = $precedent
    foreach ($nom in 'SPRING_PROFILES_ACTIVE', 'SERVER_PORT', 'MANAGEMENT_PORT', 'DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'CORS_ALLOWED_ORIGINS', 'COOKIES_SECURE', 'APP_ADMIN_USERNAME', 'APP_ADMIN_PASSWORD') {
        Remove-Item "Env:$nom" -ErrorAction SilentlyContinue
    }
}

# --- Controle de l'export ---
$dump = [IO.File]::ReadAllText($dumpBrut, (New-Object System.Text.UTF8Encoding($false)))
if ($dump -notmatch '-- Dump completed') { throw 'Export incomplet (la derniere ligne de mysqldump manque).' }
$tables = ([regex]::Matches($dump, '(?m)^CREATE TABLE `')).Count
if ($tables -lt 30) { throw "Export suspect : seulement $tables tables." }
if ($dump -notmatch 'INSERT INTO `utilisateurs`') { throw 'Export suspect : aucun utilisateur.' }
if ($dump -notmatch 'INSERT INTO `flyway_schema_history`') { throw 'Export suspect : l''historique des migrations manque.' }
if ($dump -match $MotDePasseAdmin) { throw 'Le mot de passe de l''administrateur apparait en clair dans l''export.' }
# Le nom du compte jetable figure dans l'historique de Flyway : on y met le vrai nom.
$dump = $dump.Replace("'$utilisateurJetable'", "'$Utilisateur'").Replace($baseJetable, $NomBase)

$version = 'inconnue'
$commit = (& git -C $racine rev-parse --short HEAD 2>$null)
if ($commit) { $version = "$(Get-Date -Format 'yyyy-MM-dd'), commit $commit" } else { $version = Get-Date -Format 'yyyy-MM-dd' }

$entete = @(
    '-- GesStock : base de lancement',
    "-- Version : $version",
    "-- Contenu : les $tables tables, les donnees de reference, le compte MySQL '$Utilisateur' (localhost) et l'administrateur '$Admin'.",
    "-- ATTENTION : cette importation EFFACE la base $NomBase si elle existe deja. Utilisez importer-la-base.cmd (il demande confirmation).",
    "CREATE DATABASE IF NOT EXISTS ``$NomBase`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;",
    "USE ``$NomBase``;",
    ''
) -join "`n"
$pied = @(
    '',
    "CREATE USER IF NOT EXISTS '$Utilisateur'@'localhost' IDENTIFIED BY '$MotDePasseBase';",
    "ALTER USER '$Utilisateur'@'localhost' IDENTIFIED BY '$MotDePasseBase';",
    "GRANT ALL PRIVILEGES ON ``$NomBase``.* TO '$Utilisateur'@'localhost';",
    'FLUSH PRIVILEGES;',
    ''
) -join "`n"

# --- Assemblage du dossier ---
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
Copy-Item -LiteralPath $Jar -Destination (Join-Path $Destination 'gesstock.jar')
[IO.File]::WriteAllText((Join-Path $Destination 'base-de-lancement.sql'), $entete + $dump + $pied, (New-Object System.Text.UTF8Encoding($false)))

$valeurs = @{
    'NOM_BASE' = $NomBase; 'UTILISATEUR' = $Utilisateur; 'MOT_DE_PASSE_BASE' = $MotDePasseBase; 'ADMIN' = $Admin
    'PORT' = "$Port"; 'PORT_SUPERVISION' = "$PortSupervision"; 'VERSION' = $version
}
foreach ($fichier in 'demarrer.cmd', 'importer-la-base.cmd', 'LISEZ-MOI.txt') {
    $texte = [IO.File]::ReadAllText((Join-Path $modeles $fichier), (New-Object System.Text.UTF8Encoding($false)))
    foreach ($cle in $valeurs.Keys) { $texte = $texte.Replace("{{$cle}}", $valeurs[$cle]) }
    if ($texte -match '\{\{[A-Z_]+\}\}') { throw "Un champ n'a pas ete remplace dans $fichier." }
    $texte = ($texte -replace "`r`n", "`n") -replace "`n", "`r`n"
    if ($fichier.EndsWith('.cmd')) {
        if ($texte -match '[^\x00-\x7F]') { throw "$fichier contient des caracteres non ASCII." }
        [IO.File]::WriteAllText((Join-Path $Destination $fichier), $texte, (New-Object System.Text.ASCIIEncoding))
    } else {
        [IO.File]::WriteAllText((Join-Path $Destination $fichier), $texte, (New-Object System.Text.UTF8Encoding($true)))
    }
}
Remove-Item -LiteralPath $journaux -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ''
Write-Host "Dossier pret : $Destination" -ForegroundColor Green
Get-ChildItem -LiteralPath $Destination | ForEach-Object { '  {0,-26} {1,12:N0} octets' -f $_.Name, $_.Length }
Write-Host ''
Write-Host "Administrateur de GesStock :  $Admin  /  $MotDePasseAdmin" -ForegroundColor Yellow
Write-Host '(ce mot de passe n''est ecrit nulle part dans le dossier : seule son empreinte est dans la base)'
