import { useId, useMemo, useState } from "react";
import { Search } from "lucide-react";
import { formatQuantite } from "../lib/quantite";

export type ArticleChoisissable = {
  id: number;
  reference: string;
  designation: string;
  quantiteStock: number;
  uniteMesure: string | null;
};

interface ChampArticleProps {
  articles: ArticleChoisissable[];
  /** Identifiant de l'article choisi (texte), ou « » s'il n'y en a pas encore. */
  valeur: string;
  onChange: (id: string) => void;
  /** Nom lu par les lecteurs d'écran (« Article de la ligne 2 »). */
  libelle: string;
}

/** Au-delà, la liste ne montre que les meilleurs résultats : on invite à préciser plutôt que de faire défiler tout le catalogue. */
const MAX_AFFICHES = 30;

/** Sans accents ni majuscules : « biere » retrouve « Bière ». */
const normaliser = (texte: string) => texte.normalize("NFD").replace(/[̀-ͯ]/g, "").toLocaleLowerCase("fr").trim();

/**
 * Les articles qui correspondent. Rang : le nom commence par la recherche (0), un de ses mots commence par elle (1), elle est
 * ailleurs dans le nom (2), seule la référence correspond (3). À rang égal, l'ordre alphabétique du nom.
 */
function filtrer(articles: ArticleChoisissable[], requete: string): ArticleChoisissable[] {
  const tries = [...articles].sort((a, b) => a.designation.localeCompare(b.designation, "fr"));
  if (requete === "") return tries;
  const classes: { article: ArticleChoisissable; rang: number }[] = [];
  for (const article of tries) {
    const nom = normaliser(article.designation);
    if (nom.startsWith(requete)) classes.push({ article, rang: 0 });
    else if (nom.split(/[\s'’-]+/).some((mot) => mot.startsWith(requete))) classes.push({ article, rang: 1 });
    else if (nom.includes(requete)) classes.push({ article, rang: 2 });
    else if (normaliser(article.reference).includes(requete)) classes.push({ article, rang: 3 });
  }
  return classes.sort((a, b) => a.rang - b.rang).map(({ article }) => article);
}

/**
 * Choix de l'article d'une ligne de bon : on tape une partie du nom (ou de la référence) et la liste se réduit, au lieu de
 * chercher dans une longue liste déroulante.
 */
export default function ChampArticle({ articles, valeur, onChange, libelle }: ChampArticleProps) {
  const identifiantListe = useId();
  // Texte en cours de frappe ; nul tant qu'on ne tape pas : le champ montre alors le nom de l'article choisi.
  const [saisie, setSaisie] = useState<string | null>(null);
  const [ouvert, setOuvert] = useState(false);
  const [actif, setActif] = useState(0);

  const choisi = articles.find((article) => String(article.id) === valeur) ?? null;
  const requete = normaliser(saisie ?? "");
  const resultats = useMemo(() => filtrer(articles, requete), [articles, requete]);
  const affiches = resultats.slice(0, MAX_AFFICHES);

  function choisir(article: ArticleChoisissable) {
    onChange(String(article.id));
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
      else setActif((courant) => Math.min(courant + 1, affiches.length - 1));
    } else if (evenement.key === "ArrowUp") {
      evenement.preventDefault();
      setActif((courant) => Math.max(courant - 1, 0));
    } else if (evenement.key === "Enter" && ouvert) {
      // Entrée choisit l'article en surbrillance, sans envoyer le formulaire du bon.
      evenement.preventDefault();
      if (affiches[actif]) choisir(affiches[actif]);
    } else if (evenement.key === "Escape" && ouvert) {
      evenement.preventDefault();
      fermer();
    }
  }

  return (
    <div className="relative">
      <Search className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={15} />
      <input
        role="combobox"
        aria-label={libelle}
        aria-expanded={ouvert}
        aria-controls={ouvert ? identifiantListe : undefined}
        aria-autocomplete="list"
        aria-activedescendant={ouvert && affiches[actif] ? `${identifiantListe}-${actif}` : undefined}
        autoComplete="off"
        value={saisie ?? (choisi ? `${choisi.designation} (${choisi.reference})` : "")}
        placeholder="Tapez le nom de l’article…"
        className="input-field h-10 pl-9"
        onChange={(evenement) => { setSaisie(evenement.target.value); setOuvert(true); setActif(0); }}
        onFocus={(evenement) => { evenement.target.select(); setOuvert(true); setActif(0); }}
        onClick={() => setOuvert(true)}
        onBlur={fermer}
        onKeyDown={surTouche}
      />
      {ouvert && (
        <ul id={identifiantListe} role="listbox" aria-label="Articles" className="absolute z-20 mt-1 max-h-64 w-full min-w-64 overflow-y-auto rounded-xl border border-slate-200 bg-white py-1 shadow-lg">
          {affiches.map((article, index) => (
            <li
              key={article.id}
              id={`${identifiantListe}-${index}`}
              role="option"
              aria-selected={index === actif}
              // La souris ne doit pas retirer le focus du champ avant le clic (sinon la liste se fermerait avant d'être cliquée).
              onMouseDown={(evenement) => evenement.preventDefault()}
              onClick={() => choisir(article)}
              onMouseEnter={() => setActif(index)}
              className={`cursor-pointer px-3 py-2 text-sm ${index === actif ? "bg-emerald-50 text-emerald-900" : "text-slate-800"}`}
            >
              <span className="font-semibold">{article.designation}</span>
              <span className="ml-2 text-xs text-slate-500">
                {article.reference} · stock {formatQuantite(article.quantiteStock)} {article.uniteMesure ?? ""}
              </span>
            </li>
          ))}
          {requete !== "" && resultats.length === 0 && (
            <li role="presentation" className="px-3 py-2 text-sm text-slate-500">Aucun article ne correspond à « {saisie?.trim()} ».</li>
          )}
          {resultats.length > MAX_AFFICHES && (
            <li role="presentation" className="border-t border-slate-100 px-3 py-2 text-xs text-slate-500">
              Précisez la recherche : {resultats.length - MAX_AFFICHES} autre(s) article(s) non affiché(s).
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
