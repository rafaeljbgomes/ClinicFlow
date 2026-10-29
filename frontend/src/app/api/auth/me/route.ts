import { NextResponse } from "next/server";
import { jsonError } from "@/lib/server/bff";
import { getCurrentSession } from "@/lib/server/current-user";

// The profile comes from the verified access token; no service call is needed.
export async function GET() {
  const session = await getCurrentSession();
  if (session.status === "unavailable") return jsonError(503, "Session could not be verified");
  if (session.status !== "authenticated") return jsonError(401, "Authentication is required");
  return NextResponse.json(session.user, { headers: { "Cache-Control": "no-store" } });
}
