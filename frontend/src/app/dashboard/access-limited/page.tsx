import { ShieldCheckIcon } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { requireDashboardRole } from "@/lib/server/current-user";

export default async function PatientAccessPage() {
  const user = await requireDashboardRole(["PATIENT"]);

  return (
    <section className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <div className="space-y-3">
        <p className="text-sm font-medium text-clinical-blue">Patient access</p>
        <h1 className="font-display max-w-2xl text-5xl leading-[0.95] tracking-[-0.04em] sm:text-6xl">
          Your care stays between you and your clinician.
        </h1>
        <p className="max-w-2xl text-base leading-7 text-muted-foreground">
          This account is active. Your clinician will share the information and next steps available to you.
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
          <p>Contact your practice directly with appointment or care questions.</p>
        </CardContent>
      </Card>
    </section>
  );
}
