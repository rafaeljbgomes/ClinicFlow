import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";

type RouteContext = {
  params: Promise<{ id: string }>;
};

export async function GET(request: NextRequest, context: RouteContext) {
  const { id } = await context.params;
  return proxyRequest(request, "clinical", `/clinical-cases/${id}`, { csrf: false });
}
