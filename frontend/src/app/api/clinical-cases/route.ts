import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";

export async function GET(request: NextRequest) {
  const { search } = new URL(request.url);
  return proxyRequest(request, "clinical", `/clinical-cases${search}`, {
    csrf: false,
  });
}

export async function POST(request: NextRequest) {
  return proxyRequest(request, "clinical", "/clinical-cases");
}
