import { Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api, ApiError } from "@/lib/api";
import type { SourceSummary } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";

export default function SourcesPage() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ["sources"], queryFn: () => api<SourceSummary[]>("/admin/sources") });
  const toggle = useMutation({
    mutationFn: (s: SourceSummary) =>
      api(`/admin/sources/${s.id}`, { method: "PATCH", body: { enabled: !s.enabled } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["sources"] }),
    onError: (e: ApiError) => toast.error(`Toggle failed: ${e.message}`),
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Sources</h1>
        <Button asChild><Link to="/sources/new">+ New source</Link></Button>
      </div>
      {q.isLoading && <Skeleton className="h-40" />}
      {q.data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Connector</TableHead>
              <TableHead>Enabled</TableHead>
              <TableHead>Polling (s)</TableHead>
              <TableHead>Webhook</TableHead>
              <TableHead>Subscriptions</TableHead>
              <TableHead></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {q.data.map((s) => (
              <TableRow key={s.id}>
                <TableCell><Link to={`/sources/${s.id}`} className="text-blue-700 underline">{s.name}</Link></TableCell>
                <TableCell>{s.connector_type}</TableCell>
                <TableCell>
                  <Button size="sm" variant={s.enabled ? "default" : "destructive"}
                          onClick={() => {
                            const verb = s.enabled ? "disable" : "enable";
                            if (window.confirm(`${verb} source "${s.name}"? ${s.enabled ? "Polling will stop." : "Polling will resume on the next tick."}`)) {
                              toggle.mutate(s);
                            }
                          }}>
                    {s.enabled ? "Enabled — click to disable" : "Disabled — click to enable"}
                  </Button>
                </TableCell>
                <TableCell>{s.polling_interval_sec ?? "—"}</TableCell>
                <TableCell>{s.supports_webhook ? <Badge>yes</Badge> : "—"}</TableCell>
                <TableCell>{s.subscriptions_count}</TableCell>
                <TableCell></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
