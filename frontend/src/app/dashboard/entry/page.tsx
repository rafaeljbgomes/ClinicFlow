import { redirect } from "next/navigation";
import { homeForRole, requireCurrentUser } from "@/lib/server/current-user";

export default async function DashboardEntryPage() {
  const user = await requireCurrentUser();
  redirect(homeForRole(user.role));
}
