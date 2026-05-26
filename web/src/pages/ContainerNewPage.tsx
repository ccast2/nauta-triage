import { useNavigate } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { api, ApiError } from "@/lib/api";
import { useTenant } from "@/lib/tenant-context";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const schema = z.object({
  business_id: z.string().min(1, "required"),
  carrier: z.string().optional(),
  pol: z.string().optional(),
  pod: z.string().optional(),
  declared_eta: z.string().optional(),
});
type FormValues = z.infer<typeof schema>;

export default function ContainerNewPage() {
  const { tenantId } = useTenant();
  const nav = useNavigate();
  const { register, handleSubmit, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });
  const mut = useMutation({
    mutationFn: (v: FormValues) =>
      api("/admin/containers", { method: "POST", body: v, tenantId }),
    onSuccess: (_, vars) => { toast.success("Container created"); nav(`/containers/${vars.business_id}`); },
    onError: (e: ApiError) => toast.error(`Create failed: ${e.message} (${e.code})`),
  });

  if (!tenantId) return <div className="text-muted-foreground">Select a tenant first.</div>;
  return (
    <Card className="max-w-2xl">
      <CardHeader><CardTitle>New container</CardTitle></CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={handleSubmit((v) => mut.mutate(v))}>
          <Field label="Business ID *" error={errors.business_id?.message}>
            <Input {...register("business_id")} placeholder="MSCU-1234" />
          </Field>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Carrier"><Input {...register("carrier")} /></Field>
            <Field label="Declared ETA"><Input type="date" {...register("declared_eta")} /></Field>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Field label="POL"><Input {...register("pol")} /></Field>
            <Field label="POD"><Input {...register("pod")} /></Field>
          </div>
          <Button type="submit" disabled={mut.isPending}>{mut.isPending ? "Creating…" : "Create"}</Button>
        </form>
      </CardContent>
    </Card>
  );
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label>{label}</Label>
      {children}
      {error && <p className="text-sm text-red-600">{error}</p>}
    </div>
  );
}
