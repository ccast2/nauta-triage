import { Outlet } from "react-router-dom";
import { Sidebar } from "./Sidebar";
import { TenantPicker } from "./TenantPicker";
import { Toaster } from "@/components/ui/sonner";

export function Layout() {
  return (
    <div className="min-h-screen flex">
      <Sidebar />
      <div className="flex-1 flex flex-col">
        <header className="h-14 border-b flex items-center justify-end px-6 gap-3">
          <span className="text-sm text-muted-foreground">Tenant:</span>
          <TenantPicker />
        </header>
        <main className="flex-1 p-6 overflow-auto">
          <Outlet />
        </main>
      </div>
      <Toaster />
    </div>
  );
}
