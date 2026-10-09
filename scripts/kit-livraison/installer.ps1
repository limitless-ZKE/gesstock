# Installe GesStock comme service Windows "maison" : demarrage automatique avec le PC, relance en cas de panne,
# sauvegarde quotidienne de la base, ouverture du port dans le pare-feu. A lancer via installer.cmd (administrateur).
#   -Simulation    : affiche ce qui serait fait, sans rien modifier (ne demande pas les droits administrateur)
#   -Desinstaller  : retire les taches planifiees et la regle du pare-feu (les donnees et sauvegardes restent)
# Texte volontairement sans accents : ce fichier est lu par PowerShell 5.1 sans connaitre son encodage.
param(
    [switch]$Simulation,
    [switch]$Desinstaller
)

$ErrorActionPreference = 'Stop'
$dossier = $PSScriptRoot
$tacheServeur = 'GesStock - Serveur'
$tacheSauvegarde = 'GesStock - Sauvegarde'
$regleParefeu = 'GesStock (reseau local)'

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

# Lit les lignes "set NOM=valeur" de configuration.cmd (les lignes "rem" sont ignorees).
function Lire-Configuration {
    $valeurs = @{}
    foreach ($ligne in Get-Content -LiteralPath (Join-Path $dossier 'configuration.cmd')) {
        if ($ligne -match '^\s*set\s+([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            $valeurs[$Matches[1].ToUpper()] = $Matches[2].Trim()
        }
    }
    return $valeurs
}

function Est-Administrateur {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    return ([Security.Principal.WindowsPrincipal]$id).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
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
Ecrire '=== Installation de GesStock ===' 'Cyan'
if ($Simulation) { Ecrire 'MODE SIMULATION : rien ne sera modifie sur ce PC.' 'Yellow' }

if (-not $Simulation -and -not (Est-Administrateur)) {
    Ecrire 'ERREUR : cet installateur doit etre lance en administrateur. Utilisez installer.cmd (clic droit > Executer en tant qu''administrateur).' 'Red'
    exit 1
}

# ---------------------------------------------------------------- desinstallation
if ($Desinstaller) {
    Faire "Arret de GesStock" { & (Join-Path $dossier 'arreter.ps1') }
    foreach ($nom in @($tacheServeur, $tacheSauvegarde)) {
        Faire "Suppression de la tache planifiee '$nom'" {
            if (Get-ScheduledTask -TaskName $nom -ErrorAction SilentlyContinue) {
                Unregister-ScheduledTask -TaskName $nom -Confirm:$false
            }
        }
    }
    Faire "Suppression de la regle du pare-feu '$regleParefeu'" {
        Remove-NetFirewallRule -DisplayName $regleParefeu -ErrorAction SilentlyContinue
    }
    Ecrire ''
    Ecrire 'GesStock est desinstalle. Vos donnees (base MySQL) et vos sauvegardes n''ont pas ete touchees.' 'Green'
    exit 0
}

# ---------------------------------------------------------------- verifications
$config = Lire-Configuration
foreach ($cle in @('PORT', 'HEURE_SAUVEGARDE', 'MYSQL_MOT_DE_PASSE', 'ADRESSE_SERVEUR')) {
    if (-not $config.ContainsKey($cle) -or $config[$cle] -eq '') {
        Ecrire "ERREUR : $cle n'est pas renseigne dans configuration.cmd." 'Red'
        exit 1
    }
}
if ($config['MYSQL_MOT_DE_PASSE'] -eq 'CHANGEZ-MOI') {
    Ecrire 'ERREUR : modifiez d''abord configuration.cmd (adresse du serveur, mot de passe MySQL), puis relancez l''installation.' 'Red'
    exit 1
}
$port = [int]$config['PORT']
if ($config['HEURE_SAUVEGARDE'] -notmatch '^([01]?\d|2[0-3]):[0-5]\d$') {
    Ecrire "ERREUR : HEURE_SAUVEGARDE ('$($config['HEURE_SAUVEGARDE'])') doit etre au format HH:MM, par exemple 22:00." 'Red'
    exit 1
}
if (-not (Test-Path -LiteralPath (Join-Path $dossier 'gesstock.jar'))) {
    Ecrire 'ERREUR : gesstock.jar est absent de ce dossier.' 'Red'
    exit 1
}

$java = Trouver-Java
if (-not $java) {
    Ecrire 'ERREUR : Java 17 ou plus est introuvable. Installez "Temurin 17 JRE" (adoptium.net), puis relancez l''installation.' 'Red'
    exit 1
}
Ecrire "Java trouve : $java" 'Green'

# ---------------------------------------------------------------- installation
Ecrire ''
Ecrire 'Actions :'
Faire "Memorisation du chemin de Java (java.chemin.txt)" {
    Set-Content -LiteralPath (Join-Path $dossier 'java.chemin.txt') -Value $java -Encoding ASCII
}

# Serveur : demarre avec Windows (apres 1 minute, pour laisser MySQL se lancer), sans limite de duree, relance si arrete.
$lancer = Join-Path $dossier 'lancer.cmd'
$actionServeur = New-ScheduledTaskAction -Execute 'cmd.exe' -Argument ('/c ""' + $lancer + '""') -WorkingDirectory $dossier
$declencheur = New-ScheduledTaskTrigger -AtStartup
$declencheur.Delay = 'PT1M'
$reglagesServeur = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable `
    -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew
$compteSysteme = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
Faire "Tache planifiee '$tacheServeur' : demarrage automatique avec Windows (compte SYSTEM, relance en cas d'arret)" {
    Register-ScheduledTask -TaskName $tacheServeur -Action $actionServeur -Trigger $declencheur -Settings $reglagesServeur `
        -Principal $compteSysteme -Description 'Serveur GesStock (demarre avec Windows, relance automatique)' -Force | Out-Null
}

# Sauvegarde : chaque jour a l'heure choisie ; si le PC etait eteint, elle part des qu'il est rallume.
$sauvegarder = Join-Path $dossier 'sauvegarder-base.cmd'
$actionSauvegarde = New-ScheduledTaskAction -Execute 'cmd.exe' -Argument ('/c ""' + $sauvegarder + '""') -WorkingDirectory $dossier
$heure = [datetime]::ParseExact($config['HEURE_SAUVEGARDE'], 'H:mm', [Globalization.CultureInfo]::InvariantCulture)
$declencheurSauvegarde = New-ScheduledTaskTrigger -Daily -At $heure
$reglagesSauvegarde = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Hours 2) -MultipleInstances IgnoreNew
Faire "Tache planifiee '$tacheSauvegarde' : sauvegarde de la base chaque jour a $($config['HEURE_SAUVEGARDE'])" {
    Register-ScheduledTask -TaskName $tacheSauvegarde -Action $actionSauvegarde -Trigger $declencheurSauvegarde `
        -Settings $reglagesSauvegarde -Principal $compteSysteme -Description 'Sauvegarde quotidienne de la base GesStock' -Force | Out-Null
}

# Pare-feu : le port n'est ouvert qu'aux PC du meme reseau local, jamais a Internet.
Faire "Pare-feu : port $port ouvert aux PC du reseau local uniquement" {
    Remove-NetFirewallRule -DisplayName $regleParefeu -ErrorAction SilentlyContinue
    New-NetFirewallRule -DisplayName $regleParefeu -Direction Inbound -Action Allow -Protocol TCP -LocalPort $port `
        -RemoteAddress LocalSubnet -Profile Any | Out-Null
}

Faire "Demarrage de GesStock" {
    Start-ScheduledTask -TaskName $tacheServeur
}

if ($Simulation) {
    Ecrire ''
    Ecrire 'Fin de la simulation : rien n''a ete modifie.' 'Yellow'
    exit 0
}

# Attendre que GesStock reponde (le premier demarrage d'une base vide prend 2 a 3 minutes).
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
if ($pret) {
    Ecrire ''
    Ecrire "GesStock fonctionne. Ouvrez, depuis n'importe quel poste du reseau :  http://$($config['ADRESSE_SERVEUR']):$port" 'Green'
    Ecrire 'GesStock redemarrera tout seul avec ce PC, et la base sera sauvegardee chaque jour.' 'Green'
} else {
    Ecrire 'GesStock ne repond pas encore. Regardez journaux\gesstock.log (derniers messages) et journaux\lancer.log.' 'Red'
    Ecrire 'Causes habituelles : MySQL arrete, mot de passe ou nom de base incorrects dans configuration.cmd.' 'Red'
    exit 1
}
