import { useEffect, useState } from "react";
import { aujourdhui } from "../lib/dates";
import { normaliserRole } from "../lib/routes";
import { useAuth } from "../auth/AuthContext";
import { api } from "../services/api";
import JournalCaissePeriode from "./JournalCaissePeriode";
import JournalCaissierPeriode from "./JournalCaissierPeriode";
import type { Periode } from "./SelecteurPeriode";

type Compte = { username: string; actif: boolean };

/** Ceux qui voient le journal complet de la journée et peuvent demander celui d'un caissier. */
const ROLES_RESPONSABLES = ["ADMIN", "MANAGER", "GESTIONNAIRE"];

/**
 * Journal de caisse selon qui le lit. Un caissier ne voit et n'imprime que le sien (le serveur le lui impose de toute façon). Un
 * responsable voit le journal complet de la journée, ou choisit un caissier pour lire et imprimer le sien ; la période choisie
 * est conservée quand il change de caissier.
 */
export default function JournalCaisse() {
  const { user } = useAuth();
  const responsable = (user?.roles ?? []).map(normaliserRole).some((role) => ROLES_RESPONSABLES.includes(role));
  const [periode, setPeriode] = useState<Periode>(() => ({ debut: aujourdhui(), fin: aujourdhui() }));
  const [caissier, setCaissier] = useState("");
  const [comptes, setComptes] = useState<Compte[]>([]);
  const [listeIndisponible, setListeIndisponible] = useState(false);

  useEffect(() => {
    if (!responsable) return;
    let actif = true;
    api.get<Compte[]>("/caisse/journal/caissiers")
      .then(({ data }) => { if (actif) setComptes(data); })
      .catch(() => { if (actif) setListeIndisponible(true); });
    return () => { actif = false; };
  }, [responsable]);

  if (!responsable) return <JournalCaissierPeriode periode={periode} onPeriode={setPeriode} />;

  return (
    <div className="space-y-5">
      <label className="block max-w-sm">
        <span className="mb-1.5 block text-sm font-semibold text-slate-800">Caissier</span>
        <select value={caissier} onChange={(evenement) => setCaissier(evenement.target.value)} className="input-field">
          <option value="">Tous : journal complet de la journée</option>
          {comptes.map((compte) => (
            <option key={compte.username} value={compte.username}>{compte.username}{compte.actif ? "" : " (désactivé)"}</option>
          ))}
        </select>
        {listeIndisponible && <span role="status" className="mt-1.5 block text-xs text-amber-800">La liste des caissiers n’a pas pu être chargée.</span>}
      </label>
      {caissier
        ? <JournalCaissierPeriode caissier={caissier} periode={periode} onPeriode={setPeriode} />
        : <JournalCaissePeriode periode={periode} onPeriode={setPeriode} />}
    </div>
  );
}
