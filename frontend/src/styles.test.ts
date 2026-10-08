import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const SRC = dirname(fileURLToPath(import.meta.url));

function sources(dossier: string): string[] {
  return readdirSync(dossier).flatMap((nom) => {
    const chemin = join(dossier, nom);
    if (statSync(chemin).isDirectory()) return sources(chemin);
    return /\.tsx$/.test(nom) && !/\.test\./.test(nom) ? [chemin] : [];
  });
}

/**
 * Les classes « maison » (button-primary, icon-button, input-field…) ne sont pas des utilitaires Tailwind : si elles manquent
 * dans index.css, Tailwind ne dit rien et le bouton s'affiche en texte brut. C'est arrivé pendant des mois (12 écrans) :
 * seul un vrai navigateur l'a révélé. Ce test échoue dès qu'une classe maison est utilisée sans être définie.
 */
describe("classes CSS maison", () => {
  // index.css est lu tel quel : la config de test neutralise le CSS importé, mais pas la lecture du fichier.
  const css = readFileSync(join(SRC, "index.css"), "utf8");
  const definies = new Set(Array.from(css.matchAll(/\.([a-z][a-z0-9-]*)/g), (m) => m[1]));
  const utilisees = new Map<string, string>();
  for (const fichier of sources(SRC)) {
    for (const m of readFileSync(fichier, "utf8").matchAll(/className=(?:"([^"]*)"|\{`([^`]*)`\})/g)) {
      for (const jeton of (m[1] ?? m[2] ?? "").split(/\s+/)) {
        if (/^(button-[a-z-]+|icon-button|input-field)$/.test(jeton)) utilisees.set(jeton, fichier.slice(SRC.length + 1));
      }
    }
  }

  it("repère bien les classes maison utilisées par les écrans", () => {
    expect([...utilisees.keys()]).toEqual(expect.arrayContaining(["button-primary", "button-secondary", "icon-button", "input-field"]));
  });

  it("définit dans index.css chaque classe maison utilisée", () => {
    const manquantes = [...utilisees].filter(([jeton]) => !definies.has(jeton)).map(([jeton, fichier]) => `${jeton} (ex. ${fichier})`);
    expect(manquantes).toEqual([]);
  });

  it("garde ces classes dans la couche « components » : les utilitaires posés à côté (h-9, pl-9…) doivent pouvoir les surcharger", () => {
    const couche = css.slice(css.indexOf("@layer components"));
    for (const jeton of ["button-primary", "button-secondary", "icon-button", "input-field"]) {
      expect(couche, jeton).toContain(`.${jeton}`);
    }
  });
});
