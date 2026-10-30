import { ChevronLeft, ChevronRight } from "lucide-react";

/** Les pages d'une longue liste : « Page 2 sur 3 », avec la précédente et la suivante. Rien quand tout tient sur une page. */
export default function Pagination({ page, pages, onPage, libelle }: { page: number; pages: number; onPage: (page: number) => void; libelle: string }) {
  if (pages <= 1) return null;
  return (
    <div className="flex items-center gap-2" role="navigation" aria-label={libelle}>
      <button className="icon-button disabled:opacity-40" disabled={page === 0} onClick={() => onPage(page - 1)} aria-label="Page précédente"><ChevronLeft size={17} /></button>
      <span>Page {page + 1} sur {pages}</span>
      <button className="icon-button disabled:opacity-40" disabled={page >= pages - 1} onClick={() => onPage(page + 1)} aria-label="Page suivante"><ChevronRight size={17} /></button>
    </div>
  );
}
