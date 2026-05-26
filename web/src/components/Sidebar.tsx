import { NavLink } from "react-router-dom";
import { LayoutDashboard, Boxes, Cable, FlaskConical, Users } from "lucide-react";
import { cn } from "@/lib/utils";

const items = [
  { to: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { to: "/sources", label: "Sources", icon: Cable },
  { to: "/stubs", label: "Stubs", icon: FlaskConical },
  { to: "/tenants", label: "Tenants", icon: Users },
];

export function Sidebar() {
  return (
    <aside className="w-56 border-r bg-slate-50 px-3 py-6 flex flex-col gap-1">
      <div className="px-3 mb-4">
        <div className="font-bold text-lg flex items-center gap-2"><Boxes className="w-5 h-5" /> Triage</div>
        <div className="text-xs text-muted-foreground">Control panel</div>
      </div>
      {items.map((it) => (
        <NavLink key={it.to} to={it.to}
          className={({ isActive }) => cn(
            "flex items-center gap-2 rounded-md px-3 py-2 text-sm",
            isActive ? "bg-slate-200 font-medium" : "hover:bg-slate-100",
          )}>
          <it.icon className="w-4 h-4" /> {it.label}
        </NavLink>
      ))}
    </aside>
  );
}
