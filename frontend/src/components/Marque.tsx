/**
 * Marque GesStock : trois barres de longueurs décroissantes, comme un niveau de stock qui baisse. La couleur vient du texte
 * environnant (currentColor) : posée dans un conteneur « text-emerald-600 », elle prend le vert de la marque.
 */
export default function Marque({ taille = 28, className = "" }: { taille?: number; className?: string }) {
  return (
    <svg width={taille} height={taille} viewBox="0 0 32 32" aria-hidden="true" className={`shrink-0 ${className}`}>
      <rect width="32" height="32" rx="7" fill="currentColor" />
      <path d="M7.5 9.5h17M7.5 16h12M7.5 22.5h7" stroke="#fff" strokeWidth="3" strokeLinecap="round" fill="none" />
    </svg>
  );
}
