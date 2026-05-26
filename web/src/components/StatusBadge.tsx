import { Badge } from "@/components/ui/badge";
import type { ContainerStatus } from "@/lib/types";

const styles: Record<ContainerStatus | "UNKNOWN", string> = {
  ON_TRACK: "bg-green-100 text-green-800 border-green-200",
  DELAYED: "bg-amber-100 text-amber-900 border-amber-200",
  NEEDS_REVIEW: "bg-orange-100 text-orange-900 border-orange-200",
  LOST: "bg-red-100 text-red-900 border-red-200",
  UNKNOWN: "bg-slate-100 text-slate-700 border-slate-200",
};

export function StatusBadge({ status }: { status: ContainerStatus | null | undefined }) {
  const key = status ?? "UNKNOWN";
  return <Badge variant="outline" className={styles[key]}>{key}</Badge>;
}
