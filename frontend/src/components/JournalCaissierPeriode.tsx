import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import { ChevronDown, ChevronRight, CircleAlert, LoaderCircle } from "lucide-react";
import { jourCourt, libellePeriode } from "../lib/dates";
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
  entree: number | null;
  sortie: number | null;
  cumul: number;
};
type Jour = {
  sessionId: number;
  code: string | null;
  journee: string;
  ventesEspeces: number;
  reglementsEspeces: number;
  sorties: number;
  especesARemettre: number;
  nombreVentes: number;
  nombreVentesAnnulees: number;
  ventesACredit: number;
  autresEncaissements: { mode: string; montant: number }[];
  ecritures: Ecriture[];
};
type Journal = {
  caissier: string;
  debut: string;
  fin: string;
  totaux: {
    nombreJours: number;
    nombreVentes: number;
    nombreVentesAnnulees: number;
    ventesEspeces: number;
    reglementsEspeces: number;
    sorties: number;
    especesARemettre: number;
    autresEncaissements: number;
    ventesACredit: number;
  };
  jours: Jour[];
};

const modes: Record<string, string> = { ESPECES: "Espèces", MOBILE_MONEY: "Mobile Money", CARTE: "Carte", VIREMENT: "Virement", CHEQUE: "Chèque", AUTRE: "Autre" };
const types: Record<Ecriture["type"], string> = { OUVERTURE: "Ouverture", VENTE: "Vente", REGLEMENT: "Règlement", SORTIE: "Sortie" };
const heure = new Intl.DateTimeFormat("fr-FR", { timeStyle: "short" });
/** Montants des tableaux : sans l'unité, dite une fois sous le tableau (sinon la ligne dépasse l'écran). */
const nombre = new Intl.NumberFormat("fr-FR", { maximumFractionDigits: 2 });

interface JournalCaissierPeriodeProps {
  /** Le compte dont un responsable lit le journal ; absent pour un caissier : le serveur lui sert toujours le sien. */
  caissier?: string;
  periode: Periode;
  onPeriode: (periode: Periode) => void;
}

/**
 * Journal de caisse d'UN caissier : seulement ses lignes (ses ventes, les règlements qu'il a encaissés, ses sorties), journée par
 * journée, avec ses espèces à remettre. Il s'imprime en PDF (à signer) ou s'exporte en Excel. Le fond de caisse et le solde du
 * tiroir, partagés par tous les caissiers, n'y figurent pas.
 */
export default function JournalCaissierPeriode({ caissier, periode, onPeriode }: JournalCaissierPeriodeProps) {
  const [journal, setJournal] = useState<Journal | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [exportError, setExportError] = useState<string | null>(null);
  const [ouverts, setOuverts] = useState<Set<number>>(new Set());
  // Une réponse tardive d'une ancienne demande ne doit pas écraser celle qu'on vient de faire.
  const derniereDemande = useRef(0);

  const charger = useCallback(async (choisie: Periode, pour: string | undefined) => {
    const demande = ++derniereDemande.current;
    setLoading(true);
    setError("");
    try {
      const params = pour ? { debut: choisie.debut, fin: choisie.fin, caissier: pour } : { debut: choisie.debut, fin: choisie.fin };
      const { data } = await api.get<Journal>("/caisse/journal/caissier", { params });
      if (demande !== derniereDemande.current) return;
      setJournal(data);
      // Une seule journée : son détail est directement visible.
      setOuverts(new Set(data.jours.length === 1 ? [data.jours[0].sessionId] : []));
    } catch (raison) {
      if (demande !== derniereDemande.current) return;
      setJournal(null);
      setError(apiErrorMessage(raison, "Impossible de charger le journal de caisse."));
    } finally {
      if (demande === derniereDemande.current) setLoading(false);
    }
  }, []);

  useEffect(() => { void charger(periode, caissier); }, [periode, caissier, charger]);

  function basculer(id: number) {
    setOuverts((avant) => {
      const apres = new Set(avant);
      if (!apres.delete(id)) apres.add(id);
      return apres;
    });
  }

  const totaux = journal?.totaux;
  const nom = caissier ?? journal?.caissier;
  const titre = caissier ? `Journal de caisse de ${caissier}` : "Mon journal de caisse";
  const rienFait = caissier ? `${caissier} n’a effectué aucune opération sur cette période.` : "Vous n’avez effectué aucune opération sur cette période.";

  return (
    <div className="space-y-5">
      <div className="flex flex-col justify-between gap-3 lg:flex-row lg:items-end">
        <div>
          <h2 className="text-lg font-bold text-slate-900">{titre}</h2>
          <p className="mt-0.5 text-sm text-slate-500 first-letter:uppercase">{libellePeriode(periode.debut, periode.fin)}</p>
        </div>
        <BoutonsExport
          chemin="/caisse/journal/caissier"
          parametres={caissier ? { caissier } : undefined}
          periode={periode}
          nomDuFichier={nom ? `gesstock-journal-caissier-${nom}` : "gesstock-journal-caissier"}
          desactive={loading || !journal}
          onErreur={setExportError}
        />
      </div>

      <SelecteurPeriode periode={periode} onChange={onPeriode} chargement={loading} />

      {error && <Alerte ton="erreur">{error}</Alerte>}
      {exportError && <Alerte ton="attention" fermer={() => setExportError(null)}>{exportError}</Alerte>}

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Indicateur libelle="Espèces à remettre" valeur={totaux ? money.format(totaux.especesARemettre) : "—"} note="ventes + règlements − sorties" accent />
        <Indicateur libelle="Ventes en espèces" valeur={totaux ? money.format(totaux.ventesEspeces) : "—"} note={totaux ? `${totaux.nombreVentes} vente(s) au total` : undefined} />
        <Indicateur libelle="Règlements de dettes" valeur={totaux ? money.format(totaux.reglementsEspeces) : "—"} note="encaissés en espèces" />
        <Indicateur libelle="Sorties de caisse" valeur={totaux ? money.format(totaux.sorties) : "—"} />
      </section>

      {/* Sur une seule journée, ces mentions figurent déjà sous son détail. */}
      {totaux && totaux.nombreJours > 1 && (totaux.autresEncaissements > 0 || totaux.ventesACredit > 0 || totaux.nombreVentesAnnulees > 0) && (
        <p className="text-sm text-slate-600">
          {totaux.autresEncaissements > 0 && <>Encaissé hors espèces : {money.format(totaux.autresEncaissements)}. </>}
          {totaux.ventesACredit > 0 && <>Ventes à crédit : {money.format(totaux.ventesACredit)}. </>}
          {totaux.nombreVentesAnnulees > 0 && <>{totaux.nombreVentesAnnulees} vente(s) annulée(s).</>}
        </p>
      )}

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] text-left">
            <thead className="bg-slate-50 text-sm font-semibold text-slate-600">
              <tr>
                <th className="w-10 px-3 py-3"><span className="sr-only">Détail</span></th>
                <th className="px-3 py-3">Journée</th>
                <th className="px-3 py-3 text-right">Ventes</th>
                <th className="px-3 py-3 text-right">Ventes espèces</th>
                <th className="px-3 py-3 text-right">Règlements</th>
                <th className="px-3 py-3 text-right">Sorties</th>
                <th className="px-5 py-3 text-right">Espèces à remettre</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={7} className="h-32 text-center text-sm text-slate-500"><LoaderCircle className="mr-2 inline animate-spin" size={16} />Chargement…</td></tr>
              ) : journal?.jours.length ? journal.jours.map((jour) => {
                const ouvert = ouverts.has(jour.sessionId);
                return (
                  <Fragment key={jour.sessionId}>
                    <tr className="border-t border-slate-100 hover:bg-slate-50">
                      <td className="px-3 py-3">
                        <button className="icon-button" onClick={() => basculer(jour.sessionId)} aria-expanded={ouvert} aria-label={`${ouvert ? "Masquer" : "Voir"} le détail du ${jourCourt(jour.journee)}`}>
                          {ouvert ? <ChevronDown size={17} /> : <ChevronRight size={17} />}
                        </button>
                      </td>
                      <td className="whitespace-nowrap px-3 py-3 text-sm font-semibold text-slate-900">{jourCourt(jour.journee)}</td>
                      <td className="whitespace-nowrap px-3 py-3 text-right text-sm">{jour.nombreVentes}</td>
                      <td className="whitespace-nowrap px-3 py-3 text-right text-sm">{nombre.format(jour.ventesEspeces)}</td>
                      <td className="whitespace-nowrap px-3 py-3 text-right text-sm">{nombre.format(jour.reglementsEspeces)}</td>
                      <td className={`whitespace-nowrap px-3 py-3 text-right text-sm ${jour.sorties > 0 ? "text-rose-800" : "text-slate-500"}`}>{jour.sorties > 0 ? `−${nombre.format(jour.sorties)}` : "—"}</td>
                      <td className="whitespace-nowrap px-5 py-3 text-right text-sm font-bold text-slate-900">{nombre.format(jour.especesARemettre)}</td>
                    </tr>
                    {ouvert && (
                      <tr className="bg-slate-50">
                        <td colSpan={7} className="px-5 pb-4 pt-1"><Lignes jour={jour} /></td>
                      </tr>
                    )}
                  </Fragment>
                );
              }) : (
                <tr><td colSpan={7} className="h-32 text-center text-sm text-slate-500">{rienFait}</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <p className="border-t border-slate-100 px-5 py-3 text-sm text-slate-600">
          Montants en francs CFA. Espèces à remettre = vos ventes en espèces + les règlements de dettes que vous avez encaissés en espèces − vos sorties.
          Le fond de caisse et le solde du tiroir sont ceux de la journée, partagés par tous les caissiers : ils n’apparaissent pas ici.
        </p>
      </section>
    </div>
  );
}

function Lignes({ jour }: { jour: Jour }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white">
      {jour.ecritures.length > 0 && (
        <table className="w-full text-left">
          <thead className="text-sm font-semibold text-slate-600">
            <tr>
              <th className="px-4 py-2">Heure</th>
              <th className="px-3 py-2">Opération</th>
              <th className="px-3 py-2">Libellé</th>
              <th className="px-3 py-2 text-right">Entrée</th>
              <th className="px-3 py-2 text-right">Sortie</th>
              <th className="px-4 py-2 text-right">Cumul</th>
            </tr>
          </thead>
          <tbody>
            {jour.ecritures.map((ecriture, index) => (
              <tr key={`${ecriture.date}-${index}`} className="border-t border-slate-100">
                <td className="whitespace-nowrap px-4 py-2 text-sm text-slate-600">{heure.format(new Date(ecriture.date))}</td>
                <td className="px-3 py-2 text-sm text-slate-700">{types[ecriture.type]}</td>
                <td className="px-3 py-2 text-sm text-slate-900">
                  {ecriture.libelle}
                  {ecriture.reference && <span className="ml-2 text-xs text-slate-500">{ecriture.reference}</span>}
                </td>
                <td className="px-3 py-2 text-right text-sm text-emerald-800">{ecriture.entree === null ? "" : nombre.format(ecriture.entree)}</td>
                <td className="px-3 py-2 text-right text-sm text-rose-800">{ecriture.sortie === null ? "" : `−${nombre.format(ecriture.sortie)}`}</td>
                <td className="px-4 py-2 text-right text-sm font-semibold text-slate-900">{nombre.format(ecriture.cumul)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {(jour.autresEncaissements.length > 0 || jour.ventesACredit > 0 || jour.nombreVentesAnnulees > 0) && (
        <ul className={`space-y-0.5 px-4 py-2.5 text-sm text-slate-600 ${jour.ecritures.length > 0 ? "border-t border-slate-100" : ""}`}>
          {jour.autresEncaissements.length > 0 && (
            <li>Encaissé hors espèces (non compté dans les espèces à remettre) : {jour.autresEncaissements.map((e) => `${modes[e.mode] ?? e.mode} ${money.format(e.montant)}`).join(" ; ")}.</li>
          )}
          {jour.ventesACredit > 0 && <li>Ventes à crédit (rien encaissé) : {money.format(jour.ventesACredit)}.</li>}
          {jour.nombreVentesAnnulees > 0 && <li>{jour.nombreVentesAnnulees} vente(s) annulée(s), non comptée(s).</li>}
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
