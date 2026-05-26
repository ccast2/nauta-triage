import { Link, useParams } from "react-router-dom";
import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api, ApiError } from "@/lib/api";
import type { Simulator, Stub } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { JsonEditor } from "@/components/JsonEditor";

export default function StubsPage() {
  const { sourceName } = useParams();
  if (sourceName) return <StubEditor sourceName={sourceName} />;
  return <StubsIndex />;
}

function StubsIndex() {
  const q = useQuery({ queryKey: ["simulators"], queryFn: () => api<Simulator[]>("/admin/simulators") });
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Simulators</h1>
      {q.isLoading && <Skeleton className="h-40" />}
      <div className="grid grid-cols-2 gap-4">
        {q.data?.map((s) => (
          <Link key={s.source_name} to={`/stubs/${s.source_name}`}>
            <Card className="hover:bg-slate-50 transition-colors">
              <CardHeader>
                <CardTitle className="flex items-center justify-between">
                  {s.source_name}
                  {s.reachable ? <Badge>online</Badge> : <Badge variant="destructive">offline</Badge>}
                </CardTitle>
              </CardHeader>
              <CardContent className="text-xs text-muted-foreground">{s.base_url}</CardContent>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}

function StubEditor({ sourceName }: { sourceName: string }) {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ["stubs", sourceName], queryFn: () => api<Stub[]>(`/admin/simulators/${sourceName}/stubs`) });
  const [newId, setNewId] = useState("");
  const [newBody, setNewBody] = useState<object>({ container_id: "", events: [], eta: "" });

  const upsert = useMutation({
    mutationFn: ({ containerId, body }: { containerId: string; body: object }) =>
      api<Stub>(`/admin/simulators/${sourceName}/stubs/${containerId}`, {
        method: "PUT", body: { status: 200, body },
      }),
    onSuccess: () => { toast.success("Stub saved"); qc.invalidateQueries({ queryKey: ["stubs", sourceName] }); },
    onError: (e: ApiError) => toast.error(`Save failed: ${e.message}`),
  });

  const remove = useMutation({
    mutationFn: (containerId: string) =>
      api(`/admin/simulators/${sourceName}/stubs/${containerId}`, { method: "DELETE" }),
    onSuccess: () => { toast.success("Stub deleted"); qc.invalidateQueries({ queryKey: ["stubs", sourceName] }); },
    onError: (e: ApiError) => toast.error(`Delete failed: ${e.message}`),
  });

  return (
    <div className="space-y-4">
      <div>
        <Link to="/stubs" className="text-sm text-muted-foreground hover:underline">← simulators</Link>
        <h1 className="text-2xl font-bold mt-1">{sourceName} stubs</h1>
      </div>

      <Card>
        <CardHeader><CardTitle>+ Add stub</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          <div>
            <Label>Container business ID</Label>
            <Input value={newId} onChange={(e) => setNewId(e.target.value)} placeholder="MSCU-XXXX" />
          </div>
          <div>
            <Label>Response body</Label>
            <JsonEditor value={newBody} onValid={setNewBody} height={180} />
          </div>
          <Button disabled={!newId || upsert.isPending}
                  onClick={() => upsert.mutate({ containerId: newId, body: newBody })}>
            Save stub
          </Button>
        </CardContent>
      </Card>

      {q.isLoading && <Skeleton className="h-40" />}
      <div className="space-y-3">
        {q.data?.map((s) => (
          <StubCard key={s.id} stub={s}
                    onSave={(body) => upsert.mutate({ containerId: s.container_id, body })}
                    onDelete={() => remove.mutate(s.container_id)} />
        ))}
        {q.data?.length === 0 && <div className="text-muted-foreground">No stubs configured.</div>}
      </div>
    </div>
  );
}

function StubCard({ stub, onSave, onDelete }: {
  stub: Stub; onSave: (body: object) => void; onDelete: () => void;
}) {
  const [body, setBody] = useState<object>(stub.body);
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          <span>{stub.container_id}</span>
          <span className="text-sm text-muted-foreground">HTTP {stub.status}</span>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        <JsonEditor value={body} onValid={setBody} height={160} />
        <div className="flex gap-2">
          <Button size="sm" onClick={() => onSave(body)}>Save</Button>
          <Button size="sm" variant="destructive" onClick={onDelete}>Delete</Button>
        </div>
      </CardContent>
    </Card>
  );
}
