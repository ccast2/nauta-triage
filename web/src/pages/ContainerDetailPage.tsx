import { useParams, useNavigate, Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { api, ApiError } from "@/lib/api";
import { useTenant } from "@/lib/tenant-context";
import type { ContainerDetail } from "@/lib/types";
import { StatusBadge } from "@/components/StatusBadge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";

export default function ContainerDetailPage() {
  const { businessId = "" } = useParams();
  const { tenantId } = useTenant();
  const nav = useNavigate();
  const qc = useQueryClient();
  const [deleteOpen, setDeleteOpen] = useState(false);

  const q = useQuery({
    enabled: !!tenantId,
    queryKey: ["container", tenantId, businessId],
    queryFn: () => api<ContainerDetail>(`/admin/containers/${businessId}`, { tenantId }),
    refetchInterval: 5_000,
  });

  const del = useMutation({
    mutationFn: () => api(`/admin/containers/${businessId}`, { method: "DELETE", tenantId }),
    onSuccess: () => { toast.success("Container deleted"); qc.invalidateQueries({ queryKey: ["containers", tenantId] }); nav("/dashboard"); },
    onError: (e: ApiError) => toast.error(`Delete failed: ${e.message}`),
  });

  if (!tenantId) return <div className="text-muted-foreground">Select a tenant first.</div>;
  if (q.isLoading) return <Skeleton className="h-40" />;
  if (q.isError) return <div className="text-red-600">Error: {(q.error as ApiError).message}</div>;
  if (!q.data) return null;

  const d = q.data;

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between">
        <div>
          <Link to="/dashboard" className="text-sm text-muted-foreground hover:underline">← back</Link>
          <h1 className="text-2xl font-bold mt-1">{d.container.business_id}</h1>
          <div className="text-sm text-muted-foreground">
            {d.container.carrier ?? "no carrier"} · {d.container.pol ?? "—"} → {d.container.pod ?? "—"} · ETA {d.container.declared_eta ?? "—"}
          </div>
        </div>
        <div className="flex items-center gap-3">
          <StatusBadge status={d.container.current_status} />
          <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
            <DialogTrigger asChild><Button variant="destructive">Delete</Button></DialogTrigger>
            <DialogContent>
              <DialogHeader><DialogTitle>Delete container?</DialogTitle></DialogHeader>
              <p>This will also drop its decisions, events and current state. This cannot be undone.</p>
              <DialogFooter>
                <Button variant="outline" onClick={() => setDeleteOpen(false)}>Cancel</Button>
                <Button variant="destructive" disabled={del.isPending} onClick={() => del.mutate()}>Delete</Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>
      </div>

      {d.status && (
        <Card>
          <CardHeader><CardTitle>Current state</CardTitle></CardHeader>
          <CardContent className="space-y-1 text-sm">
            <div><b>Reasoning:</b> {d.status.reasoning}</div>
            <div><b>Confidence:</b> {d.status.confidence} · <b>Next action:</b> {d.status.next_action} · <b>Path:</b> {d.status.decided_by_path}</div>
            <div><b>Updated:</b> {new Date(d.status.updated_at).toLocaleString()}</div>
          </CardContent>
        </Card>
      )}

      <Tabs defaultValue="decisions">
        <TabsList>
          <TabsTrigger value="decisions">Decisions ({d.decisions.length})</TabsTrigger>
          <TabsTrigger value="events">Raw events ({d.raw_events.length})</TabsTrigger>
        </TabsList>
        <TabsContent value="decisions" className="space-y-2">
          {d.decisions.length === 0 && <div className="text-muted-foreground">No decisions yet — wait for the next reconciliation tick.</div>}
          {d.decisions.map((dec) => (
            <Card key={dec.id}>
              <CardContent className="pt-4 text-sm space-y-1">
                <div className="flex items-center gap-2">
                  <StatusBadge status={dec.status} />
                  <span className="text-muted-foreground">path={dec.path} · conf={dec.confidence} · {new Date(dec.decided_at).toLocaleString()}</span>
                </div>
                <div>{dec.reasoning}</div>
                <div className="text-muted-foreground">next_action={dec.next_action} · latency={dec.latency_ms}ms</div>
              </CardContent>
            </Card>
          ))}
        </TabsContent>
        <TabsContent value="events" className="space-y-2">
          {d.raw_events.length === 0 && <div className="text-muted-foreground">No raw events ingested yet.</div>}
          {d.raw_events.map((e) => (
            <Card key={e.id}>
              <CardContent className="pt-4 text-sm space-y-1">
                <div className="text-muted-foreground">{new Date(e.received_at).toLocaleString()} · source={e.source_id} · status={e.processing_status ?? "—"}</div>
                <pre className="text-xs bg-slate-50 p-2 rounded overflow-x-auto">{JSON.stringify(e.payload, null, 2)}</pre>
              </CardContent>
            </Card>
          ))}
        </TabsContent>
      </Tabs>
    </div>
  );
}
