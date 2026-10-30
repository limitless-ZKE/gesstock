import { pontAndroid } from "./android";

/**
 * Attend que les images de la zone à imprimer (le logo de l'entreprise) soient chargées. Sans cela, imprimer juste après
 * l'ouverture d'un ticket donne un ticket sans logo : la boîte d'impression se fige sur l'état de la page à cet instant.
 * L'attente est bornée : un logo lent ou introuvable ne doit jamais empêcher d'imprimer un ticket de vente.
 */
export async function attendreImages(zone: ParentNode | null, delaiMaxMs = 3000): Promise<void> {
  if (!zone) return;
  const images = Array.from(zone.querySelectorAll("img")).filter((image) => !image.complete);
  if (images.length === 0) return;
  const chargees = Promise.all(
    images.map(
      (image) =>
        new Promise<void>((resolve) => {
          image.addEventListener("load", () => resolve(), { once: true });
          image.addEventListener("error", () => resolve(), { once: true });
          if (image.complete) resolve();   // terminée entre le filtrage et la pose des écouteurs
        }),
    ),
  );
  await Promise.race([chargees, new Promise<void>((resolve) => window.setTimeout(resolve, delaiMaxMs))]);
}

/**
 * Imprime la page une fois les images de {@code zone} chargées. Dans l'application Android, `window.print()` ne fait rien :
 * c'est l'application qui ouvre la fenêtre d'impression du téléphone.
 */
export async function imprimer(zone: ParentNode | null): Promise<void> {
  await attendreImages(zone);
  const pont = pontAndroid();
  if (pont) pont.imprimer(document.title);
  else window.print();
}
