import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useTenant } from "@/lib/tenant-context";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { Tenant } from "@/lib/types";

export function TenantPicker() {
  const { tenantId, setTenantId } = useTenant();
  const { data, isLoading } = useQuery({
    queryKey: ["tenants"],
    queryFn: () => api<Tenant[]>("/admin/tenants"),
  });
  if (isLoading) return <div className="text-sm text-muted-foreground">Loading tenants…</div>;
  return (
    <Select value={tenantId ?? ""} onValueChange={(v) => setTenantId(v || null)}>
      <SelectTrigger className="w-[260px]"><SelectValue placeholder="Select tenant" /></SelectTrigger>
      <SelectContent>
        {(data ?? []).map((t) => (
          <SelectItem key={t.id} value={t.id}>{t.name}</SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
