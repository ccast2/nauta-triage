import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { api, ApiError } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { JsonEditor } from "@/components/JsonEditor";

export default function SourceNewPage() {
  const nav = useNavigate();
  const [name, setName] = useState("");
  const [connectorType, setConnectorType] = useState("carrier_portal");
  const [pollingIntervalSec, setPollingIntervalSec] = useState<number | "">(30);
  const [supportsWebhook, setSupportsWebhook] = useState(false);
  const [enabled, setEnabled] = useState(true);
  const [configJson, setConfigJson] = useState<object>({ base_url: "", subscriptions: [] });
  const [mappingJson, setMappingJson] = useState<object>({});

  const mut = useMutation({
    mutationFn: () => api("/admin/sources", {
      method: "POST",
      body: {
        name, connector_type: connectorType,
        polling_interval_sec: pollingIntervalSec === "" ? null : pollingIntervalSec,
        supports_webhook: supportsWebhook, enabled,
        config_json: configJson, mapping_json: mappingJson,
      },
    }),
    onSuccess: () => { toast.success("Source created"); nav("/sources"); },
    onError: (e: ApiError) => toast.error(`Create failed: ${e.message} (${e.code})`),
  });

  return (
    <Card className="max-w-3xl">
      <CardHeader><CardTitle>New source</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        <Field label="Name *"><Input value={name} onChange={(e) => setName(e.target.value)} /></Field>
        <Field label="Connector type">
          <Select value={connectorType} onValueChange={setConnectorType}>
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="carrier_portal">carrier_portal</SelectItem>
              <SelectItem value="terminal_feed">terminal_feed</SelectItem>
              <SelectItem value="partner_webhook">partner_webhook</SelectItem>
            </SelectContent>
          </Select>
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Polling interval (sec)">
            <Input type="number" value={pollingIntervalSec}
                   onChange={(e) => setPollingIntervalSec(e.target.value === "" ? "" : Number(e.target.value))} />
          </Field>
          <Field label="Flags">
            <div className="flex gap-2 mt-2">
              <Button size="sm" variant={enabled ? "default" : "outline"} onClick={() => setEnabled(!enabled)}>{enabled ? "Enabled" : "Disabled"}</Button>
              <Button size="sm" variant={supportsWebhook ? "default" : "outline"} onClick={() => setSupportsWebhook(!supportsWebhook)}>{supportsWebhook ? "Webhook on" : "Webhook off"}</Button>
            </div>
          </Field>
        </div>
        <Field label="config_json"><JsonEditor value={configJson} onValid={setConfigJson} /></Field>
        <Field label="mapping_json"><JsonEditor value={mappingJson} onValid={setMappingJson} /></Field>
        <Button onClick={() => mut.mutate()} disabled={mut.isPending}>{mut.isPending ? "Creating…" : "Create"}</Button>
      </CardContent>
    </Card>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="space-y-1.5"><Label>{label}</Label>{children}</div>;
}
