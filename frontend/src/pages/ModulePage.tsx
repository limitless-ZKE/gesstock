import type { LucideIcon } from "lucide-react";
import { ArrowUpRight, Construction } from "lucide-react";

interface ModulePageProps {
  title: string;
  description: string;
  icon: LucideIcon;
  highlights: string[];
}

export default function ModulePage({ title, description, icon: Icon, highlights }: ModulePageProps) {
  return (
    <div className="space-y-7">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-2 text-xs font-bold uppercase tracking-[0.16em] text-emerald-600">Espace de travail</p>
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">{title}</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">{description}</p>
        </div>
        <span className="inline-flex w-fit items-center gap-2 rounded-full border border-amber-200 bg-amber-50 px-3 py-2 text-[11px] font-bold text-amber-700">
          <Construction size={14} />
          Structure prête
        </span>
      </div>

      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm md:p-8">
        <div className="flex size-12 items-center justify-center rounded-xl bg-emerald-50 text-emerald-700">
          <Icon size={22} />
        </div>
        <h2 className="mt-5 text-lg font-bold text-slate-900">Votre module {title.toLowerCase()}</h2>
        <p className="mt-2 max-w-xl text-sm leading-6 text-slate-500">
          Le routage, le layout et la couche API sont en place. Vous pouvez maintenant connecter les écrans métier aux endpoints GesStock.
        </p>
        <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {highlights.map((highlight) => (
            <div key={highlight} className="flex items-center justify-between rounded-xl border border-slate-100 bg-slate-50/70 px-4 py-3 text-sm font-semibold text-slate-700">
              {highlight}
              <ArrowUpRight size={15} className="text-slate-400" />
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
