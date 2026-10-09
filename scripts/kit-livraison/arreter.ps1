# Arrete GesStock (le serveur ET sa boucle de relance) pour CE dossier uniquement. A lancer via arreter.cmd.
# Texte volontairement sans accents : ce fichier est lu par PowerShell 5.1 sans connaitre son encodage.
$ErrorActionPreference = 'Continue'
$dossier = $PSScriptRoot

function Contient([string]$texte, [string]$morceau) {
    return ($texte -and $texte.IndexOf($morceau, [StringComparison]::OrdinalIgnoreCase) -ge 0)
}

# 1) Fin de la tache planifiee (si l'installation automatique a ete faite).
$tache = Get-ScheduledTask -TaskName 'GesStock - Serveur' -ErrorAction SilentlyContinue
if ($tache) { Stop-ScheduledTask -TaskName 'GesStock - Serveur' -ErrorAction SilentlyContinue }

# 2) La boucle de relance (cmd lancer.cmd) d'abord, sinon elle relancerait GesStock aussitot.
$boucles = Get-CimInstance Win32_Process -Filter "Name='cmd.exe'" |
    Where-Object { (Contient $_.CommandLine $dossier) -and (Contient $_.CommandLine 'lancer.cmd') }
foreach ($p in $boucles) {
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    Wait-Process -Id $p.ProcessId -Timeout 15 -ErrorAction SilentlyContinue
}

# 3) Puis le programme lui-meme.
$serveurs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { (Contient $_.CommandLine $dossier) -and (Contient $_.CommandLine 'gesstock.jar') }
foreach ($p in $serveurs) {
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    # La fin d'un processus n'est pas instantanee : sans cette attente, le controle ci-dessous donnerait une fausse alerte.
    Wait-Process -Id $p.ProcessId -Timeout 30 -ErrorAction SilentlyContinue
}

$reste = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { (Contient $_.CommandLine $dossier) -and (Contient $_.CommandLine 'gesstock.jar') })
if ($reste.Count -gt 0) {
    Write-Host 'ATTENTION : GesStock tourne encore. Relancez arreter.cmd en administrateur (clic droit > Executer en tant qu''administrateur).' -ForegroundColor Red
    exit 1
}
Write-Host 'GesStock est arrete.' -ForegroundColor Green
exit 0
