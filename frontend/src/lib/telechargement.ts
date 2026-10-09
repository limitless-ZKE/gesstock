/** Enregistre un fichier reçu de l'API (Excel, PDF) : le navigateur le propose en téléchargement sous ce nom. */
export function telecharger(contenu: Blob, nomDuFichier: string): void {
  const adresse = URL.createObjectURL(contenu);
  const lien = document.createElement("a");
  lien.href = adresse;
  lien.download = nomDuFichier;
  document.body.appendChild(lien);
  lien.click();
  lien.remove();
  URL.revokeObjectURL(adresse);
}
