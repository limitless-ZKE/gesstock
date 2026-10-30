import { pontAndroid } from "./android";

/** Types des fichiers servis par l'API, pour un fichier reçu sans type. */
const TYPES_SELON_EXTENSION: Record<string, string> = {
  pdf: "application/pdf",
  xlsx: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
};

/**
 * Enregistre un fichier reçu de l'API (Excel, PDF) : le navigateur le propose en téléchargement sous ce nom. Dans l'application
 * Android, c'est elle qui l'enregistre dans les téléchargements du téléphone, puis l'ouvre.
 */
export async function telecharger(contenu: Blob, nomDuFichier: string): Promise<void> {
  const pont = pontAndroid();
  if (pont) {
    const extension = nomDuFichier.split(".").pop()?.toLowerCase() ?? "";
    const type = contenu.type || TYPES_SELON_EXTENSION[extension] || "application/octet-stream";
    pont.enregistrerFichier(nomDuFichier, type, await enBase64(contenu));
    return;
  }
  const adresse = URL.createObjectURL(contenu);
  const lien = document.createElement("a");
  lien.href = adresse;
  lien.download = nomDuFichier;
  document.body.appendChild(lien);
  lien.click();
  lien.remove();
  URL.revokeObjectURL(adresse);
}

/** Le pont n'échange que du texte : le fichier lui est confié en base 64, sans l'en-tête « data:…;base64, ». */
function enBase64(contenu: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const lecteur = new FileReader();
    lecteur.onload = () => {
      const adresse = String(lecteur.result);
      resolve(adresse.slice(adresse.indexOf(",") + 1));
    };
    lecteur.onerror = () => reject(lecteur.error ?? new Error("Lecture du fichier impossible"));
    lecteur.readAsDataURL(contenu);
  });
}
