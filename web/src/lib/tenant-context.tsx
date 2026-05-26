import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

type Ctx = { tenantId: string | null; setTenantId: (id: string | null) => void };
const TenantContext = createContext<Ctx>({ tenantId: null, setTenantId: () => {} });

const STORAGE_KEY = "triage.tenantId";

export function TenantProvider({ children }: { children: ReactNode }) {
  const [tenantId, setTenantIdState] = useState<string | null>(
    () => localStorage.getItem(STORAGE_KEY),
  );
  useEffect(() => {
    if (tenantId) localStorage.setItem(STORAGE_KEY, tenantId);
    else localStorage.removeItem(STORAGE_KEY);
  }, [tenantId]);
  return (
    <TenantContext.Provider value={{ tenantId, setTenantId: setTenantIdState }}>
      {children}
    </TenantContext.Provider>
  );
}

export function useTenant() { return useContext(TenantContext); }
