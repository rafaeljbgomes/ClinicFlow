import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";

export async function GET(request: NextRequest) {
  return proxyRequest(request, "auth", "/users/me", { csrf: false });
}
