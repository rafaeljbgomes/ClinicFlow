import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ id: string }> }
) {
  const { id } = await context.params;
  return proxyRequest(request, "auth", `/users/${encodeURIComponent(id)}`, {
    csrf: false,
    roles: ["ADMIN"],
  });
}
