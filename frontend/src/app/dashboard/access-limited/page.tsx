import { ShieldCheckIcon } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { requireDashboardRole } from "@/lib/server/current-user";

export default async function PatientAccessPage() {
  const user = await requireDashboardRole(["PATIENT"]);

  return (
    <section className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <div className="space-y-3">
        <p className="text-sm font-medium text-clinical-blue">Patient access</p>
        <h1 className="max-w-2xl text-3xl font-semibold tracking-tight sm:text-4xl">
          Your care stays between you and your clinician.
        </h1>
        <p className="max-w-2xl text-base leading-7 text-muted-foreground">
          This account is active, but the patient portal is not enabled in the current release. Clinical notes,
          care plans, appointments, and practice administration remain protected in the clinician workspace.
        </p>
      </div>

      <Card className="self-start">
        <CardHeader>
          <div className="mb-2 flex size-10 items-center justify-center rounded-full bg-status-success-bg text-status-success-fg">
            <ShieldCheckIcon className="size-5" />
          </div>
          <CardTitle>Access protected</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm text-muted-foreground">
          <p className="font-medium text-foreground">{user.fullName}</p>
          <p>{user.email}</p>
          <p>Contact your practice directly for appointment or care questions.</p>
        </CardContent>
      </Card>
    </section>
  );
}
