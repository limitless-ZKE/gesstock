# Apres une sauvegarde de la base : garde les N plus recentes, en envoie une copie vers le dossier choisi (cle USB, autre disque)
# et note le resultat dans sauvegardes\journal-sauvegardes.txt. Lance par sauvegarder-la-base.cmd (jamais a la main).
#   -Echec : note seulement l'echec d'une sauvegarde.
# Une erreur ici (cle USB debranchee...) ne rend jamais la sauvegarde invalide : elle est notee, la sauvegarde reste sur ce PC.
# Texte volontairement sans accents : ce fichier est lu par PowerShell 5.1 sans connaitre son encodage.
param(
    [string]$Fichier = '',
    [string]$Prefixe = '',
    [int]$Garder = 30,
    [switch]$Echec
)

$ErrorActionPreference = 'Continue'
$racine = Split-Path -Parent $PSScriptRoot
$dossierSauvegardes = Join-Path $racine 'sauvegardes'
$journal = Join-Path $dossierSauvegardes 'journal-sauvegardes.txt'
$configuration = Join-Path $racine 'copie-des-sauvegardes.txt'

# Une ligne datee dans le journal des sauvegardes, qui reste court (300 lignes).
function Noter([string]$texte) {
    try {
        if (-not (Test-Path -LiteralPath $dossierSauvegardes)) { New-Item -ItemType Directory -Path $dossierSauvegardes | Out-Null }
        Add-Content -LiteralPath $journal -Value ('{0}  {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm'), $texte)
        $lignes = @(Get-Content -LiteralPath $journal -ErrorAction SilentlyContinue)
        if ($lignes.Count -gt 300) { Set-Content -LiteralPath $journal -Value ($lignes | Select-Object -Last 300) }
    } catch { }
}

# Ne garde que les $Garder sauvegardes les plus recentes de ce dossier ; renvoie le nombre de fichiers supprimes.
function Garder-Les-Recentes([string]$dossier) {
    if (-not (Test-Path -LiteralPath $dossier)) { return 0 }
    $anciennes = @(Get-ChildItem -LiteralPath $dossier -Filter ($Prefixe + '*.sql') -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -Skip $Garder)
    foreach ($ancienne in $anciennes) { Remove-Item -LiteralPath $ancienne.FullName -Force -ErrorAction SilentlyContinue }
    return $anciennes.Count
}

if ($Echec) {
    Noter 'ECHEC de la sauvegarde (MySQL ne repond pas, ou la sauvegarde est incomplete)'
    exit 0
}

if (-not $Fichier -or -not (Test-Path -LiteralPath $Fichier)) {
    Noter 'ECHEC : le fichier de sauvegarde est introuvable'
    exit 0
}

$nom = Split-Path -Leaf $Fichier
$taille = [math]::Round((Get-Item -LiteralPath $Fichier).Length / 1MB, 1)
$supprimees = Garder-Les-Recentes $dossierSauvegardes
$resume = "Sauvegarde $nom ($taille Mo)"
if ($supprimees -gt 0) { $resume += ", $supprimees ancienne(s) supprimee(s)" }

# Copie vers la cle USB ou l'autre disque choisi (choisir-la-copie-des-sauvegardes.cmd), s'il y en a un.
$copie = ''
if (Test-Path -LiteralPath $configuration) {
    $cible = @(Get-Content -LiteralPath $configuration -ErrorAction SilentlyContinue | Where-Object { $_.Trim() -ne '' } | Select-Object -First 1)
    if ($cible.Count -gt 0) {
        $cible = $cible[0].Trim().Trim('"')
        try {
            if (-not (Test-Path -LiteralPath $cible)) { New-Item -ItemType Directory -Path $cible -Force -ErrorAction Stop | Out-Null }
            Copy-Item -LiteralPath $Fichier -Destination $cible -Force -ErrorAction Stop
            Garder-Les-Recentes $cible | Out-Null
            $copie = "copie envoyee vers $cible"
        } catch {
            $copie = "ATTENTION : copie vers $cible impossible (cle USB debranchee ?), la sauvegarde reste sur ce PC"
        }
    }
}
if ($copie -ne '') { $resume += ' ; ' + $copie }
Noter $resume

Write-Host ''
Write-Host "  $nom ($taille Mo)" -ForegroundColor Green
if ($supprimees -gt 0) { Write-Host "  $supprimees ancienne(s) sauvegarde(s) supprimee(s) : les $Garder plus recentes sont gardees." }
if ($copie -like 'ATTENTION*') { Write-Host "  $copie" -ForegroundColor Yellow }
elseif ($copie -ne '') { Write-Host "  $copie" -ForegroundColor Green }
exit 0
