' Ouvre GesStock dans le navigateur par defaut des que le serveur repond, sans aucune fenetre pendant l'attente.
' Lance par Windows a l'ouverture de session (dossier "Demarrage"), apres installer-demarrage-automatique.cmd.
' Le port est lu dans demarrer.cmd (ligne "set PORT=..."). Si le serveur ne repond pas au bout de 7 minutes, le navigateur
' s'ouvre quand meme : l'utilisateur voit alors que la page ne charge pas, au lieu de ne rien voir.
' Essai sans navigateur :  wscript //B ouvrir-navigateur.vbs essai [secondes]   (le resultat est dans journaux\navigateur.log)
Option Explicit

Dim fso, shell, dossier, port, adresse, essai, attenteMax, pret, debut, ecoule, journal, texte, motif, http

Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")
dossier = fso.GetParentFolderName(fso.GetParentFolderName(WScript.ScriptFullName))

' --- le port de GesStock ---
port = 8080
If fso.FileExists(dossier & "\demarrer.cmd") Then
    Set motif = New RegExp
    motif.Pattern = "^\s*set\s+PORT=(\d+)"
    motif.IgnoreCase = True
    motif.Multiline = True
    texte = fso.OpenTextFile(dossier & "\demarrer.cmd", 1).ReadAll
    If motif.Test(texte) Then port = CLng(motif.Execute(texte)(0).SubMatches(0))
End If
adresse = "http://localhost:" & port & "/"

essai = False
attenteMax = 420
If WScript.Arguments.Count > 0 Then essai = (LCase(WScript.Arguments(0)) = "essai")
If WScript.Arguments.Count > 1 Then attenteMax = CLng(WScript.Arguments(1))

' --- attendre que le serveur reponde (n'importe quelle reponse HTTP prouve qu'il tourne) ---
pret = False
debut = Timer
Do
    On Error Resume Next
    Set http = CreateObject("MSXML2.ServerXMLHTTP.6.0")
    http.setProxy 1                      ' connexion directe : jamais par un proxy pour une adresse locale
    http.setTimeouts 3000, 3000, 3000, 5000
    http.Open "GET", adresse, False
    http.Send
    If Err.Number = 0 Then
        If http.Status > 0 Then pret = True
    End If
    Err.Clear
    On Error GoTo 0
    If pret Then Exit Do
    WScript.Sleep 3000
    ecoule = Timer - debut
    If ecoule < 0 Then ecoule = ecoule + 86400   ' la minuterie repart de zero a minuit
Loop While ecoule < attenteMax

' --- une trace, pour comprendre ce qui s'est passe ---
On Error Resume Next
If Not fso.FolderExists(dossier & "\journaux") Then fso.CreateFolder dossier & "\journaux"
Set journal = fso.OpenTextFile(dossier & "\journaux\navigateur.log", 8, True)
journal.WriteLine Now & " : " & IIf(pret, "serveur pret", "serveur silencieux apres " & attenteMax & " s") & IIf(essai, " (essai : navigateur non ouvert)", " : ouverture de " & adresse)
journal.Close
On Error GoTo 0

If Not essai Then shell.Run adresse, 1, False

Function IIf(condition, siVrai, siFaux)
    If condition Then IIf = siVrai Else IIf = siFaux
End Function
