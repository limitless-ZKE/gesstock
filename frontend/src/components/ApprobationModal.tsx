import { useState, type FormEvent } from "react";
import { LoaderCircle, ShieldCheck } from "lucide-react";

export interface Approbation {
  username: string;
  motDePasse: string;
}

interface ApprobationModalProps {
  /** Pourquoi l'accord est demandé (texte du serveur : remise au-delà du plafond, prix sous le coût…). */
  message: string;
  /** Erreur de la tentative précédente (identifiants refusés, trop d'essais…), vide la première fois. */
  erreur: string;
  occupe: boolean;
  /** Texte du bouton de validation (« Valider la vente », « Enregistrer le proforma »…). */
  libelleAction?: string;
  onSoumettre: (approbation: Approbation) => void;
  onAnnuler: () => void;
}

/**
 * Fenêtre d'accord d'un responsable : une remise au-delà du plafond du caissier, ou un prix sous le coût, exige qu'un
 * MANAGER ou un ADMIN saisisse son identifiant et son mot de passe au comptoir. Le mot de passe n'est ni conservé ni
 * mémorisé : il part avec la requête de vente et le serveur le vérifie sans ouvrir de session.
 */
export default function ApprobationModal({ message, erreur, occupe, libelleAction = "Valider", onSoumettre, onAnnuler }: ApprobationModalProps) {
  const [username, setUsername] = useState("");
  const [motDePasse, setMotDePasse] = useState("");

  function soumettre(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSoumettre({ username: username.trim(), motDePasse });
  }

  return (
    <div className="fixed inset-0 z-[60] grid place-items-center overflow-y-auto bg-slate-950/50 p-4 print:hidden">
      <section role="dialog" aria-modal="true" aria-label="Accord d'un responsable" className="my-auto w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
        <div className="mb-4 flex items-center gap-3">
          
          <div>
            <h2 className="text-base font-bold text-slate-900">Accord d’un responsable requis</h2>
            <p className="text-xs text-slate-500">Un MANAGER ou un ADMIN doit donner son accord.</p>
          </div>
        </div>
        <p className="mb-4 rounded-xl bg-amber-50 px-4 py-3 text-xs font-semibold text-amber-900">{message}</p>
        <form onSubmit={soumettre} className="space-y-3">
          <label className="block">
            <span className="mb-1.5 block text-xs font-bold text-slate-600">Identifiant du responsable</span>
            <input required autoFocus autoComplete="off" className="input-field" value={username} onChange={(event) => setUsername(event.target.value)} />
          </label>
          <label className="block">
            <span className="mb-1.5 block text-xs font-bold text-slate-600">Mot de passe</span>
            <input required type="password" autoComplete="new-password" className="input-field" value={motDePasse} onChange={(event) => setMotDePasse(event.target.value)} />
          </label>
          {erreur && <p role="alert" className="rounded-xl bg-rose-50 px-4 py-3 text-xs font-semibold text-rose-700">{erreur}</p>}
          <div className="flex justify-end gap-2 border-t border-slate-100 pt-4">
            <button type="button" disabled={occupe} onClick={onAnnuler} className="button-secondary">Annuler</button>
            <button disabled={occupe || !username.trim() || !motDePasse} className="button-primary min-w-40">
              {occupe ? <LoaderCircle size={15} className="animate-spin" /> : <ShieldCheck size={15} />}
              {occupe ? "Vérification…" : libelleAction}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
