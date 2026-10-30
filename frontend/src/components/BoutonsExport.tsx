import { useState } from "react";
import { FileSpreadsheet, FileText, LoaderCircle } from "lucide-react";
import { telecharger } from "../lib/telechargement";
import { api } from "../services/api";
import { apiErrorMessage, lireErreurDeFichier } from "../services/apiError";
import type { Periode } from "./SelecteurPeriode";

type Format = "excel" | "pdf";

interface BoutonsExportProps {
  /** Chemin de l'API qui sert « /excel » et « /pdf » pour une période (ex. « /ventes/historique »). */
  chemin: string;
  /** Paramètres ajoutés à la période, par exemple le caissier dont on imprime le journal. */
  parametres?: Record<string, string>;
  periode: Periode;
  /** Début du nom du fichier téléchargé : « gesstock-historique-ventes » donne gesstock-historique-ventes-2026-10-01-2026-10-09.pdf. */
  nomDuFichier: string;
  /** Rien à imprimer tant que l'écran n'a pas ses chiffres. */
  desactive?: boolean;
  /** Reçoit le message d'échec (ou null quand un nouvel essai commence) : l'écran choisit où l'afficher. */
  onErreur: (message: string | null) => void;
}

/**
 * Les deux documents à imprimer d'une période, comme sur l'écran des statistiques : Excel pour retravailler les chiffres, PDF
 * pour imprimer. Le serveur les prépare (avec le nom et le logo de l'entreprise) ; le navigateur les enregistre.
 */
export default function BoutonsExport({ chemin, parametres, periode, nomDuFichier, desactive = false, onErreur }: BoutonsExportProps) {
  const [enCours, setEnCours] = useState<Format | null>(null);

  async function exporter(format: Format) {
    setEnCours(format);
    onErreur(null);
    try {
      const { data } = await api.get<Blob>(`${chemin}/${format}`, { params: { debut: periode.debut, fin: periode.fin, ...parametres }, responseType: "blob" });
      await telecharger(data, `${nomDuFichier}-${periode.debut}-${periode.fin}.${format === "excel" ? "xlsx" : "pdf"}`);
    } catch (raison) {
      onErreur(apiErrorMessage(await lireErreurDeFichier(raison), `L’export ${format === "excel" ? "Excel" : "PDF"} a échoué.`));
    } finally {
      setEnCours(null);
    }
  }

  return (
    <div className="flex flex-wrap gap-2">
      <button type="button" disabled={desactive || enCours !== null} onClick={() => void exporter("excel")} className="button-secondary">
        {enCours === "excel" ? <LoaderCircle size={16} className="animate-spin" /> : <FileSpreadsheet size={16} />}
        {enCours === "excel" ? "Préparation…" : "Excel"}
      </button>
      <button type="button" disabled={desactive || enCours !== null} onClick={() => void exporter("pdf")} className="button-secondary">
        {enCours === "pdf" ? <LoaderCircle size={16} className="animate-spin" /> : <FileText size={16} />}
        {enCours === "pdf" ? "Préparation…" : "PDF (imprimer)"}
      </button>
    </div>
  );
}
