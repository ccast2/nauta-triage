import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useTenant } from "@/lib/tenant-context";
import type { Tenant } from "@/lib/types";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

export default function TenantsPage() {
  const { tenantId, setTenantId } = useTenant();
  const q = useQuery({ queryKey: ["tenants"], queryFn: () => api<Tenant[]>("/admin/tenants") });

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Tenants</h1>
      <p className="text-sm text-muted-foreground">Demo bearer tokens are exposed here for testing only — MVP.</p>
      {q.isLoading && <Skeleton className="h-40" />}
      {q.data && (
        <Table>
          <TableHeader>
            <TableRow><TableHead>Name</TableHead><TableHead>ID</TableHead><TableHead>Demo token</TableHead><TableHead></TableHead></TableRow>
          </TableHeader>
          <TableBody>
            {q.data.map((t) => (
              <TableRow key={t.id} className={tenantId === t.id ? "bg-slate-50" : ""}>
                <TableCell>{t.name}</TableCell>
                <TableCell className="font-mono text-xs">{t.id}</TableCell>
                <TableCell className="font-mono text-xs">{t.demo_token ?? "—"}</TableCell>
                <TableCell>
                  <Button size="sm" variant={tenantId === t.id ? "default" : "outline"} onClick={() => setTenantId(t.id)}>
                    {tenantId === t.id ? "Selected" : "Use"}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
