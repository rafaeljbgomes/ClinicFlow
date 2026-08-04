import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";

export async function POST(request: NextRequest) {
  return proxyRequest(request, "auth", "/auth/register", {
    auth: false,
    csrf: false,
  });
}
