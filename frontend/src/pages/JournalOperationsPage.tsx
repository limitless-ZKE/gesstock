import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { CircleAlert, LoaderCircle, ScrollText, Search } from "lucide-react";
import BoutonsExport from "../components/BoutonsExport";
import Indicateur from "../components/Indicateur";
import Pagination from "../components/Pagination";
import SelecteurPeriode, { type Periode } from "../components/SelecteurPeriode";
import { aujourdhui, libellePeriode } from "../lib/dates";
import { PAR_PAGE, momentDansLaPeriode, nomDeFichier } from "../lib/historique";
import { api } from "../services/api";
import { apiErrorMessage } from "../services/apiError";

type Compte = { username: string; actif: boolean };
type Operation = {
  id: number;
  date: string;
  /** Le compte qui a fait l'opération ; vide pour une connexion refusée d'un identifiant inconnu. */
  utilisateur: string | null;
  famille: string;
  operation: string;
  details: string | null;
  element: string;
  poste: string | null;
};
type NombreParLibelle = { libelle: string; nombre: number };
type Journal = {
  debut: string;
  fin: string;
  nombre: number;
  parUtilisateur: NombreParLibelle[];
  parOperation: NombreParLibelle[];
  operations: Operation[];
};

/** Les familles du journal, comme le serveur les nomme (OperationsConnues.Famille). */
const FAMILLES: { valeur: string; libelle: string }[] = [
  { valeur: "", libelle: "Toutes les opérations" },
  { valeur: "VENTES", libelle: "Ventes" },
  { valeur: "CAISSE", libelle: "Caisse" },
  { valeur: "CLIENTS", libelle: "Clients et dettes" },
  { valeur: "STOCK", libelle: "Stock" },
  { valeur: "FOURNISSEURS", libelle: "Fournisseurs" },
  { valeur: "CATALOGUE", libelle: "Catalogue" },
  { valeur: "COMPTES", libelle: "Connexions et comptes" },
  { valeur: "ENTREPRISE", libelle: "Entreprise" },
  { valeur: "AUTRES", libelle: "Autres" },
];

const nombreDe = (liste: NombreParLibelle[] | undefined, libelle: string) => liste?.find((ligne) => ligne.libelle === libelle)?.nombre ?? 0;

/**
 * Le journal des opérations : qui a fait quoi, et quand, pour tous les utilisateurs ou pour un seul, toutes les opérations ou une
 * famille (ventes, caisse, connexions et comptes…). Il s'imprime (PDF) ou s'exporte (Excel) tel qu'il est affiché. Réservé aux
 * responsables : le serveur refuse un caissier, le menu ne le lui montre pas.
 */
export default function JournalOperationsPage() {
  const [periode, setPeriode] = useState<Periode>(() => ({ debut: aujourdhui(), fin: aujourdhui() }));
  const [utilisateur, setUtilisateur] = useState("");
  const [famille, setFamille] = useState("");
  const [comptes, setComptes] = useState<Compte[]>([]);
  const [listeIndisponible, setListeIndisponible] = useState(false);
  const [journal, setJournal] = useState<Journal | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState("");
  const [erreurExport, setErreurExport] = useState<string | null>(null);
  const [recherche, setRecherche] = useState("");
  const [page, setPage] = useState(0);
  // Une réponse tardive d'une demande précédente ne doit pas remplacer la dernière.
  const derniereDemande = useRef(0);

  useEffect(() => {
    let actif = true;
    api.get<Compte[]>("/caisse/journal/caissiers")
      .then(({ data }) => { if (actif) setComptes(Array.isArray(data) ? data : []); })
      .catch(() => { if (actif) setListeIndisponible(true); });
    return () => { actif = false; };
  }, []);

  const parametres = useMemo(() => ({ ...(utilisateur ? { utilisateur } : {}), ...(famille ? { famille } : {}) }), [utilisateur, famille]);

  const charger = useCallback(async () => {
    const demande = ++derniereDemande.current;
    setChargement(true);
    setErreur("");
    try {
      const { data } = await api.get<Journal>("/journal-activites/operations", { params: { debut: periode.debut, fin: periode.fin, ...parametres } });
      if (demande !== derniereDemande.current) return;
      setJournal(data);
    } catch (raison) {
      if (demande !== derniereDemande.current) return;
      setJournal(null);
      setErreur(apiErrorMessage(raison, "Impossible de charger le journal des opérations."));
    } finally {
      if (demande === derniereDemande.current) setChargement(false);
    }
  }, [periode, parametres]);

  useEffect(() => { void charger(); }, [charger]);

  function changer<T>(maj: (valeur: T) => void) {
    return (valeur: T) => {
      maj(valeur);
      setPage(0);
      setErreurExport(null);
    };
  }

  const operations = useMemo(() => journal?.operations ?? [], [journal]);
  const filtrees = useMemo(() => {
    const texte = recherche.trim().toLocaleLowerCase("fr");
    return texte
      ? operations.filter((operation) => [operation.utilisateur, operation.operation, operation.details, operation.poste]
        .some((valeur) => valeur?.toLocaleLowerCase("fr").includes(texte)))
      : operations;
  }, [operations, recherche]);
  const pages = Math.max(1, Math.ceil(filtrees.length / PAR_PAGE));
  const pageCourante = Math.min(page, pages - 1);
  const affichees = filtrees.slice(pageCourante * PAR_PAGE, (pageCourante + 1) * PAR_PAGE);
  const plusieursJours = periode.debut !== periode.fin;
  const refusees = nombreDe(journal?.parOperation, "Connexion refusée");
  const libelleFamille = FAMILLES.find((option) => option.valeur === famille)?.libelle ?? "";

  return (
    <div className="space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-slate-900">Journal des opérations</h1>
        <p className="mt-1.5 text-sm text-slate-500 first-letter:uppercase">{libellePeriode(periode.debut, periode.fin)}</p>
        <p className="text-sm font-medium text-slate-700">
          {utilisateur ? `Utilisateur : ${utilisateur}` : "Tous les utilisateurs"}{famille ? ` · ${libelleFamille}` : ""}
        </p>
      </header>

      <div className="flex flex-col gap-4 md:flex-row md:items-end">
        <label className="block md:w-64">
          <span className="mb-1.5 block text-sm font-semibold text-slate-800">Utilisateur</span>
          <select value={utilisateur} onChange={(evenement) => changer(setUtilisateur)(evenement.target.value)} className="input-field">
            <option value="">Tous les utilisateurs</option>
            {comptes.map((compte) => (
              <option key={compte.username} value={compte.username}>{compte.username}{compte.actif ? "" : " (désactivé)"}</option>
            ))}
          </select>
          {listeIndisponible && <span role="status" className="mt-1.5 block text-xs text-amber-800">La liste des utilisateurs n’a pas pu être chargée.</span>}
        </label>
        <label className="block md:w-64">
          <span className="mb-1.5 block text-sm font-semibold text-slate-800">Opérations</span>
          <select value={famille} onChange={(evenement) => changer(setFamille)(evenement.target.value)} className="input-field">
            {FAMILLES.map((option) => <option key={option.valeur} value={option.valeur}>{option.libelle}</option>)}
          </select>
        </label>
        <div className="md:ml-auto">
          <BoutonsExport chemin="/journal-activites/operations" parametres={Object.keys(parametres).length ? parametres : undefined} periode={periode}
            nomDuFichier={nomDeFichier("gesstock-journal-operations", utilisateur)} desactive={chargement || !journal} onErreur={setErreurExport} />
        </div>
      </div>

      <SelecteurPeriode periode={periode} onChange={changer(setPeriode)} chargement={chargement} />

      {erreur && (
        <div role="alert" className="flex items-center gap-2 border-l-4 border-rose-600 bg-rose-50 px-3 py-3 text-sm font-medium text-rose-800">
          <CircleAlert size={16} /> {erreur}
        </div>
      )}
      {erreurExport && (
        <div role="alert" className="flex items-center gap-2 border-l-4 border-amber-600 bg-amber-50 px-3 py-3 text-sm font-medium text-amber-900">
          <CircleAlert size={16} /> {erreurExport}
          <button className="ml-auto font-semibold underline" onClick={() => setErreurExport(null)}>Fermer</button>
        </div>
      )}

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Indicateur libelle="Opérations" valeur={journal ? String(journal.nombre) : "—"} accent />
        <Indicateur libelle="Utilisateurs" valeur={journal ? String(journal.parUtilisateur.length) : "—"} note="ayant fait au moins une opération" />
        <Indicateur libelle="Ventes annulées" valeur={journal ? String(nombreDe(journal.parOperation, "Vente annulée")) : "—"} />
        <Indicateur libelle="Connexions refusées" valeur={journal ? String(refusees) : "—"} alerte={refusees > 0} note="mot de passe faux, compte inconnu ou désactivé" />
      </section>
      {journal && journal.parUtilisateur.length > 0 && (
        <div className="space-y-1 text-sm text-slate-700">
          <p><span className="font-semibold">Par utilisateur : </span>{journal.parUtilisateur.map((ligne) => `${ligne.libelle} ${ligne.nombre}`).join(" · ")}</p>
          <p><span className="font-semibold">Les plus fréquentes : </span>{journal.parOperation.slice(0, 6).map((ligne) => `${ligne.libelle} ${ligne.nombre}`).join(" · ")}</p>
        </div>
      )}

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        <div className="border-b border-slate-100 p-4">
          <label className="relative block">
            <Search size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
            <input value={recherche} onChange={(evenement) => { setRecherche(evenement.target.value); setPage(0); }} className="input-field pl-10"
              placeholder="Utilisateur, opération ou détail…" aria-label="Rechercher une opération" />
          </label>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[820px] text-left">
            <thead className="bg-slate-50 text-sm font-semibold text-slate-600">
              <tr>
                <th className="px-5 py-3">{plusieursJours ? "Date" : "Heure"}</th>
                <th className="px-3 py-3">Utilisateur</th>
                <th className="px-3 py-3">Opération</th>
                <th className="px-4 py-3">Détail</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {chargement ? (
                <tr><td colSpan={4} className="h-32 text-center text-sm text-slate-500"><LoaderCircle className="mr-2 inline animate-spin" size={16} />Chargement…</td></tr>
              ) : affichees.length ? affichees.map((operation) => (
                <tr key={operation.id} className="align-top hover:bg-slate-50">
                  <td className="whitespace-nowrap px-5 py-3 text-sm text-slate-600">{momentDansLaPeriode(operation.date, plusieursJours)}</td>
                  <td className="whitespace-nowrap px-3 py-3 text-sm font-semibold text-slate-900">{operation.utilisateur ?? "—"}</td>
                  <td className="px-3 py-3 text-sm">
                    <p className={`font-semibold ${operation.operation === "Connexion refusée" || operation.operation === "Vente annulée" ? "text-amber-800" : "text-slate-900"}`}>
                      {operation.operation}
                    </p>
                    <p className="mt-0.5 text-xs text-slate-500">{operation.famille}</p>
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-700">{operation.details ?? "—"}</td>
                </tr>
              )) : (
                <tr>
                  <td colSpan={4} className="h-36 text-center text-sm text-slate-500">
                    <ScrollText className="mx-auto mb-2" size={22} />
                    {operations.length ? "Aucune opération ne correspond à votre recherche." : "Aucune opération enregistrée sur cette période."}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="flex flex-col gap-2 border-t border-slate-100 px-5 py-3 text-sm text-slate-600 sm:flex-row sm:items-center sm:justify-between">
          <p>{filtrees.length} opération(s). Chaque opération est enregistrée au moment où elle est faite, avec son auteur.</p>
          <Pagination page={pageCourante} pages={pages} onPage={setPage} libelle="Pages du journal" />
        </div>
      </section>
    </div>
  );
}
