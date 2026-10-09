import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { CheckCircle2, CircleAlert, ImagePlus, LoaderCircle, Save, Trash2, X } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import EnTeteDocument from "../components/EnTeteDocument";
import { logoUrlDe, useEntreprise, type Entreprise } from "../entreprise/EntrepriseContext";
import { normaliserRole } from "../lib/routes";
import { api } from "../services/api";
import { apiErrorMessage } from "../services/apiError";

type Form = {
  nom: string;
  adresse: string;
  telephone: string;
  email: string;
  identifiantLegal: string;
  siteWeb: string;
  piedDePage: string;
  logoSurTickets: boolean;
};

const LOGO_OCTETS_MAX = 2 * 1024 * 1024;
const FORMATS_LOGO = ["image/png", "image/jpeg"];

const depuis = (entreprise: Entreprise): Form => ({
  nom: entreprise.nom,
  adresse: entreprise.adresse ?? "",
  telephone: entreprise.telephone ?? "",
  email: entreprise.email ?? "",
  identifiantLegal: entreprise.identifiantLegal ?? "",
  siteWeb: entreprise.siteWeb ?? "",
  piedDePage: entreprise.piedDePage ?? "",
  logoSurTickets: entreprise.logoSurTickets,
});

/** Ce que le ticket et l'en-tête imprimeront, avec les valeurs en cours de saisie (avant enregistrement). */
const apercuDe = (entreprise: Entreprise, form: Form): Entreprise => ({
  ...entreprise,
  nom: form.nom.trim() || entreprise.nom,
  adresse: form.adresse.trim() || null,
  telephone: form.telephone.trim() || null,
  email: form.email.trim() || null,
  identifiantLegal: form.identifiantLegal.trim() || null,
  siteWeb: form.siteWeb.trim() || null,
  piedDePage: form.piedDePage.trim() || null,
  logoSurTickets: form.logoSurTickets,
});

export default function EntreprisePage() {
  const { user } = useAuth();
  const { entreprise, rafraichir } = useEntreprise();
  const peutModifier = (user?.roles ?? []).some((role) => ["ADMIN", "MANAGER"].includes(normaliserRole(role)));
  const [form, setForm] = useState<Form | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ text: string; isError: boolean } | null>(null);
  const champFichier = useRef<HTMLInputElement>(null);

  // Le formulaire reprend les valeurs du serveur UNE FOIS, au premier chargement. Il ne doit plus être réinitialisé ensuite :
  // envoyer ou retirer un logo recharge l'entreprise, ce qui effacerait sinon les textes saisis et pas encore enregistrés.
  useEffect(() => {
    if (entreprise && form === null) setForm(depuis(entreprise));
  }, [entreprise, form]);

  async function enregistrer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form) return;
    setBusy(true);
    try {
      // champs vides : le serveur les enregistre comme absents ; sa réponse (textes nettoyés) remplace la saisie
      const { data } = await api.put<Entreprise>("/entreprise", form);
      setForm(depuis(data));
      await rafraichir();
      setNotice({ text: "Informations enregistrées. Elles apparaissent dès maintenant sur les documents imprimés.", isError: false });
    } catch (reason) {
      setNotice({ text: apiErrorMessage(reason, "L’enregistrement a échoué."), isError: true });
    } finally {
      setBusy(false);
    }
  }

  async function envoyerLogo(event: ChangeEvent<HTMLInputElement>) {
    const fichier = event.target.files?.[0];
    event.target.value = "";   // permet de renvoyer le même fichier après une correction
    if (!fichier) return;
    // Contrôle de confort : le serveur refait toujours ces vérifications (et d'autres) et fait foi.
    if (!FORMATS_LOGO.includes(fichier.type)) {
      setNotice({ text: "Format non pris en charge : choisissez une image PNG ou JPEG.", isError: true });
      return;
    }
    if (fichier.size > LOGO_OCTETS_MAX) {
      setNotice({ text: "Ce fichier dépasse 2 Mo : choisissez un logo plus léger.", isError: true });
      return;
    }
    setBusy(true);
    try {
      const donnees = new FormData();
      donnees.append("fichier", fichier);
      await api.post("/entreprise/logo", donnees);
      await rafraichir();
      setNotice({ text: "Logo enregistré. Il est adapté automatiquement à chaque document (ticket, en-tête A4…).", isError: false });
    } catch (reason) {
      setNotice({ text: apiErrorMessage(reason, "Le logo n’a pas pu être envoyé."), isError: true });
    } finally {
      setBusy(false);
    }
  }

  async function retirerLogo() {
    if (!window.confirm("Retirer le logo ? Les documents s’imprimeront sans logo.")) return;
    setBusy(true);
    try {
      await api.delete("/entreprise/logo");
      await rafraichir();
      setNotice({ text: "Logo retiré.", isError: false });
    } catch (reason) {
      setNotice({ text: apiErrorMessage(reason, "Le logo n’a pas pu être retiré."), isError: true });
    } finally {
      setBusy(false);
    }
  }

  if (!entreprise || !form) {
    return (
      <p className="py-24 text-center text-sm text-slate-400">
        <LoaderCircle className="mr-2 inline animate-spin" size={16} />Chargement…
      </p>
    );
  }

  const apercu = apercuDe(entreprise, form);
  const champ = <K extends keyof Form>(cle: K, valeur: Form[K]) => setForm({ ...form, [cle]: valeur });

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold text-slate-900">Entreprise</h1>
        <p className="mt-1.5 text-sm text-slate-500">
          Ces informations et ce logo s’impriment sur les tickets, reçus, rapports et feuilles d’inventaire. Modifiez-les ici : aucun
          redémarrage n’est nécessaire.
        </p>
      </header>

      {notice && (
        <div role={notice.isError ? "alert" : "status"} className={`flex items-center gap-2 rounded-xl border px-4 py-3 text-xs font-semibold ${notice.isError ? "border-rose-200 bg-rose-50 text-rose-800" : "border-emerald-200 bg-emerald-50 text-emerald-800"}`}>
          {notice.isError ? <CircleAlert size={16} /> : <CheckCircle2 size={16} />} {notice.text}
          <button className="ml-auto opacity-60 hover:opacity-100" onClick={() => setNotice(null)} aria-label="Fermer"><X size={15} /></button>
        </div>
      )}
      {!peutModifier && (
        <p role="status" className="rounded-xl bg-amber-50 px-4 py-3 text-xs font-semibold text-amber-900">
          Seuls un responsable (MANAGER) ou un administrateur peuvent modifier ces informations.
        </p>
      )}

      <div className="grid gap-6 xl:grid-cols-[1.4fr_1fr]">
        <div className="space-y-6">
          <form onSubmit={(event) => void enregistrer(event)} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-base font-bold text-slate-900">Informations</h2>
            <fieldset disabled={!peutModifier || busy} className="space-y-4">
              <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Nom de l’entreprise</span>
                <input required maxLength={150} className="input-field" value={form.nom} onChange={(event) => champ("nom", event.target.value)} />
              </label>
              <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Adresse</span>
                <textarea rows={2} maxLength={300} className="input-field h-auto resize-y py-3" value={form.adresse} onChange={(event) => champ("adresse", event.target.value)} />
              </label>
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Téléphone</span>
                  <input maxLength={40} className="input-field" value={form.telephone} onChange={(event) => champ("telephone", event.target.value)} />
                </label>
                <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">E-mail</span>
                  <input type="email" maxLength={180} className="input-field" value={form.email} onChange={(event) => champ("email", event.target.value)} />
                </label>
                <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">RCCM / NINEA / SIRET</span>
                  <input maxLength={80} className="input-field" value={form.identifiantLegal} onChange={(event) => champ("identifiantLegal", event.target.value)} />
                </label>
                <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Site web</span>
                  <input maxLength={180} className="input-field" value={form.siteWeb} onChange={(event) => champ("siteWeb", event.target.value)} />
                </label>
              </div>
              <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Message en bas des tickets</span>
                <input maxLength={200} className="input-field" value={form.piedDePage} onChange={(event) => champ("piedDePage", event.target.value)} placeholder="Ex. : Merci de votre visite !" />
              </label>
              <label className="flex items-center gap-2 text-xs font-semibold text-slate-700">
                <input type="checkbox" checked={form.logoSurTickets} onChange={(event) => champ("logoSurTickets", event.target.checked)} />
                Imprimer le logo sur les tickets 80 mm
              </label>
            </fieldset>
            {peutModifier && (
              <div className="flex justify-end border-t border-slate-100 pt-4">
                <button disabled={busy || !form.nom.trim()} className="button-primary min-w-44">
                  {busy ? <LoaderCircle size={15} className="animate-spin" /> : <Save size={15} />} Enregistrer
                </button>
              </div>
            )}
          </form>

          <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm" aria-label="Logo">
            <h2 className="text-base font-bold text-slate-900">Logo</h2>
            <div className="flex flex-wrap items-center gap-5">
              <div className="grid h-24 w-44 place-items-center rounded-xl border border-dashed border-slate-300 bg-slate-50">
                {entreprise.logo ? (
                  <img src={logoUrlDe(entreprise, "miniature") ?? undefined} alt="Logo actuel" className="max-h-20 max-w-40 object-contain" />
                ) : (
                  <span className="text-xs text-slate-400">Aucun logo</span>
                )}
              </div>
              {peutModifier && (
                <div className="space-y-2">
                  <input ref={champFichier} type="file" accept="image/png,image/jpeg" aria-label="Choisir un fichier de logo" className="hidden" onChange={(event) => void envoyerLogo(event)} />
                  <div className="flex flex-wrap gap-2">
                    <button type="button" disabled={busy} className="button-secondary" onClick={() => champFichier.current?.click()}>
                      <ImagePlus size={15} /> {entreprise.logo ? "Remplacer le logo" : "Ajouter un logo"}
                    </button>
                    {entreprise.logo && (
                      <button type="button" disabled={busy} className="button-secondary text-rose-700" onClick={() => void retirerLogo()}>
                        <Trash2 size={15} /> Retirer
                      </button>
                    )}
                  </div>
                </div>
              )}
            </div>
            <p className="text-xs leading-relaxed text-slate-500">
              PNG ou JPEG, 2 Mo au plus. Le serveur contrôle l’image, la convertit en PNG et en prépare une version réduite pour chaque
              usage (ticket 80 mm, en-tête A4, miniature) : envoyez simplement votre meilleur fichier.
              {entreprise.logo && ` Logo actuel : ${entreprise.logo.largeur} × ${entreprise.logo.hauteur} px.`}
            </p>
          </section>
        </div>

        <aside className="space-y-6">
          <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm" aria-label="Aperçu du ticket">
            <h2 className="mb-4 text-base font-bold text-slate-900">Aperçu d’un ticket</h2>
            <div className="mx-auto max-w-[72mm] rounded-lg border border-slate-200 p-4 font-mono text-xs text-slate-800">
              <EnTeteDocument variante="ticket" entreprise={apercu} />
              <p className="mt-2 text-center text-xs uppercase tracking-[0.2em]">Ticket de vente</p>
              {apercu.piedDePage && <p className="mt-4 text-center text-xs text-slate-500">{apercu.piedDePage}</p>}
            </div>
          </section>
          <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm" aria-label="Aperçu de l’en-tête A4">
            <h2 className="mb-4 text-base font-bold text-slate-900">Aperçu d’un en-tête de document</h2>
            <EnTeteDocument variante="a4" entreprise={apercu} />
          </section>
        </aside>
      </div>
    </div>
  );
}
