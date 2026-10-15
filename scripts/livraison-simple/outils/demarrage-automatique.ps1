# Installe le DEMARRAGE AUTOMATIQUE de GesStock :
#   - le serveur demarre avec Windows, sans fenetre et sans que personne ne se connecte (tache planifiee "GesStock - Serveur",
#     compte SYSTEM, relance en cas d'arret) ;
#   - le navigateur s'ouvre sur la page de connexion a l'ouverture de session de Windows, une fois le serveur pret
#     (raccourci du dossier Demarrage de tous les utilisateurs, qui lance outils\ouvrir-navigateur.vbs sans fenetre) ;
#   - le port est ouvert dans le pare-feu, pour les PC du reseau local seulement.
# A lancer via installer-demarrage-automatique.cmd (administrateur). Peut etre relance sans risque : il remplace l'installation
# precedente, arrete le GesStock en cours et le relance (pratique apres une mise a jour de gesstock.jar).
#   -Simulation    : affiche ce qui serait fait, sans rien modifier (ne demande pas les droits administrateur)
#   -Desinstaller  : retire la tache, le raccourci et la regle du pare-feu (la base et les fichiers restent)
# Texte volontairement sans accents : ce fichier est lu par PowerShell 5.1 sans connaitre son encodage.
param(
    [switch]$Simulation,
    [switch]$Desinstaller
)

$ErrorActionPreference = 'Stop'
$outils = $PSScriptRoot
$dossier = Split-Path -Parent $outils
$tacheServeur = 'GesStock - Serveur'
$regleParefeu = 'GesStock (reseau local)'
$raccourci = Join-Path ([Environment]::GetFolderPath('CommonStartup')) 'GesStock - ouvrir le navigateur.lnk'

function Ecrire([string]$texte, [string]$couleur = 'White') { Write-Host $texte -ForegroundColor $couleur }

# Execute une action, ou la decrit seulement en mode simulation.
function Faire([string]$description, [scriptblock]$action) {
    if ($Simulation) {
        Ecrire "[SIMULATION] $description" 'Yellow'
    } else {
        Ecrire "  - $description"
        & $action
    }
}

function Est-Administrateur {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    return ([Security.Principal.WindowsPrincipal]$id).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

# Le port est celui de demarrer.cmd (ligne "set PORT=...") : un seul endroit a modifier.
function Lire-Port {
    $ligne = Select-String -LiteralPath (Join-Path $dossier 'demarrer.cmd') -Pattern '^\s*set\s+PORT=(\d+)' | Select-Object -First 1
    if ($ligne) { return [int]$ligne.Matches[0].Groups[1].Value }
    return 8080
}

# Java 17 ou plus : cherche dans le PATH puis dans les dossiers d'installation habituels.
function Trouver-Java {
    $candidats = New-Object System.Collections.Generic.List[string]
    $dansPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($dansPath) { $candidats.Add($dansPath.Source) }
    foreach ($modele in @(
            'C:\Program Files\Eclipse Adoptium\*\bin\java.exe',
            'C:\Program Files\Java\*\bin\java.exe',
            'C:\Program Files\Microsoft\jdk-*\bin\java.exe',
            'C:\Program Files\Amazon Corretto\*\bin\java.exe',
            'C:\Program Files\Zulu\*\bin\java.exe')) {
        foreach ($f in Get-ChildItem -Path $modele -ErrorAction SilentlyContinue) { $candidats.Add($f.FullName) }
    }
    foreach ($chemin in $candidats) {
        $version = (Get-Item -LiteralPath $chemin).VersionInfo.FileMajorPart
        if ($version -ge 17) { return $chemin }
    }
    return $null
}

Ecrire ''
Ecrire '=== Demarrage automatique de GesStock ===' 'Cyan'
if ($Simulation) { Ecrire 'MODE SIMULATION : rien ne sera modifie sur ce PC.' 'Yellow' }

if (-not $Simulation -and -not (Est-Administrateur)) {
    Ecrire 'ERREUR : ceci doit etre lance en administrateur. Utilisez installer-demarrage-automatique.cmd (il demande les droits tout seul).' 'Red'
    exit 1
}

# ---------------------------------------------------------------- desinstallation
if ($Desinstaller) {
    Faire 'Arret de GesStock' { & (Join-Path $outils 'arreter.ps1') }
    Faire "Suppression de la tache planifiee '$tacheServeur'" {
        if (Get-ScheduledTask -TaskName $tacheServeur -ErrorAction SilentlyContinue) { Unregister-ScheduledTask -TaskName $tacheServeur -Confirm:$false }
    }
    Faire 'Suppression du raccourci qui ouvre le navigateur' { Remove-Item -LiteralPath $raccourci -Force -ErrorAction SilentlyContinue }
    Faire "Suppression de la regle du pare-feu '$regleParefeu'" { Remove-NetFirewallRule -DisplayName $regleParefeu -ErrorAction SilentlyContinue }
    Ecrire ''
    Ecrire 'Le demarrage automatique est retire. Votre base de donnees et vos fichiers n''ont pas ete touches.' 'Green'
    Ecrire 'Pour lancer GesStock a la main : demarrer.cmd. Pour le remettre en automatique : installer-demarrage-automatique.cmd.' 'Green'
    exit 0
}

# ---------------------------------------------------------------- verifications
foreach ($fichier in @('gesstock.jar', 'demarrer.cmd', 'outils\ouvrir-navigateur.vbs', 'outils\arreter.ps1')) {
    if (-not (Test-Path -LiteralPath (Join-Path $dossier $fichier))) {
        Ecrire "ERREUR : $fichier est absent de $dossier." 'Red'
        exit 1
    }
}
$port = Lire-Port
$java = Trouver-Java
if (-not $java) {
    Ecrire 'ERREUR : Java 17 ou plus est introuvable. Installez-le (adoptium.net, Temurin 17), puis relancez ce fichier.' 'Red'
    exit 1
}
Ecrire "Java trouve : $java" 'Green'

$mysqlRepond = $false
try {
    $client = New-Object System.Net.Sockets.TcpClient
    $attente = $client.BeginConnect('127.0.0.1', 3306, $null, $null)
    $mysqlRepond = $attente.AsyncWaitHandle.WaitOne(2000) -and $client.Connected
    $client.Close()
} catch { }
if ($mysqlRepond) {
    Ecrire 'MySQL repond.' 'Green'
} else {
    Ecrire 'ATTENTION : MySQL ne repond pas (WampServer est-il demarre, icone verte ?). GesStock reessaiera toutes les 30 secondes.' 'Yellow'
}

# ---------------------------------------------------------------- installation
Ecrire ''
Ecrire 'Actions :'
Faire 'Arret du GesStock en cours (lance a la main ou par une installation precedente)' {
    $code = & (Join-Path $outils 'arreter.ps1')
}

# Serveur : demarre avec Windows (apres 1 minute, pour laisser MySQL se lancer), sans limite de duree, relance si arrete.
# Sous le compte SYSTEM, Windows ne montre aucune fenetre : rien ne s'affiche pendant le demarrage.
$demarrer = Join-Path $dossier 'demarrer.cmd'
$actionServeur = New-ScheduledTaskAction -Execute 'cmd.exe' -Argument ('/c ""' + $demarrer + '" silencieux"') -WorkingDirectory $dossier
$declencheur = New-ScheduledTaskTrigger -AtStartup
$declencheur.Delay = 'PT1M'
$reglagesServeur = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable `
    -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew
$compteSysteme = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
Faire "Tache planifiee '$tacheServeur' : GesStock demarre avec Windows, sans fenetre (compte SYSTEM, relance en cas d'arret)" {
    Register-ScheduledTask -TaskName $tacheServeur -Action $actionServeur -Trigger $declencheur -Settings $reglagesServeur `
        -Principal $compteSysteme -Description 'Serveur GesStock (demarre avec Windows, relance automatique)' -Force | Out-Null
}

# Navigateur : a l'ouverture de session de n'importe quel utilisateur, un petit script invisible attend que GesStock reponde,
# puis ouvre le navigateur par defaut sur la page de connexion.
Faire 'Raccourci de demarrage de Windows : le navigateur s''ouvre sur la page de connexion quand GesStock est pret' {
    $lien = (New-Object -ComObject WScript.Shell).CreateShortcut($raccourci)
    $lien.TargetPath = Join-Path $env:SystemRoot 'System32\wscript.exe'
    $lien.Arguments = '//B //Nologo "' + (Join-Path $outils 'ouvrir-navigateur.vbs') + '"'
    $lien.WorkingDirectory = $outils
    $lien.WindowStyle = 7
    $lien.Description = 'Ouvre GesStock dans le navigateur quand le serveur est pret'
    $lien.Save()
}

# Pare-feu : le port n'est ouvert qu'aux PC du meme reseau local, jamais a Internet. Indispensable ici : lance par Windows et
# non par un utilisateur, GesStock n'affiche pas la question "autoriser l'acces ?" et les autres postes seraient bloques.
Faire "Pare-feu : port $port ouvert aux PC du reseau local uniquement" {
    Remove-NetFirewallRule -DisplayName $regleParefeu -ErrorAction SilentlyContinue
    New-NetFirewallRule -DisplayName $regleParefeu -Direction Inbound -Action Allow -Protocol TCP -LocalPort $port `
        -RemoteAddress LocalSubnet -Profile Any | Out-Null
}

Faire 'Demarrage de GesStock maintenant (sans attendre un redemarrage)' {
    Start-ScheduledTask -TaskName $tacheServeur
}

if ($Simulation) {
    Ecrire ''
    Ecrire 'Fin de la simulation : rien n''a ete modifie.' 'Yellow'
    exit 0
}

# Attendre que GesStock reponde.
Ecrire ''
Ecrire "Attente du demarrage de GesStock (jusqu'a 6 minutes) " 'White'
$pret = $false
$limite = (Get-Date).AddMinutes(6)
while ((Get-Date) -lt $limite) {
    try {
        Invoke-WebRequest -Uri "http://127.0.0.1:$port/" -UseBasicParsing -TimeoutSec 5 | Out-Null
        $pret = $true
    } catch {
        # Une reponse HTTP, meme d'erreur, prouve que le serveur tourne ; seul un echec de connexion fait attendre.
        if ($_.Exception.Response) { $pret = $true }
    }
    if ($pret) { break }
    Write-Host -NoNewline '.'
    Start-Sleep -Seconds 5
}
Ecrire ''
if (-not $pret) {
    Ecrire 'GesStock ne repond pas encore. Regardez journaux\gesstock.log (derniers messages) et journaux\lancer.log.' 'Red'
    Ecrire 'Causes habituelles : MySQL arrete (WampServer), base non importee (importer-la-base.cmd).' 'Red'
    exit 1
}

Ecrire ''
Ecrire "GesStock fonctionne et demarrera tout seul avec ce PC, sans aucune fenetre :  http://localhost:$port" 'Green'
$adresses = @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' })
foreach ($adresse in $adresses) { Ecrire "  Depuis les autres postes du reseau :  http://$($adresse.IPAddress):$port" 'Green' }
Ecrire ''
Ecrire 'POUR VERIFIER : redemarrez ce PC (Demarrer > Redemarrer), connectez-vous a Windows et attendez : le navigateur s''ouvre tout seul' 'Cyan'
Ecrire 'sur la page de connexion de GesStock, deux a quatre minutes apres le demarrage du PC.' 'Cyan'
