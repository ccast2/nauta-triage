import { useParams, Link, useNavigate } from "react-router-dom";
import { useState, useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api, ApiError } from "@/lib/api";
import { useTenant } from "@/lib/tenant-context";
import type { SourceDetail } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { JsonEditor } from "@/components/JsonEditor";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

export default function SourceDetailPage() {
  const { id = "" } = useParams();
  const { tenantId } = useTenant();
  const nav = useNavigate();
  const qc = useQueryClient();

  const q = useQuery({ queryKey: ["source", id], queryFn: () => api<SourceDetail>(`/admin/sources/${id}`) });

  const [configJson, setConfigJson] = useState<object>({});
  const [mappingJson, setMappingJson] = useState<object>({});
  const [polling, setPolling] = useState<number | "">("");
  useEffect(() => {
    if (q.data) {
      setConfigJson(q.data.config_json ?? {});
      setMappingJson(q.data.mapping_json ?? {});
      setPolling(q.data.polling_interval_sec ?? "");
    }
  }, [q.data]);

  const save = useMutation({
    mutationFn: () => api(`/admin/sources/${id}`, {
      method: "PATCH",
      body: {
        polling_interval_sec: polling === "" ? null : polling,
        config_json: configJson, mapping_json: mappingJson,
      },
    }),
    onSuccess: () => { toast.success("Saved"); qc.invalidateQueries({ queryKey: ["source", id] }); qc.invalidateQueries({ queryKey: ["sources"] }); },
    onError: (e: ApiError) => toast.error(`Save failed: ${e.message}`),
  });

  const del = useMutation({
    mutationFn: () => api(`/admin/sources/${id}`, { method: "DELETE" }),
    onSuccess: () => { toast.success("Source deleted"); nav("/sources"); },
    onError: (e: ApiError) => toast.error(`Delete failed: ${e.message}`),
  });

  const addSub = useMutation({
    mutationFn: (containerId: string) =>
      api<{ subscriptions: Array<{ tenant_id: string; container_id: string }> }>(
        `/admin/sources/${id}/subscriptions`,
        { method: "POST", body: { tenant_id: tenantId, container_business_id: containerId } },
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["source", id] }),
    onError: (e: ApiError) => toast.error(`Subscribe failed: ${e.message}`),
  });

  const removeSub = useMutation({
    mutationFn: (sub: { tenant_id: string; container_id: string }) =>
      api(`/admin/sources/${id}/subscriptions`, {
        method: "DELETE", body: { tenant_id: sub.tenant_id, container_business_id: sub.container_id },
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["source", id] }),
    onError: (e: ApiError) => toast.error(`Unsubscribe failed: ${e.message}`),
  });

  const [newContainer, setNewContainer] = useState("");

  if (q.isLoading) return <Skeleton className="h-40" />;
  if (!q.data) return null;

  const subs = ((q.data.config_json?.subscriptions ?? []) as Array<{ tenant_id: string; container_id: string }>);

  return (
    <div className="space-y-4">
      <div>
        <Link to="/sources" className="text-sm text-muted-foreground hover:underline">← sources</Link>
        <h1 className="text-2xl font-bold mt-1">{q.data.name}</h1>
        <div className="text-sm text-muted-foreground">{q.data.connector_type} · {q.data.enabled ? "enabled" : "disabled"}</div>
      </div>
      <Tabs defaultValue="config">
        <TabsList>
          <TabsTrigger value="config">Config</TabsTrigger>
          <TabsTrigger value="mapping">Mapping</TabsTrigger>
          <TabsTrigger value="subs">Subscriptions ({subs.length})</TabsTrigger>
        </TabsList>
        <TabsContent value="config" className="space-y-4">
          <div className="max-w-xs">
            <Label>Polling interval (sec)</Label>
            <Input type="number" value={polling} onChange={(e) => setPolling(e.target.value === "" ? "" : Number(e.target.value))} />
          </div>
          <Card><CardHeader><CardTitle>config_json</CardTitle></CardHeader>
            <CardContent><JsonEditor value={configJson} onValid={setConfigJson} height={250} /></CardContent></Card>
          <div className="flex gap-2">
            <Button onClick={() => save.mutate()} disabled={save.isPending}>Save</Button>
            <Button variant="destructive" onClick={() => del.mutate()}>Delete source</Button>
          </div>
        </TabsContent>
        <TabsContent value="mapping" className="space-y-4">
          <Card><CardHeader><CardTitle>mapping_json</CardTitle></CardHeader>
            <CardContent><JsonEditor value={mappingJson} onValid={setMappingJson} height={250} /></CardContent></Card>
          <Button onClick={() => save.mutate()} disabled={save.isPending}>Save</Button>
        </TabsContent>
        <TabsContent value="subs" className="space-y-4">
          {!tenantId && <div className="text-muted-foreground">Pick a tenant in header to add subscriptions.</div>}
          {tenantId && (
            <div className="flex gap-2">
              <Input placeholder="container business id" value={newContainer} onChange={(e) => setNewContainer(e.target.value)} />
              <Button onClick={() => { if (newContainer) { addSub.mutate(newContainer); setNewContainer(""); } }}>Subscribe</Button>
            </div>
          )}
          <Table>
            <TableHeader><TableRow><TableHead>Tenant</TableHead><TableHead>Container</TableHead><TableHead></TableHead></TableRow></TableHeader>
            <TableBody>
              {subs.map((s) => (
                <TableRow key={s.tenant_id + s.container_id}>
                  <TableCell className="font-mono text-xs">{s.tenant_id}</TableCell>
                  <TableCell>{s.container_id}</TableCell>
                  <TableCell><Button size="sm" variant="outline" onClick={() => removeSub.mutate(s)}>Remove</Button></TableCell>
                </TableRow>
              ))}
              {subs.length === 0 && <TableRow><TableCell colSpan={3} className="text-center text-muted-foreground">No subscriptions.</TableCell></TableRow>}
            </TableBody>
          </Table>
        </TabsContent>
      </Tabs>
    </div>
  );
}
