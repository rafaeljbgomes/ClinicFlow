import { RefreshCwIcon } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

export const STALE_VERSION_MESSAGE =
  "This record changed in another tab or device after you opened it. Your changes were not saved.";

// Shown in a form whose save was rejected as stale (412). The form keeps what was typed; reloading
// replaces it with the latest saved version, so nothing is merged behind the user's back.
export function StaleVersionAlert({ onReload }: { onReload: () => void | Promise<void> }) {
  return (
    <Alert variant="destructive" className="mb-4">
      <AlertTitle>Changed elsewhere</AlertTitle>
      <AlertDescription className="flex flex-col items-start gap-3">
        <p>{STALE_VERSION_MESSAGE}</p>
        <Button type="button" variant="outline" size="sm" onClick={() => void onReload()}>
          <RefreshCwIcon data-icon="inline-start" />
          Reload latest
        </Button>
      </AlertDescription>
    </Alert>
  );
}
