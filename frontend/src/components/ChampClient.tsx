import { useId, useMemo, useState } from "react";
import { Search, UserRound, X } from "lucide-react";

type Client = { id: number; nom: string; telephone: string | null };

interface ChampClientProps {
  clients: Client[];
  /** Identifiant du client choisi (texte), ou « » pour un client de passage. */
  valeur: string;
  onChange: (id: string) => void;
}

/** Au-delà, la liste ne montre que les meilleurs résultats : on invite à préciser plutôt que de faire défiler des centaines de noms. */
const MAX_AFFICHES = 30;

/** Sans accents ni majuscules : « ELISE » retrouve « Élise ». */
const normaliser = (texte: string) => texte.normalize("NFD").replace(/[̀-ͯ]/g, "").toLocaleLowerCase("fr").trim();
const chiffres = (texte: string) => texte.replace(/\D/g, "");

/** Les clients qui correspondent, les noms qui commencent par la recherche (ou un de leurs mots) d'abord, puis les autres. */
function filtrer(clients: Client[], requete: string): Client[] {
  if (requete === "") return clients;
  const chiffresRecherches = chiffres(requete);
  const classes: { client: Client; rang: number }[] = [];
  for (const client of clients) {
    const nom = normaliser(client.nom);
    if (nom.startsWith(requete) || nom.split(/\s+/).some((mot) => mot.startsWith(requete))) classes.push({ client, rang: 0 });
    else if (nom.includes(requete)) classes.push({ client, rang: 1 });
    else if (client.telephone && (normaliser(client.telephone).includes(requete)
      || (chiffresRecherches.length >= 3 && chiffres(client.telephone).includes(chiffresRecherches)))) classes.push({ client, rang: 2 });
  }
  return classes.sort((a, b) => a.rang - b.rang).map(({ client }) => client);
}

/**
 * Choix du client d'une vente : on tape une partie du nom (ou du téléphone) et la liste se réduit, au lieu de lire une longue liste
 * déroulante. « Client de passage » (aucun client) reste le choix par défaut ; la croix y revient.
 */
export default function ChampClient({ clients, valeur, onChange }: ChampClientProps) {
  const identifiantChamp = useId();
  const identifiantListe = useId();
  // Texte en cours de frappe ; nul tant qu'on ne tape pas : le champ montre alors le nom du client choisi.
  const [saisie, setSaisie] = useState<string | null>(null);
  const [ouvert, setOuvert] = useState(false);
  const [actif, setActif] = useState(0);

  const choisi = clients.find((client) => String(client.id) === valeur) ?? null;
  const requete = normaliser(saisie ?? "");
  const resultats = useMemo(() => filtrer(clients, requete), [clients, requete]);
  const affiches = resultats.slice(0, MAX_AFFICHES);
  // L'entrée « Client de passage » n'a de sens que sans recherche en cours.
  const entrees: (Client | null)[] = requete === "" ? [null, ...affiches] : affiches;

  function choisir(client: Client | null) {
    onChange(client ? String(client.id) : "");
    setSaisie(null);
    setOuvert(false);
  }

  function fermer() {
    setOuvert(false);
    setSaisie(null);
  }

  function surTouche(evenement: React.KeyboardEvent<HTMLInputElement>) {
    if (evenement.key === "ArrowDown") {
      evenement.preventDefault();
      if (!ouvert) setOuvert(true);
      else setActif((courant) => Math.min(courant + 1, entrees.length - 1));
    } else if (evenement.key === "ArrowUp") {
      evenement.preventDefault();
      setActif((courant) => Math.max(courant - 1, 0));
    } else if (evenement.key === "Enter" && ouvert && entrees[actif] !== undefined) {
      evenement.preventDefault();
      choisir(entrees[actif]);
    } else if (evenement.key === "Escape" && ouvert) {
      evenement.preventDefault();
      fermer();
    }
  }

  return (
    <div>
      <label htmlFor={identifiantChamp} className="mb-2 flex items-center gap-2 text-xs font-bold text-slate-600">
        <UserRound size={15} /> Client (facultatif)
      </label>
      <div className="relative">
        <Search className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
        <input
          id={identifiantChamp}
          role="combobox"
          aria-expanded={ouvert}
          aria-controls={ouvert ? identifiantListe : undefined}
          aria-autocomplete="list"
          aria-activedescendant={ouvert && entrees[actif] !== undefined ? `${identifiantListe}-${actif}` : undefined}
          autoComplete="off"
          value={saisie ?? choisi?.nom ?? ""}
          placeholder="Client de passage — tapez un nom ou un téléphone"
          className="input-field pl-10 pr-10"
          onChange={(evenement) => { setSaisie(evenement.target.value); setOuvert(true); setActif(0); }}
          onFocus={(evenement) => { evenement.target.select(); setOuvert(true); setActif(0); }}
          onClick={() => setOuvert(true)}
          onBlur={fermer}
          onKeyDown={surTouche}
        />
        {choisi && (
          <button
            type="button"
            aria-label="Retirer le client"
            onClick={() => choisir(null)}
            className="absolute right-2 top-1/2 grid size-7 -translate-y-1/2 place-items-center rounded-md text-slate-500 hover:bg-slate-100 hover:text-slate-900"
          >
            <X size={15} />
          </button>
        )}
        {ouvert && (
          <ul id={identifiantListe} role="listbox" aria-label="Clients" className="absolute z-20 mt-1 max-h-64 w-full overflow-y-auto rounded-xl border border-slate-200 bg-white py-1 shadow-lg">
            {entrees.map((client, index) => (
              <li
                key={client?.id ?? "passage"}
                id={`${identifiantListe}-${index}`}
                role="option"
                aria-selected={index === actif}
                // La souris ne doit pas retirer le focus du champ avant le clic (sinon la liste se fermerait avant d'être cliquée).
                onMouseDown={(evenement) => evenement.preventDefault()}
                onClick={() => choisir(client)}
                onMouseEnter={() => setActif(index)}
                className={`cursor-pointer px-3 py-2 text-sm ${index === actif ? "bg-emerald-50 text-emerald-900" : "text-slate-800"}`}
              >
                {client === null ? (
                  <span className="font-semibold">Client de passage</span>
                ) : (
                  <>
                    <span className="font-semibold">{client.nom}</span>
                    {client.telephone && <span className="ml-2 text-xs text-slate-500">{client.telephone}</span>}
                  </>
                )}
              </li>
            ))}
            {requete !== "" && resultats.length === 0 && (
              <li role="presentation" className="px-3 py-2 text-sm text-slate-500">Aucun client ne correspond à « {saisie?.trim()} ».</li>
            )}
            {resultats.length > MAX_AFFICHES && (
              <li role="presentation" className="border-t border-slate-100 px-3 py-2 text-xs text-slate-500">
                Précisez la recherche : {resultats.length - MAX_AFFICHES} autre(s) client(s) non affiché(s).
              </li>
            )}
          </ul>
        )}
      </div>
    </div>
  );
}
