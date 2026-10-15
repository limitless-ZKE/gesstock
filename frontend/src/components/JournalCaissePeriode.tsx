import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import { ChevronDown, ChevronRight, CircleAlert, LoaderCircle } from "lucide-react";
import { aujourdhui, jourCourt, libellePeriode } from "../lib/dates";
import { money } from "../lib/format";
import { api } from "../services/api";
import { apiErrorMessage } from "../services/apiError";
import BoutonsExport from "./BoutonsExport";
import Indicateur from "./Indicateur";
import SelecteurPeriode, { type Periode } from "./SelecteurPeriode";

type Ecriture = {
  date: string;
  type: "OUVERTURE" | "VENTE" | "REGLEMENT" | "SORTIE";
  libelle: string;
  reference: string | null;
  utilisateur: string | null;
  entree: number | null;
  sortie: number | null;
  solde: number;
};
type Journee = {
  sessionId: number;
  code: string | null;
  journee: string;
  statut: "OUVERTE" | "FERMEE";
  ouvertePar: string | null;
  fermeePar: string | null;
  ouverteLe: string;
  fermeeLe: string | null;
  fondDeCaisse: number;
  encaissementsEspeces: number;
  reglementsClientsEspeces: number;
  sorties: number;
  soldeTheorique: number;
  montantCloture: number | null;
  ecart: number | null;
  nombreVentes: number;
  nombreVentesAnnulees: number;
  ventesACredit: number;
  autresEncaissements: { mode: string; montant: number }[];
  journeeDepassee: boolean;
  ecritures: Ecriture[];
};
type Journal = {
  debut: string;
  fin: string;
  totaux: {
    nombreSessions: number;
    sessionsNonCloturees: number;
    nombreVentes: number;
    nombreVentesAnnulees: number;
    encaissementsEspeces: number;
    reglementsClientsEspeces: number;
    sorties: number;
    especesComptees: number;
    ecarts: number;
  };
  sessions: Journee[];
};

const modes: Record<string, string> = { ESPECES: "Espèces", MOBILE_MONEY: "Mobile Money", CARTE: "Carte", VIREMENT: "Virement", CHEQUE: "Chèque", AUTRE: "Autre" };
const types: Record<Ecriture["type"], string> = { OUVERTURE: "Ouverture", VENTE: "Vente", REGLEMENT: "Règlement", SORTIE: "Sortie" };
const heure = new Intl.DateTimeFormat("fr-FR", { timeStyle: "short" });
/** Montants des tableaux : sans l'unité, dite une fois sous le tableau (sinon la ligne dépasse l'écran). */
const nombre = new Intl.NumberFormat("fr-FR", { maximumFractionDigits: 2 });

function etat(journee: Journee): string {
  if (journee.statut === "FERMEE") return "Clôturée";
  return journee.journeeDepassee ? "Non clôturée" : "En cours";
}

/**
 * Journal de caisse d'une période : une ligne par journée (espèces entrées, sorties, solde, espèces comptées, écart), dépliable
 * pour lire chaque écriture avec son solde. Le même contenu s'imprime en PDF (à signer) ou s'exporte en Excel.
 */
export default function JournalCaissePeriode({ periode: periodeFournie, onPeriode }: { periode?: Periode; onPeriode?: (periode: Periode) => void } = {}) {
  // La période peut être pilotée par l'écran qui change de caissier sans la perdre ; seul, le journal garde la sienne.
  const [periodeInterne, setPeriodeInterne] = useState<Periode>(() => ({ debut: aujourdhui(), fin: aujourdhui() }));
  const periode = periodeFournie ?? periodeInterne;
  const setPeriode = onPeriode ?? setPeriodeInterne;
  const [journal, setJournal] = useState<Journal | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [exportError, setExportError] = useState<string | null>(null);
  const [ouvertes, setOuvertes] = useState<Set<number>>(new Set());
  // Une réponse tardive d'une ancienne période ne doit pas écraser celle qu'on vient de choisir.
  const derniereDemande = useRef(0);

  const charger = useCallback(async (choisie: Periode) => {
    const demande = ++derniereDemande.current;
    setLoading(true);
    setError("");
    try {
      const { data } = await api.get<Journal>("/caisse/journal", { params: { debut: choisie.debut, fin: choisie.fin } });
      if (demande !== derniereDemande.current) return;
      setJournal(data);
      // Une seule journée : son détail est directement visible.
      setOuvertes(new Set(data.sessions.length === 1 ? [data.sessions[0].sessionId] : []));
    } catch (raison) {
      if (demande !== derniereDemande.current) return;
      setJournal(null);
      setError(apiErrorMessage(raison, "Impossible de charger le journal de caisse."));
    } finally {
      if (demande === derniereDemande.current) setLoading(false);
    }
  }, []);

  useEffect(() => { void charger(periode); }, [periode, charger]);

  function basculer(id: number) {
    setOuvertes((avant) => {
      const apres = new Set(avant);
      if (!apres.delete(id)) apres.add(id);
      return apres;
    });
  }

  const totaux = journal?.totaux;
  const entrees = totaux ? totaux.encaissementsEspeces + totaux.reglementsClientsEspeces : 0;

  return (
    <div className="space-y-5">
      <div className="flex flex-col justify-between gap-3 lg:flex-row lg:items-end">
        <div>
          <h2 className="text-lg font-bold text-slate-900">Journal de caisse</h2>
          <p className="mt-0.5 text-sm text-slate-500 first-letter:uppercase">{libellePeriode(periode.debut, periode.fin)}</p>
        </div>
        <BoutonsExport chemin="/caisse/journal" periode={periode} nomDuFichier="gesstock-journal-caisse" desactive={loading || !journal} onErreur={setExportError} />
      </div>

      <SelecteurPeriode periode={periode} onChange={setPeriode} chargement={loading} />

      {error && <Alerte ton="erreur">{error}</Alerte>}
      {exportError && <Alerte ton="attention" fermer={() => setExportError(null)}>{exportError}</Alerte>}

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Indicateur
          libelle="Journées de caisse"
          valeur={totaux ? String(totaux.nombreSessions) : "—"}
          note={totaux && totaux.sessionsNonCloturees > 0 ? `dont ${totaux.sessionsNonCloturees} non clôturée(s)` : undefined}
        />
        <Indicateur libelle="Espèces entrées" valeur={totaux ? money.format(entrees) : "—"} note="ventes + règlements de dettes" />
        <Indicateur libelle="Sorties de caisse" valeur={totaux ? money.format(totaux.sorties) : "—"} />
        <Indicateur
          libelle="Écart de clôture"
          valeur={totaux ? money.format(totaux.ecarts) : "—"}
          note={totaux && totaux.ecarts !== 0 ? (totaux.ecarts < 0 ? "espèces manquantes" : "excédent d’espèces") : "compté − théorique"}
          alerte={!!totaux && totaux.ecarts !== 0}
        />
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[960px] text-left">
            <thead className="bg-slate-50 text-sm font-semibold text-slate-600">
              <tr>
                <th className="w-10 px-3 py-3"><span className="sr-only">Détail</span></th>
                <th className="px-3 py-3">Journée</th>
                <th className="px-3 py-3">Caissier</th>
                <th className="px-3 py-3">État</th>
                <th className="px-3 py-3 text-right">Fond</th>
                <th className="px-3 py-3 text-right">Ventes espèces</th>
                <th className="px-3 py-3 text-right">Règlements</th>
                <th className="px-3 py-3 text-right">Sorties</th>
                <th className="px-3 py-3 text-right">Solde théorique</th>
                <th className="px-3 py-3 text-right">Compté</th>
                <th className="px-5 py-3 text-right">Écart</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={11} className="h-32 text-center text-sm text-slate-500"><LoaderCircle className="mr-2 inline animate-spin" size={16} />Chargement…</td></tr>
              ) : journal?.sessions.length ? journal.sessions.map((journee) => {
                const ouverte = ouvertes.has(journee.sessionId);
                return (
                  <Fragment key={journee.sessionId}>
                    <tr className="border-t border-slate-100 hover:bg-slate-50">
                      <td className="px-3 py-3">
                        <button className="icon-button" onClick={() => basculer(journee.sessionId)} aria-expanded={ouverte} aria-label={`${ouverte ? "Masquer" : "Voir"} le détail du ${jourCourt(journee.journee)}`}>
                          {ouverte ? <ChevronDown size={17} /> : <ChevronRight size={17} />}
                        </button>
                      </td>
                      <td className="whitespace-nowrap px-3 py-3 text-sm font-semibold text-slate-900">{jourCourt(journee.journee)}</td>
                      <td className="px-3 py-3 text-sm text-slate-700">{journee.ouvertePar ?? "—"}</td>
                      <td className="whitespace-nowrap px-3 py-3 text-sm"><span className={journee.statut === "OUVERTE" && journee.journeeDepassee ? "font-semibold text-amber-800" : "text-slate-700"}>{etat(journee)}</span></td>
                      <td className="whitespace-nowrap px-3 py-3 text-right text-sm">{nombre.format(journee.fondDeCaisse)}</td>
                      <td className="whitespace-nowrap px-3 py-3 text-right text-sm">{nombre.format(journee.encaissementsEspeces)}</td>
                      <td className="whitespace-nowrap px-3 py-3 text-right text-sm">{nombre.format(journee.reglementsClientsEspeces)}</td>
                      <td className={`whitespace-nowrap px-3 py-3 text-right text-sm ${journee.sorties > 0 ? "text-rose-800" : "text-slate-500"}`}>{journee.sorties > 0 ? `−${nombre.format(journee.sorties)}` : "—"}</td>
                      <td className="whitespace-nowrap px-3 py-3 text-right text-sm font-bold text-slate-900">{nombre.format(journee.soldeTheorique)}</td>
                      <td className="whitespace-nowrap px-3 py-3 text-right text-sm">{journee.montantCloture === null ? "—" : nombre.format(journee.montantCloture)}</td>
                      <td className={`px-5 py-3 text-right text-sm font-bold ${journee.ecart === null || journee.ecart === 0 ? "text-slate-700" : journee.ecart < 0 ? "text-rose-800" : "text-amber-800"}`}>
                        {journee.ecart === null ? "—" : journee.ecart === 0 ? "aucun" : nombre.format(journee.ecart)}
                      </td>
                    </tr>
                    {ouverte && (
                      <tr className="bg-slate-50">
                        <td colSpan={11} className="px-5 pb-4 pt-1"><Ecritures journee={journee} /></td>
                      </tr>
                    )}
                  </Fragment>
                );
              }) : (
                <tr><td colSpan={11} className="h-32 text-center text-sm text-slate-500">Aucune session de caisse n’a été ouverte sur cette période.</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <p className="border-t border-slate-100 px-5 py-3 text-sm text-slate-600">
          Montants en francs CFA. Le solde théorique est celui de la clôture : fond de caisse + ventes encaissées en espèces + règlements de dettes en espèces − sorties. Les paiements Mobile Money, carte ou virement passent hors du tiroir-caisse et sont indiqués à part.
        </p>
      </section>
    </div>
  );
}

function Ecritures({ journee }: { journee: Journee }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white">
      <p className="border-b border-slate-100 px-4 py-2.5 text-sm text-slate-600">
        Session {journee.code} · ouverte par {journee.ouvertePar ?? "—"} à {heure.format(new Date(journee.ouverteLe))}
        {journee.fermeeLe ? ` · clôturée par ${journee.fermeePar ?? "—"} à ${heure.format(new Date(journee.fermeeLe))}` : ""}
      </p>
      <table className="w-full text-left">
        <thead className="text-sm font-semibold text-slate-600">
          <tr>
            <th className="px-4 py-2">Heure</th>
            <th className="px-3 py-2">Opération</th>
            <th className="px-3 py-2">Libellé</th>
            <th className="px-3 py-2">Par</th>
            <th className="px-3 py-2 text-right">Entrée</th>
            <th className="px-3 py-2 text-right">Sortie</th>
            <th className="px-4 py-2 text-right">Solde</th>
          </tr>
        </thead>
        <tbody>
          {journee.ecritures.map((ecriture, index) => (
            <tr key={`${ecriture.date}-${index}`} className="border-t border-slate-100">
              <td className="whitespace-nowrap px-4 py-2 text-sm text-slate-600">{heure.format(new Date(ecriture.date))}</td>
              <td className="px-3 py-2 text-sm text-slate-700">{types[ecriture.type]}</td>
              <td className="px-3 py-2 text-sm text-slate-900">
                {ecriture.libelle}
                {ecriture.reference && <span className="ml-2 text-xs text-slate-500">{ecriture.reference}</span>}
              </td>
              <td className="px-3 py-2 text-sm text-slate-600">{ecriture.utilisateur ?? "—"}</td>
              <td className="px-3 py-2 text-right text-sm text-emerald-800">{ecriture.entree === null ? "" : nombre.format(ecriture.entree)}</td>
              <td className="px-3 py-2 text-right text-sm text-rose-800">{ecriture.sortie === null ? "" : `−${nombre.format(ecriture.sortie)}`}</td>
              <td className="px-4 py-2 text-right text-sm font-semibold text-slate-900">{nombre.format(ecriture.solde)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {(journee.autresEncaissements.length > 0 || journee.ventesACredit > 0 || journee.nombreVentesAnnulees > 0) && (
        <ul className="space-y-0.5 border-t border-slate-100 px-4 py-2.5 text-sm text-slate-600">
          {journee.autresEncaissements.length > 0 && (
            <li>Encaissé hors espèces (non compté dans le tiroir) : {journee.autresEncaissements.map((e) => `${modes[e.mode] ?? e.mode} ${money.format(e.montant)}`).join(" ; ")}.</li>
          )}
          {journee.ventesACredit > 0 && <li>Ventes à crédit (rien encaissé) : {money.format(journee.ventesACredit)}.</li>}
          {journee.nombreVentesAnnulees > 0 && <li>{journee.nombreVentesAnnulees} vente(s) annulée(s), non comptée(s).</li>}
        </ul>
      )}
    </div>
  );
}

function Alerte({ ton, fermer, children }: { ton: "erreur" | "attention"; fermer?: () => void; children: string }) {
  const style = ton === "erreur" ? "border-rose-600 bg-rose-50 text-rose-800" : "border-amber-600 bg-amber-50 text-amber-900";
  return (
    <div role="alert" className={`flex items-center gap-2 border-l-4 px-3 py-3 text-sm font-medium ${style}`}>
      <CircleAlert size={16} className="shrink-0" /> {children}
      {fermer && <button className="ml-auto font-semibold underline" onClick={fermer}>Fermer</button>}
    </div>
  );
}
