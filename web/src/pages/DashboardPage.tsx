import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useTenant } from "@/lib/tenant-context";
import type { ContainerSummary } from "@/lib/types";
import { StatusBadge } from "@/components/StatusBadge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";

export default function DashboardPage() {
  const { tenantId } = useTenant();
  const q = useQuery({
    enabled: !!tenantId,
    queryKey: ["containers", tenantId],
    queryFn: () => api<ContainerSummary[]>("/admin/containers", { tenantId }),
    refetchInterval: 5_000,
  });

  if (!tenantId) {
    return <div className="text-muted-foreground">Select a tenant from the header to continue.</div>;
  }
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Containers</h1>
        <Button asChild><Link to="/containers/new">+ New container</Link></Button>
      </div>
      {q.isLoading && <Skeleton className="h-40" />}
      {q.data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Business ID</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Confidence</TableHead>
              <TableHead>Next action</TableHead>
              <TableHead>ETA</TableHead>
              <TableHead>Updated</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {q.data.map((c) => (
              <TableRow key={c.id} className="cursor-pointer">
                <TableCell><Link to={`/containers/${c.business_id}`} className="text-blue-700 underline">{c.business_id}</Link></TableCell>
                <TableCell><StatusBadge status={c.current_status} /></TableCell>
                <TableCell>{c.current_confidence ?? "—"}</TableCell>
                <TableCell>{c.next_action ?? "—"}</TableCell>
                <TableCell>{c.declared_eta ?? "—"}</TableCell>
                <TableCell>{new Date(c.updated_at).toLocaleString()}</TableCell>
              </TableRow>
            ))}
            {q.data.length === 0 && (
              <TableRow><TableCell colSpan={6} className="text-center text-muted-foreground">No containers yet.</TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
