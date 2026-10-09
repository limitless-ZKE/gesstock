# Controle d'installation de GesStock : verifie, point par point, tout ce dont depend le bon fonctionnement ET le demarrage
# automatique apres un redemarrage du serveur. A lancer via verifier-installation.cmd (administrateur), a la fin de
# l'installation et apres chaque redemarrage de test. Le rapport est aussi ecrit dans journaux\verification.txt.
#   -Dossier  : dossier d'installation a controler (par defaut : le dossier de ce script)
# Texte volontairement sans accents : ce fichier est lu par PowerShell 5.1 sans connaitre son encodage.
param(
    [string]$Dossier = $PSScriptRoot
)

$ErrorActionPreference = 'Continue'
$rapport = New-Object System.Collections.Generic.List[string]
$compte = @{ OK = 0; ATTENTION = 0; ERREUR = 0 }
$couleurs = @{ OK = 'Green'; ATTENTION = 'Yellow'; ERREUR = 'Red'; INFO = 'White' }

function Ecrire([string]$niveau, [string]$titre, [string]$detail = '') {
    if ($compte.ContainsKey($niveau)) { $compte[$niveau]++ }
    $etiquette = ('[{0}]' -f $niveau).PadRight(12)
    Write-Host ($etiquette + $titre) -ForegroundColor $couleurs[$niveau]
    $rapport.Add($etiquette + $titre)
    if ($detail) {
        foreach ($ligne in ($detail -split "`n")) {
            Write-Host ('             ' + $ligne) -ForegroundColor Gray
            $rapport.Add('             ' + $ligne)
        }
    }
}

function Section([string]$titre) {
    Write-Host ''
    Write-Host ('--- ' + $titre) -ForegroundColor Cyan
    $rapport.Add('')
    $rapport.Add('--- ' + $titre)
}

function Lire-Configuration {
    $valeurs = @{}
    $fichier = Join-Path $Dossier 'configuration.cmd'
    if (-not (Test-Path -LiteralPath $fichier)) { return $valeurs }
    foreach ($ligne in Get-Content -LiteralPath $fichier) {
        if ($ligne -match '^\s*set\s+([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $valeurs[$Matches[1].ToUpper()] = $Matches[2].Trim() }
    }
    return $valeurs
}

function Est-Administrateur {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    return ([Security.Principal.WindowsPrincipal]$id).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Appel-Web([string]$adresse) {
    # Trois essais : la toute premiere requete, apres une longue inactivite ou juste apres le demarrage, peut etre lente.
    for ($essai = 1; $essai -le 3; $essai++) {
        try {
            Invoke-WebRequest -Uri $adresse -UseBasicParsing -TimeoutSec 20 | Out-Null
            return $true
        } catch {
            # Une reponse HTTP, meme d'erreur, prouve que le serveur tourne ; seul un echec de connexion compte.
            if ($_.Exception.Response) { return $true }
            if ($essai -lt 3) { Start-Sleep -Seconds 3 }
        }
    }
    return $false
}

function Il-Y-A([datetime]$moment) {
    $duree = (Get-Date) - $moment
    if ($duree.TotalMinutes -lt 1) { return 'a l''instant' }
    if ($duree.TotalHours -lt 1) { return ('il y a {0} min' -f [int]$duree.TotalMinutes) }
    if ($duree.TotalDays -lt 1) { return ('il y a {0} h {1} min' -f [int]$duree.TotalHours, $duree.Minutes) }
    return ('il y a {0} jours' -f [int]$duree.TotalDays)
}

$admin = Est-Administrateur
$config = Lire-Configuration

Write-Host ''
Write-Host '=== Controle de l''installation de GesStock ===' -ForegroundColor Cyan
Write-Host ('Dossier controle : ' + $Dossier)
Write-Host ('Date et heure de ce PC : ' + (Get-Date -Format 'dd/MM/yyyy HH:mm') + '  (fuseau : ' + (Get-TimeZone).Id + ')')
$rapport.Add('Controle de l''installation de GesStock - ' + (Get-Date -Format 'dd/MM/yyyy HH:mm'))
$rapport.Add('Dossier controle : ' + $Dossier)
if (-not $admin) {
    Write-Host 'ATTENTION : ce controle n''est pas lance en administrateur ; les points marques "droits administrateur" ne seront pas verifies.' -ForegroundColor Yellow
    Write-Host '            Utilisez verifier-installation.cmd (clic droit > Executer en tant qu''administrateur).' -ForegroundColor Yellow
}

# ---------------------------------------------------------------- configuration
Section 'Reglages (configuration.cmd)'
$port = 8080
$portSupervision = 8081
if ($config.Count -eq 0) {
    Ecrire 'ERREUR' 'configuration.cmd est introuvable dans ce dossier.'
} else {
    if ($config['PORT'] -match '^\d+$') { $port = [int]$config['PORT'] }
    if ($config['PORT_SUPERVISION'] -match '^\d+$') { $portSupervision = [int]$config['PORT_SUPERVISION'] }
    if (-not $config.ContainsKey('MYSQL_MOT_DE_PASSE') -or $config['MYSQL_MOT_DE_PASSE'] -in @('', 'CHANGEZ-MOI')) {
        Ecrire 'ERREUR' 'Le mot de passe MySQL n''est pas renseigne.' 'Renseignez MYSQL_MOT_DE_PASSE dans configuration.cmd (partie 7 du guide).'
    } else {
        Ecrire 'OK' 'Mot de passe MySQL renseigne.'
    }
    if (-not $config.ContainsKey('ADRESSE_SERVEUR') -or $config['ADRESSE_SERVEUR'] -eq '') {
        Ecrire 'ERREUR' 'ADRESSE_SERVEUR n''est pas renseignee.'
    } else {
        Ecrire 'OK' ('Adresse du serveur : ' + $config['ADRESSE_SERVEUR'] + '   port : ' + $port)
    }
    $adminActif = (Select-String -LiteralPath (Join-Path $Dossier 'configuration.cmd') -Pattern '^\s*set\s+APP_ADMIN_PASSWORD=' -Quiet)
    if ($adminActif) {
        Ecrire 'ATTENTION' 'Les lignes APP_ADMIN_USERNAME / APP_ADMIN_PASSWORD sont encore actives dans configuration.cmd.' 'Apres la premiere connexion, remettez "rem " au debut de ces deux lignes (partie 9 du guide) : le mot de passe ne doit pas rester en clair.'
    } else {
        Ecrire 'OK' 'Les lignes du premier administrateur sont desactivees (le mot de passe n''est plus en clair).'
    }
}

# ---------------------------------------------------------------- Java
Section 'Java'
$java = $null
$fichierJava = Join-Path $Dossier 'java.chemin.txt'
if (Test-Path -LiteralPath $fichierJava) { $java = (Get-Content -LiteralPath $fichierJava -TotalCount 1).Trim() }
if ($java -and (Test-Path -LiteralPath $java)) {
    $version = (Get-Item -LiteralPath $java).VersionInfo.FileMajorPart
    if ($version -ge 17) { Ecrire 'OK' ('Java ' + $version + ' : ' + $java) }
    else { Ecrire 'ERREUR' ('Java ' + $version + ' trouve, mais il faut Java 17 ou plus : ' + $java) }
} else {
    Ecrire 'ERREUR' 'Java n''a pas ete memorise par l''installateur (java.chemin.txt absent ou chemin disparu).' 'Installez Java 17 (partie 1) puis relancez installer.cmd.'
}

# ---------------------------------------------------------------- MySQL
Section 'MySQL (la base de donnees)'
$services = @(Get-CimInstance Win32_Service | Where-Object { $_.Name -match '^(wampmysqld|mysql)' -or $_.DisplayName -match '^MySQL' })
if ($services.Count -eq 0) {
    Ecrire 'ERREUR' 'Aucun service MySQL trouve sur ce PC.' 'Avec WampServer, le service s''appelle "wampmysqld64". Si WampServer vient d''etre installe, lancez-le une fois (menu Demarrer > WampServer64).'
} else {
    foreach ($s in $services) {
        if ($s.State -eq 'Running' -and $s.StartMode -eq 'Auto') {
            Ecrire 'OK' ('Service ' + $s.Name + ' : en cours d''execution, demarrage AUTOMATIQUE avec Windows.')
        } elseif ($s.State -ne 'Running') {
            Ecrire 'ERREUR' ('Service ' + $s.Name + ' : ARRETE (etat : ' + $s.State + ').') 'Icone Wamp > "Demarrer tous les services", ou services.msc > clic droit > Demarrer.'
        } else {
            Ecrire 'ERREUR' ('Service ' + $s.Name + ' : demarrage "' + $s.StartMode + '" au lieu d''automatique : MySQL ne repartira pas tout seul apres un redemarrage.') 'services.msc > double-clic sur le service > Type de demarrage : Automatique > Appliquer.'
        }
    }
}

$portMysql = 3306
if ($config.ContainsKey('MYSQL_PORT') -and $config['MYSQL_PORT'] -match '^\d+$') { $portMysql = [int]$config['MYSQL_PORT'] }
$ecoute = @(netstat -an -p TCP | Where-Object { $_ -match (':' + $portMysql + '\s+\S+:0\s') })
if ($ecoute.Count -eq 0) {
    Ecrire 'ERREUR' ('Rien n''ecoute sur le port MySQL ' + $portMysql + '.') 'MySQL est arrete, ou utilise un autre port (verifiez MYSQL_PORT dans configuration.cmd).'
} else {
    $ouvertAuReseau = @($ecoute | Where-Object { $_ -match '\s(0\.0\.0\.0|\[::\]):' + $portMysql }).Count -gt 0
    if ($ouvertAuReseau) {
        Ecrire 'ATTENTION' ('MySQL (port ' + $portMysql + ') accepte les connexions de tout le reseau.') 'Fermez-le au reseau : partie 3 du guide (ligne bind-address = 127.0.0.1 dans my.ini, puis redemarrer les services).'
    } else {
        Ecrire 'OK' ('MySQL (port ' + $portMysql + ') n''est accessible que depuis ce PC.')
    }
}

# acces a la base avec le compte de GesStock
$mysql = $null
foreach ($modele in @('C:\wamp64\bin\mysql\mysql*\bin\mysql.exe', 'C:\Program Files\MySQL\MySQL Server *\bin\mysql.exe', 'C:\xampp\mysql\bin\mysql.exe')) {
    $trouve = Get-ChildItem -Path $modele -ErrorAction SilentlyContinue | Sort-Object FullName | Select-Object -Last 1
    if ($trouve) { $mysql = $trouve.FullName; break }
}
if ($config.ContainsKey('MYSQLDUMP_EXE') -and $config['MYSQLDUMP_EXE']) {
    $candidat = Join-Path (Split-Path -Parent $config['MYSQLDUMP_EXE']) 'mysql.exe'
    if (Test-Path -LiteralPath $candidat) { $mysql = $candidat }
}
if ($config.ContainsKey('MYSQL_MOT_DE_PASSE') -and $config['MYSQL_MOT_DE_PASSE'] -notin @('', 'CHANGEZ-MOI')) {
    if (-not $mysql) {
        Ecrire 'ATTENTION' 'Client mysql.exe introuvable : l''acces a la base n''a pas pu etre essaye.'
    } else {
        $options = Join-Path $env:TEMP ('gesstock-controle-' + [guid]::NewGuid().ToString('N').Substring(0, 8) + '.cnf')
        try {
            $hote = if ($config.ContainsKey('MYSQL_HOTE')) { $config['MYSQL_HOTE'] } else { 'localhost' }
            $base = if ($config.ContainsKey('MYSQL_BASE')) { $config['MYSQL_BASE'] } else { 'gesstock_db' }
            $utilisateur = if ($config.ContainsKey('MYSQL_UTILISATEUR')) { $config['MYSQL_UTILISATEUR'] } else { 'gesstock' }
            Set-Content -LiteralPath $options -Encoding ASCII -Value @('[client]', "host=$hote", "port=$portMysql", "user=$utilisateur", ('password="' + $config['MYSQL_MOT_DE_PASSE'] + '"'))
            $sortie = & $mysql "--defaults-extra-file=$options" --batch --skip-column-names -e 'SELECT COUNT(*) FROM flyway_schema_history' $base 2>&1
            if ($LASTEXITCODE -eq 0 -and ("$sortie".Trim() -match '^\d+$')) {
                Ecrire 'OK' ('La base "' + $base + '" repond avec le compte "' + $utilisateur + '" (' + "$sortie".Trim() + ' mises a niveau appliquees).')
            } else {
                $message = ("$sortie" -replace '\s+', ' ').Trim()
                Ecrire 'ERREUR' ('La base "' + $base + '" refuse la connexion du compte "' + $utilisateur + '".') ($message + "`nVerifiez le mot de passe (partie 13 du guide : ALTER USER ...) et que la base existe (partie 4).")
            }
        } finally {
            Remove-Item -LiteralPath $options -Force -ErrorAction SilentlyContinue
        }
    }
}

# ---------------------------------------------------------------- GesStock
Section 'GesStock (le programme)'
if (Appel-Web ('http://127.0.0.1:' + $port + '/')) { Ecrire 'OK' ('GesStock repond sur ce PC (http://127.0.0.1:' + $port + ').') }
else { Ecrire 'ERREUR' ('GesStock ne repond pas sur le port ' + $port + '.') 'Voyez journaux\gesstock.log et journaux\lancer.log (partie 13 du guide).' }
if ($config.ContainsKey('ADRESSE_SERVEUR') -and $config['ADRESSE_SERVEUR']) {
    $adresseReseau = 'http://' + $config['ADRESSE_SERVEUR'] + ':' + $port + '/'
    if (Appel-Web $adresseReseau) { Ecrire 'OK' ('GesStock repond a l''adresse du reseau : ' + $adresseReseau) }
    else { Ecrire 'ERREUR' ('GesStock ne repond pas a ' + $adresseReseau) 'L''adresse est-elle bien celle de ce PC (point suivant) ?' }
}
if (Appel-Web ('http://127.0.0.1:' + $portSupervision + '/actuator/health')) { Ecrire 'OK' 'Le controle de sante interne de GesStock repond.' }
else { Ecrire 'ATTENTION' ('Le controle de sante interne (port ' + $portSupervision + ') ne repond pas : GesStock demarre peut-etre encore, ou le port est pris.') }

if (Test-Path -LiteralPath (Join-Path $Dossier 'journaux\gesstock.log')) {
    $dernier = (Get-Item -LiteralPath (Join-Path $Dossier 'journaux\gesstock.log')).LastWriteTime
    Ecrire 'INFO' ('Journal de GesStock mis a jour ' + (Il-Y-A $dernier) + ' (journaux\gesstock.log).')
}

# ---------------------------------------------------------------- reseau
Section 'Adresse du serveur sur le reseau'
$adresses = @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' })
if ($adresses.Count -eq 0) {
    Ecrire 'ERREUR' 'Ce PC n''a aucune adresse sur un reseau (cable ou Wi-Fi non connecte ?).'
} elseif ($config.ContainsKey('ADRESSE_SERVEUR') -and $config['ADRESSE_SERVEUR']) {
    $mienne = $adresses | Where-Object { $_.IPAddress -eq $config['ADRESSE_SERVEUR'] } | Select-Object -First 1
    if (-not $mienne) {
        Ecrire 'ERREUR' ('L''adresse ' + $config['ADRESSE_SERVEUR'] + ' n''est celle d''aucune carte reseau de ce PC.') ('Adresses de ce PC : ' + (($adresses | ForEach-Object { $_.IPAddress }) -join ', ') + "`nCorrigez ADRESSE_SERVEUR dans configuration.cmd, puis arreter.cmd et demarrer.cmd.")
    } else {
        Ecrire 'OK' ('L''adresse ' + $config['ADRESSE_SERVEUR'] + ' est bien celle de ce PC.')
        $interface = Get-NetIPInterface -InterfaceIndex $mienne.InterfaceIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue
        if ($interface -and $interface.Dhcp -eq 'Enabled') {
            Ecrire 'ATTENTION' 'Cette adresse est attribuee automatiquement (DHCP) : elle peut changer un jour.' 'Verifiez qu''elle est RESERVEE dans la box pour ce PC, ou rendez-la fixe (partie 6 du guide).'
        } elseif ($interface) {
            Ecrire 'OK' 'L''adresse est fixe (configuree a la main sur ce PC).'
        }
    }
}

# ---------------------------------------------------------------- demarrage automatique
Section 'Demarrage automatique (le test du redemarrage)'
$demarrageWindows = (Get-CimInstance Win32_OperatingSystem).LastBootUpTime
Ecrire 'INFO' ('Dernier demarrage de Windows : ' + $demarrageWindows.ToString('dd/MM/yyyy HH:mm') + ' (' + (Il-Y-A $demarrageWindows) + ').')
if (-not $admin) {
    Ecrire 'ATTENTION' 'Taches planifiees, pare-feu et processus GesStock : non verifiables (droits administrateur).'
} else {
    $tacheServeur = Get-ScheduledTask -TaskName 'GesStock - Serveur' -ErrorAction SilentlyContinue
    if (-not $tacheServeur) {
        Ecrire 'ERREUR' 'La tache planifiee "GesStock - Serveur" n''existe pas : GesStock ne repartira pas apres un redemarrage.' 'Lancez installer.cmd.'
    } else {
        $info = $tacheServeur | Get-ScheduledTaskInfo
        $auDemarrage = @($tacheServeur.Triggers | Where-Object { $_.CimClass.CimClassName -eq 'MSFT_TaskBootTrigger' }).Count -gt 0
        $compteSysteme = $tacheServeur.Principal.UserId -match 'SYSTEM|Syst'
        if ($auDemarrage -and $compteSysteme -and $tacheServeur.Settings.Enabled) {
            Ecrire 'OK' 'Tache "GesStock - Serveur" : lancee au demarrage de Windows, sans qu''aucune session soit ouverte (compte SYSTEM).'
        } else {
            Ecrire 'ERREUR' 'Tache "GesStock - Serveur" mal configuree (declencheur au demarrage, compte SYSTEM, activee).' 'Relancez installer.cmd : il la recree.'
        }
        Ecrire 'INFO' ('Etat de la tache : ' + $tacheServeur.State + ' ; dernier lancement : ' + $(if ($info.LastRunTime.Year -gt 2000) { $info.LastRunTime.ToString('dd/MM/yyyy HH:mm') } else { 'jamais' }) + '.')
    }
    $tacheSauvegarde = Get-ScheduledTask -TaskName 'GesStock - Sauvegarde' -ErrorAction SilentlyContinue
    if (-not $tacheSauvegarde) {
        Ecrire 'ERREUR' 'La tache planifiee "GesStock - Sauvegarde" n''existe pas : aucune sauvegarde automatique.' 'Lancez installer.cmd.'
    } else {
        $infoSauvegarde = $tacheSauvegarde | Get-ScheduledTaskInfo
        Ecrire 'OK' ('Tache "GesStock - Sauvegarde" presente ; prochaine sauvegarde : ' + $(if ($infoSauvegarde.NextRunTime.Year -gt 2000) { $infoSauvegarde.NextRunTime.ToString('dd/MM/yyyy HH:mm') } else { 'non planifiee' }) + '.')
    }
    $regle = Get-NetFirewallRule -DisplayName 'GesStock (reseau local)' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $regle) {
        Ecrire 'ERREUR' 'La regle de pare-feu "GesStock (reseau local)" n''existe pas : les autres postes ne pourront pas se connecter.' 'Lancez installer.cmd.'
    } elseif ($regle.Enabled -ne 'True' -or $regle.Action -ne 'Allow') {
        Ecrire 'ERREUR' 'La regle de pare-feu de GesStock est desactivee ou bloquante.'
    } else {
        $filtrePort = ($regle | Get-NetFirewallPortFilter).LocalPort
        if ("$filtrePort" -eq "$port") { Ecrire 'OK' ('Pare-feu : le port ' + $port + ' est ouvert aux PC du reseau local.') }
        else { Ecrire 'ERREUR' ('Pare-feu : la regle ouvre le port ' + $filtrePort + ' mais GesStock utilise le port ' + $port + '.') 'Relancez installer.cmd.' }
    }
    $racine = $Dossier.TrimEnd('\')
    $processus = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.IndexOf($racine, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and $_.CommandLine -match 'gesstock\.jar' })
    if ($processus.Count -eq 0) {
        Ecrire 'ERREUR' 'Le programme GesStock (gesstock.jar) ne tourne pas.'
    } else {
        $lance = ($processus | Sort-Object CreationDate | Select-Object -First 1).CreationDate
        if ($lance -ge $demarrageWindows) {
            Ecrire 'OK' ('GesStock a demarre APRES le dernier demarrage de Windows (a ' + $lance.ToString('HH:mm') + ') : il repart bien tout seul.')
        } else {
            Ecrire 'ATTENTION' ('GesStock tourne depuis ' + $lance.ToString('dd/MM/yyyy HH:mm') + ', AVANT le dernier demarrage de Windows : il n''a pas ete relance par le redemarrage.') 'Faites le test du redemarrage (partie 11 du guide), puis relancez ce controle.'
        }
        if ($processus.Count -gt 1) { Ecrire 'ATTENTION' ('Plusieurs copies de GesStock tournent (' + $processus.Count + ') : une seule doit rester. Lancez arreter.cmd puis demarrer.cmd.') }
        if ($tacheServeur -and $tacheServeur.State -ne 'Running') {
            Ecrire 'ATTENTION' 'GesStock tourne mais pas lance par la tache planifiee (lance a la main ?).' 'Lancez arreter.cmd puis demarrer.cmd pour que la tache en reprenne le controle.'
        }
    }
}

# ---------------------------------------------------------------- sauvegardes
Section 'Sauvegardes'
$copie = if ($config.ContainsKey('DOSSIER_COPIE_SAUVEGARDE')) { $config['DOSSIER_COPIE_SAUVEGARDE'] } else { '' }
if (-not $copie) {
    Ecrire 'ERREUR' 'Aucun second emplacement de sauvegarde (DOSSIER_COPIE_SAUVEGARDE) n''est configure.' 'Si le disque du serveur tombe en panne, les sauvegardes qui s''y trouvent sont perdues. Renseignez la lettre de la 2e cle USB (partie 7).'
} elseif (-not (Test-Path -LiteralPath (Split-Path -Qualifier $copie -ErrorAction SilentlyContinue))) {
    Ecrire 'ERREUR' ('Le lecteur de ' + $copie + ' est absent (cle USB debranchee ?).')
} else {
    try {
        New-Item -ItemType Directory -Force -Path $copie -ErrorAction Stop | Out-Null
        $essai = Join-Path $copie ('.essai-' + [guid]::NewGuid().ToString('N').Substring(0, 6))
        Set-Content -LiteralPath $essai -Value 'essai' -ErrorAction Stop
        Remove-Item -LiteralPath $essai -Force
        Ecrire 'OK' ('Le second emplacement de sauvegarde est present et accepte les ecritures : ' + $copie)
    } catch {
        Ecrire 'ERREUR' ('Impossible d''ecrire dans ' + $copie + ' : ' + $_.Exception.Message)
    }
}
$dossierSauvegardes = Join-Path $Dossier 'sauvegardes'
$dernierZip = $null
if (Test-Path -LiteralPath $dossierSauvegardes) { $dernierZip = Get-ChildItem -LiteralPath $dossierSauvegardes -Filter '*.zip' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1 }
if (-not $dernierZip) {
    Ecrire 'ATTENTION' 'Aucune sauvegarde n''existe encore.' 'Double-cliquez sur sauvegarder-base.cmd pour en faire une maintenant (partie 11 du guide).'
} else {
    $age = (Get-Date) - $dernierZip.LastWriteTime
    if ($age.TotalHours -le 30) { Ecrire 'OK' ('Derniere sauvegarde : ' + $dernierZip.Name + ' (' + (Il-Y-A $dernierZip.LastWriteTime) + ').') }
    else { Ecrire 'ATTENTION' ('Derniere sauvegarde : ' + $dernierZip.Name + ' (' + (Il-Y-A $dernierZip.LastWriteTime) + ') : plus d''un jour.') 'Verifiez journaux\sauvegarde.log et la tache "GesStock - Sauvegarde".' }
    if ($copie -and (Test-Path -LiteralPath $copie)) {
        if (Test-Path -LiteralPath (Join-Path $copie $dernierZip.Name)) { Ecrire 'OK' 'Cette sauvegarde est aussi sur le second emplacement (cle USB).' }
        else { Ecrire 'ATTENTION' 'Cette sauvegarde n''est pas sur le second emplacement (cle USB).' 'Verifiez journaux\sauvegarde.log.' }
    }
}

# ---------------------------------------------------------------- le PC
Section 'Le PC serveur'
$veille = @(powercfg /query SCHEME_CURRENT SUB_SLEEP STANDBYIDLE 2>$null | Select-String '0x[0-9a-fA-F]{8}' | ForEach-Object { [Convert]::ToInt32(($_.Matches[0].Value.Substring(2)), 16) })
if ($veille.Count -ge 2) {
    if ($veille[-2] -eq 0) { Ecrire 'OK' 'La mise en veille est desactivee (sur secteur).' }
    else { Ecrire 'ATTENTION' ('Le PC se met en veille apres ' + [int]($veille[-2] / 60) + ' minutes d''inactivite : un PC en veille n''est plus un serveur.') 'Parametres > Systeme > Alimentation > mise en veille : "Jamais".' }
} else {
    Ecrire 'INFO' 'Reglage de mise en veille non lu : verifiez-le a la main (Parametres > Systeme > Alimentation).'
}
$disque = Get-PSDrive -Name ($Dossier.Substring(0, 1)) -ErrorAction SilentlyContinue
if ($disque) {
    $libreGo = [math]::Round($disque.Free / 1GB, 1)
    if ($libreGo -ge 5) { Ecrire 'OK' ('Espace libre sur le disque ' + $Dossier.Substring(0, 2) + ' : ' + $libreGo + ' Go.') }
    else { Ecrire 'ATTENTION' ('Il ne reste que ' + $libreGo + ' Go libres sur le disque ' + $Dossier.Substring(0, 2) + '.') }
}

# ---------------------------------------------------------------- bilan
Write-Host ''
Write-Host '=== Bilan ===' -ForegroundColor Cyan
$rapport.Add('')
$rapport.Add('=== Bilan ===')
$resume = ('{0} verification(s) reussie(s), {1} point(s) d''attention, {2} erreur(s).' -f $compte['OK'], $compte['ATTENTION'], $compte['ERREUR'])
$niveauFinal = if ($compte['ERREUR'] -gt 0) { 'Red' } elseif ($compte['ATTENTION'] -gt 0) { 'Yellow' } else { 'Green' }
Write-Host $resume -ForegroundColor $niveauFinal
$rapport.Add($resume)
if ($compte['ERREUR'] -gt 0) {
    $conclusion = 'NE PARTEZ PAS : corrigez chaque ligne [ERREUR] (le texte en dessous dit comment), puis relancez ce controle.'
} elseif ($compte['ATTENTION'] -gt 0) {
    $conclusion = 'Lisez chaque ligne [ATTENTION] : certaines sont normales (voir le guide), d''autres sont a corriger avant de partir.'
} else {
    $conclusion = 'Tout est en ordre. Il reste le test du redemarrage (partie 11 du guide) si vous ne l''avez pas encore fait.'
}
Write-Host $conclusion -ForegroundColor $niveauFinal
$rapport.Add($conclusion)

try {
    $dossierJournaux = Join-Path $Dossier 'journaux'
    if (-not (Test-Path -LiteralPath $dossierJournaux)) { New-Item -ItemType Directory -Path $dossierJournaux -Force | Out-Null }
    Set-Content -LiteralPath (Join-Path $dossierJournaux 'verification.txt') -Value $rapport -Encoding ASCII
    Write-Host ('Rapport enregistre : ' + (Join-Path $dossierJournaux 'verification.txt')) -ForegroundColor Gray
} catch {
    Write-Host 'Le rapport n''a pas pu etre enregistre dans le dossier journaux (droits).' -ForegroundColor Gray
}
if ($compte['ERREUR'] -gt 0) { exit 1 } else { exit 0 }
