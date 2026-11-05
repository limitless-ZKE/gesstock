import { useRef, useState } from "react";
import { AlertTriangle, Check, FileSpreadsheet, LoaderCircle, Upload, X } from "lucide-react";
import { telecharger } from "../lib/telechargement";
import { api } from "../services/api";
import { apiErrorMessage, lireErreurDeFichier } from "../services/apiError";

type Statut = "NOUVEAU" | "MODIFIE" | "IDENTIQUE" | "ERREUR";

type LigneApercu = {
  numero: number;
  reference: string;
  designation: string;
  statut: Statut;
  changements: string[];
  avertissements: string[];
  erreurs: string[];
};

export type ApercuImport = {
  nombreDeLignes: number;
  nouveaux: number;
  modifies: number;
  identiques: number;
  erreurs: number;
  avertissements: number;
  categoriesACreer: string[];
  colonnesIgnorees: string[];
  importable: boolean;
  lignes: LigneApercu[];
};

type ResultatImport = { crees: number; modifies: number; identiques: number; categoriesCreees: number };

const LIBELLES: Record<Statut, { texte: string; classe: string }> = {
  NOUVEAU: { texte: "Nouveau", classe: "bg-emerald-50 text-emerald-800" },
  MODIFIE: { texte: "Modifié", classe: "bg-sky-50 text-sky-800" },
  IDENTIQUE: { texte: "Identique", classe: "bg-slate-100 text-slate-600" },
  ERREUR: { texte: "Erreur", classe: "bg-rose-50 text-rose-700" },
};

type ImportArticlesModalProps = {
  onClose: () => void;
  /** Appelé après un import réussi, avec le message à afficher : l'écran recharge son catalogue. */
  onImported: (message: string) => void;
};

/**
 * Importer des articles depuis Excel ou CSV : on choisit le fichier, on VOIT ce qui serait créé, modifié ou refusé ligne par ligne,
 * puis on confirme. Rien n'est enregistré avant la confirmation, et une seule erreur bloque tout le fichier.
 */
export default function ImportArticlesModal({ onClose, onImported }: ImportArticlesModalProps) {
  const champFichier = useRef<HTMLInputElement>(null);
  const [fichier, setFichier] = useState<File | null>(null);
  const [apercu, setApercu] = useState<ApercuImport | null>(null);
  const [analyse, setAnalyse] = useState(false);
  const [importEnCours, setImportEnCours] = useState(false);
  const [telechargement, setTelechargement] = useState<"vide" | "catalogue" | null>(null);
  const [erreur, setErreur] = useState("");
  const [seulementAVerifier, setSeulementAVerifier] = useState(false);
  const occupe = analyse || importEnCours;

  async function telechargerModele(avecArticles: boolean) {
    setTelechargement(avecArticles ? "catalogue" : "vide");
    setErreur("");
    try {
      const { data } = await api.get<Blob>("/articles/import/modele", { params: { avecArticles }, responseType: "blob" });
      await telecharger(data, avecArticles ? "gesstock-articles-modifiables.xlsx" : "gesstock-modele-import-articles.xlsx");
    } catch (raison) {
      setErreur(apiErrorMessage(await lireErreurDeFichier(raison), "Le modèle n’a pas pu être téléchargé."));
    } finally {
      setTelechargement(null);
    }
  }

  function envoi(choisi: File): FormData {
    const donnees = new FormData();
    donnees.append("fichier", choisi);
    return donnees;
  }

  async function choisir(choisi: File | null) {
    setFichier(choisi);
    setApercu(null);
    setErreur("");
    if (!choisi) return;
    setAnalyse(true);
    try {
      const { data } = await api.post<ApercuImport>("/articles/import/apercu", envoi(choisi));
      setApercu(data);
      setSeulementAVerifier(data.erreurs > 0);
    } catch (raison) {
      setErreur(apiErrorMessage(raison, "Ce fichier n’a pas pu être lu."));
    } finally {
      setAnalyse(false);
    }
  }

  async function importer() {
    if (!fichier || !apercu?.importable) return;
    setImportEnCours(true);
    setErreur("");
    try {
      const { data } = await api.post<ResultatImport>("/articles/import", envoi(fichier));
      const parties = [`${data.crees} article(s) créé(s)`, `${data.modifies} modifié(s)`];
      if (data.categoriesCreees > 0) parties.push(`${data.categoriesCreees} catégorie(s) créée(s)`);
      onImported(`Import terminé : ${parties.join(", ")}.`);
    } catch (raison) {
      setErreur(apiErrorMessage(raison, "L’import a échoué : rien n’a été enregistré."));
    } finally {
      setImportEnCours(false);
    }
  }

  const lignes = apercu?.lignes.filter((ligne) => !seulementAVerifier || ligne.erreurs.length > 0 || ligne.avertissements.length > 0) ?? [];
  const aImporter = (apercu?.nouveaux ?? 0) + (apercu?.modifies ?? 0);

  return (
    <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/50 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !occupe) onClose(); }}>
      <section role="dialog" aria-modal="true" aria-label="Importer des articles" className="my-auto w-full max-w-5xl rounded-2xl bg-white p-5 shadow-2xl">
        <div className="mb-4 flex items-start justify-between gap-4">
          <div>
            <h2 className="text-xl font-bold text-slate-900">Importer des articles</h2>
            <p className="mt-1 text-xs text-slate-500">Depuis un fichier Excel (.xlsx) ou CSV. Vous voyez le résultat avant que rien ne soit enregistré.</p>
          </div>
          <button type="button" disabled={occupe} onClick={onClose} className="icon-button" aria-label="Fermer"><X size={16} /></button>
        </div>

        <ol className="space-y-3 text-xs text-slate-600">
          <li className="rounded-xl border border-slate-200 p-3">
            <p className="font-semibold text-slate-800">1. Préparez votre fichier</p>
            <p className="mt-1">Une ligne par article. Un article dont la référence existe déjà est modifié ; sinon il est créé. Le stock sert seulement pour un nouvel article.</p>
            <div className="mt-2 flex flex-wrap gap-2">
              <button type="button" disabled={telechargement !== null} onClick={() => void telechargerModele(false)} className="button-secondary h-9">
                {telechargement === "vide" ? <LoaderCircle size={14} className="animate-spin" /> : <FileSpreadsheet size={14} />}Télécharger le modèle vide
              </button>
              <button type="button" disabled={telechargement !== null} onClick={() => void telechargerModele(true)} className="button-secondary h-9" title="Votre catalogue actuel dans le même format : modifiez les prix dans Excel, puis importez-le">
                {telechargement === "catalogue" ? <LoaderCircle size={14} className="animate-spin" /> : <FileSpreadsheet size={14} />}Télécharger mon catalogue (à modifier)
              </button>
            </div>
          </li>
          <li className="rounded-xl border border-slate-200 p-3">
            <p className="font-semibold text-slate-800">2. Choisissez le fichier rempli</p>
            <div className="mt-2 flex flex-wrap items-center gap-3">
              <input ref={champFichier} type="file" accept=".xlsx,.xls,.csv,.txt" aria-label="Fichier d’articles" className="sr-only" disabled={occupe}
                onChange={(event) => { void choisir(event.target.files?.[0] ?? null); event.target.value = ""; }} />
              <button type="button" disabled={occupe} onClick={() => champFichier.current?.click()} className="button-primary h-9">
                {analyse ? <LoaderCircle size={14} className="animate-spin" /> : <Upload size={14} />}{analyse ? "Vérification…" : fichier ? "Choisir un autre fichier" : "Choisir un fichier"}
              </button>
              {fichier && <span className="text-xs font-semibold text-slate-700">{fichier.name}</span>}
            </div>
          </li>
        </ol>

        {erreur && <div role="alert" className="mt-3 flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-xs font-semibold text-rose-800"><AlertTriangle size={15} className="mt-0.5 shrink-0" /><span>{erreur}</span></div>}

        {apercu && <div className="mt-4 space-y-3">
          <p className="text-sm font-semibold text-slate-800">3. Vérifiez ce qui sera fait ({apercu.nombreDeLignes} ligne(s) lue(s))</p>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
            <Compte libelle="Nouveaux" valeur={apercu.nouveaux} classe="text-emerald-700" />
            <Compte libelle="Modifiés" valeur={apercu.modifies} classe="text-sky-700" />
            <Compte libelle="Identiques" valeur={apercu.identiques} classe="text-slate-600" />
            <Compte libelle="Erreurs" valeur={apercu.erreurs} classe={apercu.erreurs > 0 ? "text-rose-700" : "text-slate-600"} />
            <Compte libelle="À vérifier" valeur={apercu.avertissements} classe={apercu.avertissements > 0 ? "text-amber-700" : "text-slate-600"} />
          </div>
          {apercu.categoriesACreer.length > 0 && <p className="text-xs text-slate-600">Catégories qui seront créées : <span className="font-semibold text-slate-800">{apercu.categoriesACreer.join(", ")}</span></p>}
          {apercu.colonnesIgnorees.length > 0 && <p className="text-xs text-slate-500">Colonnes non reconnues, ignorées : {apercu.colonnesIgnorees.join(", ")}.</p>}
          {apercu.erreurs > 0 && <p role="alert" className="text-xs font-semibold text-rose-700">Corrigez les lignes en erreur dans votre fichier, puis choisissez-le de nouveau : tant qu’il y a une erreur, rien ne peut être importé.</p>}

          <label className="flex items-center gap-2 text-xs font-semibold text-slate-700">
            <input type="checkbox" checked={seulementAVerifier} onChange={(event) => setSeulementAVerifier(event.target.checked)} />
            Afficher seulement les lignes à vérifier (erreurs et avertissements)
          </label>
          <div className="max-h-80 overflow-auto rounded-xl border border-slate-200">
            <table className="w-full min-w-[720px] text-left">
              <thead className="sticky top-0 bg-slate-50 text-xs font-semibold text-slate-600"><tr><th className="px-3 py-2">Ligne</th><th className="px-3 py-2">Article</th><th className="px-3 py-2">État</th><th className="px-3 py-2">Détail</th></tr></thead>
              <tbody className="divide-y divide-slate-100">
                {lignes.length === 0 ? <tr><td colSpan={4} className="h-16 text-center text-xs text-slate-400">Aucune ligne à vérifier.</td></tr> : lignes.map((ligne) => (
                  <tr key={ligne.numero} className="align-top">
                    <td className="px-3 py-2 text-xs text-slate-500">{ligne.numero}</td>
                    <td className="px-3 py-2"><p className="text-xs font-bold text-slate-800">{ligne.designation || "—"}</p><p className="mt-0.5 text-xs text-slate-400">{ligne.reference || "sans référence"}</p></td>
                    <td className="px-3 py-2"><span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-bold ${LIBELLES[ligne.statut].classe}`}>{LIBELLES[ligne.statut].texte}</span></td>
                    <td className="px-3 py-2 text-xs">
                      {ligne.erreurs.map((message) => <p key={message} className="font-semibold text-rose-700">{message}</p>)}
                      {ligne.avertissements.map((message) => <p key={message} className="text-amber-700">{message}</p>)}
                      {ligne.changements.map((message) => <p key={message} className="text-slate-600">{message}</p>)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>}

        <div className="mt-5 flex justify-end gap-3">
          <button type="button" disabled={occupe} onClick={onClose} className="button-secondary">Annuler</button>
          <button type="button" disabled={occupe || !apercu?.importable} onClick={() => void importer()} className="button-primary">
            {importEnCours ? <LoaderCircle size={14} className="animate-spin" /> : <Check size={14} />}{importEnCours ? "Import en cours…" : aImporter > 0 ? `Importer ${aImporter} article(s)` : "Importer"}
          </button>
        </div>
      </section>
    </div>
  );
}

function Compte({ libelle, valeur, classe }: { libelle: string; valeur: number; classe: string }) {
  return <div className="rounded-xl border border-slate-200 px-3 py-2"><p className={`text-lg font-bold ${classe}`}>{valeur}</p><p className="text-xs text-slate-500">{libelle}</p></div>;
}
