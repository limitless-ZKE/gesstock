import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import HistoriqueReglementsDettes from "../components/HistoriqueReglementsDettes";
import HistoriqueToutesVentes from "../components/HistoriqueToutesVentes";
import HistoriqueVentesSoldees from "../components/HistoriqueVentesSoldees";
import SelecteurPeriode, { type Periode } from "../components/SelecteurPeriode";
import { aujourdhui, libellePeriode } from "../lib/dates";
import type { ProprietesHistorique } from "../lib/historique";
import { normaliserRole } from "../lib/routes";
import { api } from "../services/api";

type Compte = { username: string; actif: boolean };
type Onglet = "ventes" | "soldees" | "reglements";

const ONGLETS: { id: Onglet; libelle: string }[] = [
  { id: "ventes", libelle: "Toutes les ventes" },
  { id: "soldees", libelle: "Ventes soldées" },
  { id: "reglements", libelle: "Règlements de dettes" },
];

/** Ceux qui voient les ventes et les règlements de tous et choisissent un vendeur (le serveur l'impose de toute façon). */
const ROLES_RESPONSABLES = ["ADMIN", "MANAGER", "GESTIONNAIRE"];

/**
 * Trois historiques d'une période : toutes les ventes, les ventes soldées (payées jusqu'au dernier franc) et les règlements de
 * dettes. Un responsable les voit pour tous les vendeurs ou pour celui qu'il choisit ; un caissier ne voit que les siens. La période
 * et le vendeur restent les mêmes d'un onglet à l'autre ; chaque onglet s'imprime (PDF) ou s'exporte (Excel) tel qu'il est affiché.
 */
export default function VentesHistoriquePage() {
  const { user } = useAuth();
  const responsable = (user?.roles ?? []).map(normaliserRole).some((role) => ROLES_RESPONSABLES.includes(role));
  const [periode, setPeriode] = useState<Periode>(() => ({ debut: aujourdhui(), fin: aujourdhui() }));
  const [onglet, setOnglet] = useState<Onglet>("ventes");
  const [vendeur, setVendeur] = useState("");
  const [comptes, setComptes] = useState<Compte[]>([]);
  const [listeIndisponible, setListeIndisponible] = useState(false);
  const [chargement, setChargement] = useState(false);

  useEffect(() => {
    if (!responsable) return;
    let actif = true;
    api.get<Compte[]>("/caisse/journal/caissiers")
      .then(({ data }) => { if (actif) setComptes(Array.isArray(data) ? data : []); })
      .catch(() => { if (actif) setListeIndisponible(true); });
    return () => { actif = false; };
  }, [responsable]);

  const proprietes: ProprietesHistorique = {
    periode,
    vendeur: responsable ? vendeur : "",
    nomVendeur: responsable ? vendeur : user?.username ?? "",
    onChargement: setChargement,
  };
  // Changer de période, de vendeur ou d'onglet repart d'un onglet neuf (page 1, recherche vide) qui recharge ses chiffres.
  const cle = `${onglet}|${periode.debut}|${periode.fin}|${vendeur}`;

  return (
    <div className="space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-slate-900">Historique des ventes</h1>
        <p className="mt-1.5 text-sm text-slate-500 first-letter:uppercase">{libellePeriode(periode.debut, periode.fin)}</p>
        <p className="text-sm font-medium text-slate-700">
          {responsable ? (vendeur ? `Vendeur : ${vendeur}` : "Tous les vendeurs") : "Vos ventes et les règlements que vous avez encaissés"}
        </p>
      </header>

      {responsable && (
        <label className="block max-w-sm">
          <span className="mb-1.5 block text-sm font-semibold text-slate-800">Vendeur</span>
          <select value={vendeur} onChange={(evenement) => setVendeur(evenement.target.value)} className="input-field">
            <option value="">Tous les vendeurs</option>
            {comptes.map((compte) => (
              <option key={compte.username} value={compte.username}>{compte.username}{compte.actif ? "" : " (désactivé)"}</option>
            ))}
          </select>
          {listeIndisponible && <span role="status" className="mt-1.5 block text-xs text-amber-800">La liste des vendeurs n’a pas pu être chargée.</span>}
        </label>
      )}

      <SelecteurPeriode periode={periode} onChange={setPeriode} chargement={chargement} />

      {/* Sur un téléphone, les onglets passent à la ligne plutôt que de défiler : les trois restent visibles. */}
      <div role="tablist" aria-label="Historiques" className="flex flex-wrap gap-1 rounded-xl bg-slate-100 p-1 sm:w-fit">
        {ONGLETS.map(({ id, libelle }) => (
          <button key={id} type="button" role="tab" id={`onglet-${id}`} aria-selected={onglet === id} aria-controls="historique-panneau"
            onClick={() => setOnglet(id)}
            className={`whitespace-nowrap rounded-lg px-3 py-2 text-sm font-semibold transition ${onglet === id ? "bg-white text-emerald-700 shadow-sm" : "text-slate-500 hover:text-slate-800"}`}>
            {libelle}
          </button>
        ))}
      </div>

      <div role="tabpanel" id="historique-panneau" aria-labelledby={`onglet-${onglet}`} className="space-y-5">
        {onglet === "ventes" && <HistoriqueToutesVentes key={cle} {...proprietes} />}
        {onglet === "soldees" && <HistoriqueVentesSoldees key={cle} {...proprietes} />}
        {onglet === "reglements" && <HistoriqueReglementsDettes key={cle} {...proprietes} />}
      </div>
    </div>
  );
}
