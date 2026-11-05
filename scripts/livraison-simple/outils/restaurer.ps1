# Remet la base de GesStock dans l'etat d'une sauvegarde. A lancer via restaurer-une-sauvegarde.cmd (administrateur).
# Etapes : choix de la sauvegarde -> confirmation ecrite -> arret de GesStock -> copie de securite de l'etat ACTUEL -> restauration
# -> verification. Si la copie de securite echoue, RIEN n'est modifie.
#   -Fichier   la sauvegarde a restaurer (sinon : la liste est proposee)
#   -Oui       ne demande pas la confirmation ecrite (essais automatiques)
#   -SansArret n'arrete pas GesStock (essais sur une base jetable)
# Texte volontairement sans accents : ce fichier est lu par PowerShell 5.1 sans connaitre son encodage.
param(
    [Parameter(Mandatory = $true)][string]$Base,
    [string]$Fichier = '',
    [switch]$Oui,
    [switch]$SansArret
)

$ErrorActionPreference = 'Stop'
$outils = $PSScriptRoot
$racine = Split-Path -Parent $outils
$dossierSauvegardes = Join-Path $racine 'sauvegardes'

function Ecrire([string]$texte, [string]$couleur = 'White') { Write-Host $texte -ForegroundColor $couleur }

function Trouver-Outil([string]$exe) {
    $commande = Get-Command $exe -ErrorAction SilentlyContinue
    if ($commande) { return $commande.Source }
    $candidats = New-Object System.Collections.Generic.List[string]
    foreach ($lettre in 'C', 'D', 'E', 'F') {
        foreach ($modele in "${lettre}:\wamp64\bin\mysql\mysql*\bin\$exe", "${lettre}:\wamp\bin\mysql\mysql*\bin\$exe") {
            foreach ($f in Get-ChildItem -Path $modele -ErrorAction SilentlyContinue) { $candidats.Add($f.FullName) }
        }
    }
    foreach ($f in Get-ChildItem -Path "$env:ProgramFiles\MySQL\MySQL Server *\bin\$exe" -ErrorAction SilentlyContinue) { $candidats.Add($f.FullName) }
    if (Test-Path "C:\xampp\mysql\bin\$exe") { $candidats.Add("C:\xampp\mysql\bin\$exe") }
    if ($candidats.Count -gt 0) { return $candidats[$candidats.Count - 1] }
    return $null
}

# Execute un programme et renvoie son code de sortie (sans que PowerShell traite le texte d'erreur comme une exception).
function Lancer([string]$programme, [string[]]$arguments, [string]$entree = '', [string]$sortie = '') {
    # Start-Process colle les arguments sans les guillemeter : un argument avec des espaces (une requete SQL, un chemin) est protege ici.
    $ligne = ($arguments | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }) -join ' '
    $parametres = @{ FilePath = $programme; ArgumentList = $ligne; Wait = $true; PassThru = $true; NoNewWindow = $true }
    if ($entree -ne '') { $parametres['RedirectStandardInput'] = $entree }
    # Sans fichier de sortie demande, ce que le programme affiche (le resultat d'un "SELECT 1"...) est jete, pas montre a l'ecran.
    $sortieJetee = ''
    if ($sortie -eq '') { $sortieJetee = [IO.Path]::GetTempFileName(); $sortie = $sortieJetee }
    $parametres['RedirectStandardOutput'] = $sortie
    $erreurs = [IO.Path]::GetTempFileName()
    $parametres['RedirectStandardError'] = $erreurs
    $processus = Start-Process @parametres
    $texteErreur = (Get-Content -LiteralPath $erreurs -ErrorAction SilentlyContinue) -join ' '
    Remove-Item -LiteralPath $erreurs -Force -ErrorAction SilentlyContinue
    if ($sortieJetee -ne '') { Remove-Item -LiteralPath $sortieJetee -Force -ErrorAction SilentlyContinue }
    $script:derniereErreur = $texteErreur
    return $processus.ExitCode
}

# Une sauvegarde complete se termine par "Dump completed" (mysqldump l'ecrit en dernier).
function Est-Complete([string]$chemin) {
    $fin = @(Get-Content -LiteralPath $chemin -Tail 5 -ErrorAction SilentlyContinue)
    return (($fin -join "`n") -match 'Dump completed')
}

Ecrire ''
Ecrire "=== Restaurer la base $Base ===" 'Cyan'

$mysql = Trouver-Outil 'mysql.exe'
$mysqldump = Trouver-Outil 'mysqldump.exe'
if (-not $mysql -or -not $mysqldump) { Ecrire 'ERREUR : mysql.exe ou mysqldump.exe est introuvable. WampServer est-il installe ?' 'Red'; exit 1 }

if ((Lancer $mysql @('-u', 'root', '-e', 'SELECT 1')) -ne 0) {
    Ecrire 'MySQL ne repond pas avec le compte root sans mot de passe.' 'Yellow'
    Ecrire 'Si WampServer vient de demarrer, attendez que son icone soit verte. Si root a un mot de passe, saisissez-le ici (sinon fermez cette fenetre).'
    $env:MYSQL_PWD = Read-Host 'Mot de passe root de MySQL'
    if ((Lancer $mysql @('-u', 'root', '-e', 'SELECT 1')) -ne 0) { Ecrire 'MySQL refuse la connexion : rien n''a ete modifie.' 'Red'; exit 1 }
}

# ---------------------------------------------------------------- 1. quelle sauvegarde ?
if ($Fichier -eq '') {
    $liste = @(Get-ChildItem -LiteralPath $dossierSauvegardes -Filter "$Base-*.sql" -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)
    if ($liste.Count -eq 0) { Ecrire "Aucune sauvegarde dans $dossierSauvegardes : rien a restaurer." 'Red'; exit 1 }
    Ecrire ''
    Ecrire 'Sauvegardes disponibles (la plus recente en premier) :'
    for ($i = 0; $i -lt $liste.Count; $i++) {
        Ecrire ('  {0,2})  {1:yyyy-MM-dd HH:mm}   {2,6:N1} Mo   {3}' -f ($i + 1), $liste[$i].LastWriteTime, ($liste[$i].Length / 1MB), $liste[$i].Name)
    }
    Ecrire ''
    $choix = Read-Host 'Numero de la sauvegarde a restaurer (Entree pour annuler)'
    if ($choix -notmatch '^\d+$' -or [int]$choix -lt 1 -or [int]$choix -gt $liste.Count) { Ecrire 'Annule : rien n''a ete modifie.' 'Yellow'; exit 0 }
    $Fichier = $liste[[int]$choix - 1].FullName
}
if (-not (Test-Path -LiteralPath $Fichier)) { Ecrire "ERREUR : $Fichier est introuvable." 'Red'; exit 1 }
if (-not (Est-Complete $Fichier)) { Ecrire 'ERREUR : cette sauvegarde est incomplete (elle ne se termine pas par "Dump completed") : elle ne peut pas etre restauree.' 'Red'; exit 1 }

# ---------------------------------------------------------------- 2. confirmation
if (-not $Oui) {
    Ecrire ''
    Ecrire "Vous allez remettre la base dans l'etat de :  $(Split-Path -Leaf $Fichier)" 'Yellow'
    Ecrire 'Tout ce qui a ete enregistre APRES cette sauvegarde (ventes, articles, caisse...) sera retire de GesStock.' 'Yellow'
    Ecrire 'Une copie de l''etat actuel est faite d''abord, pour ne rien perdre definitivement.' 'Yellow'
    $reponse = Read-Host 'Pour continuer, tapez RESTAURER puis Entree'
    if ($reponse -ne 'RESTAURER') { Ecrire 'Annule : rien n''a ete modifie.' 'Yellow'; exit 0 }
}

# ---------------------------------------------------------------- 3. arret de GesStock
if (-not $SansArret) {
    Ecrire ''
    Ecrire 'Arret de GesStock...'
    & (Join-Path $outils 'arreter.ps1')
    if ($LASTEXITCODE -ne 0) { Ecrire 'GesStock n''a pas pu etre arrete : rien n''a ete modifie.' 'Red'; exit 1 }
}

# ---------------------------------------------------------------- 4. copie de securite de l'etat actuel
$securite = Join-Path $dossierSauvegardes ("avant-restauration-{0}.sql" -f (Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'))
Ecrire ''
Ecrire 'Copie de securite de l''etat actuel...'
$code = Lancer $mysqldump @('-u', 'root', '--single-transaction', '--routines', '--default-character-set=utf8mb4', '--databases', $Base, "--result-file=$securite")
if ($code -ne 0 -or -not (Est-Complete $securite)) {
    Remove-Item -LiteralPath $securite -Force -ErrorAction SilentlyContinue
    Ecrire "ERREUR : la copie de securite a echoue ($script:derniereErreur). Rien n'a ete modifie." 'Red'
    exit 1
}
Ecrire "  Copie de securite : $securite" 'Green'

# ---------------------------------------------------------------- 5. restauration
Ecrire ''
Ecrire 'Restauration en cours (cela peut prendre quelques minutes)...'
$code = Lancer $mysql @('-u', 'root', '--default-character-set=utf8mb4') $Fichier
if ($code -ne 0) {
    Ecrire "ERREUR pendant la restauration ($script:derniereErreur)." 'Red'
    Ecrire "L'etat d'avant est conserve dans : $securite" 'Yellow'
    Ecrire 'Ne relancez pas GesStock : envoyez une photo de cet ecran a votre prestataire.' 'Yellow'
    exit 1
}

# ---------------------------------------------------------------- 6. verification
$sortieVerification = [IO.Path]::GetTempFileName()
$code = Lancer $mysql @('-u', 'root', '-N', '-B', '-e', "SELECT (SELECT COUNT(*) FROM $Base.article), (SELECT COUNT(*) FROM $Base.vente), (SELECT COUNT(*) FROM $Base.utilisateurs)") '' $sortieVerification
$comptes = (Get-Content -LiteralPath $sortieVerification -ErrorAction SilentlyContinue) -join ''
Remove-Item -LiteralPath $sortieVerification -Force -ErrorAction SilentlyContinue
if ($code -ne 0 -or $comptes -eq '') {
    Ecrire "ERREUR : la base restauree ne repond pas comme attendu ($script:derniereErreur). Etat d'avant : $securite" 'Red'
    exit 1
}
$valeurs = $comptes -split '\s+'
Ecrire ''
Ecrire "Base restauree : $($valeurs[0]) article(s), $($valeurs[1]) vente(s), $($valeurs[2]) compte(s) utilisateur." 'Green'

if (-not $SansArret) {
    $tache = Get-ScheduledTask -TaskName 'GesStock - Serveur' -ErrorAction SilentlyContinue
    if ($tache) {
        Start-ScheduledTask -TaskName 'GesStock - Serveur'
        Ecrire 'GesStock redemarre : attendez une a deux minutes (davantage sur un vieux PC), puis rouvrez-le dans le navigateur.' 'Green'
    } else {
        Ecrire 'Relancez maintenant GesStock : double-cliquez sur demarrer.cmd.' 'Green'
    }
}
exit 0
