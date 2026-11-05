import { useState } from "react";
import { FileSpreadsheet, FileText, LoaderCircle } from "lucide-react";
import { telecharger } from "../lib/telechargement";
import { api } from "../services/api";
import { apiErrorMessage, lireErreurDeFichier } from "../services/apiError";

type Format = "excel" | "pdf";

interface BoutonsExportListeProps {
  /** Chemin de l'API qui sert « /excel » et « /pdf » (ex. « /fournisseurs/export »). */
  chemin: string;
  /** Les filtres de l'écran, envoyés tels quels : le document porte exactement ce que l'écran montre. */
  parametres?: Record<string, string | number | undefined>;
  /** Nom du fichier téléchargé, sans extension : « gesstock-fournisseurs-avec-dette » donne gesstock-fournisseurs-avec-dette.pdf. */
  nomDuFichier: string;
  desactive?: boolean;
  /** Reçoit le message d'échec (ou null quand un nouvel essai commence) : l'écran choisit où l'afficher. */
  onErreur: (message: string | null) => void;
}

/** Les deux documents d'une liste (Excel pour retravailler, PDF pour imprimer), selon les filtres affichés à l'écran. */
export default function BoutonsExportListe({ chemin, parametres, nomDuFichier, desactive = false, onErreur }: BoutonsExportListeProps) {
  const [enCours, setEnCours] = useState<Format | null>(null);

  async function exporter(format: Format) {
    setEnCours(format);
    onErreur(null);
    try {
      const params = Object.fromEntries(Object.entries(parametres ?? {}).filter(([, valeur]) => valeur !== undefined && valeur !== ""));
      const { data } = await api.get<Blob>(`${chemin}/${format}`, { params, responseType: "blob" });
      await telecharger(data, `${nomDuFichier}.${format === "excel" ? "xlsx" : "pdf"}`);
    } catch (raison) {
      onErreur(apiErrorMessage(await lireErreurDeFichier(raison), `L’export ${format === "excel" ? "Excel" : "PDF"} a échoué.`));
    } finally {
      setEnCours(null);
    }
  }

  return (
    <div className="flex flex-wrap gap-2">
      <button type="button" disabled={desactive || enCours !== null} onClick={() => void exporter("excel")} className="button-secondary h-9">
        {enCours === "excel" ? <LoaderCircle size={14} className="animate-spin" /> : <FileSpreadsheet size={14} />}
        {enCours === "excel" ? "Préparation…" : "Excel"}
      </button>
      <button type="button" disabled={desactive || enCours !== null} onClick={() => void exporter("pdf")} className="button-secondary h-9">
        {enCours === "pdf" ? <LoaderCircle size={14} className="animate-spin" /> : <FileText size={14} />}
        {enCours === "pdf" ? "Préparation…" : "PDF (imprimer)"}
      </button>
    </div>
  );
}
