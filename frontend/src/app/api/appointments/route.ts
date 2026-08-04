import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";

export async function GET(request: NextRequest) {
  return proxyRequest(request, "appointment", "/appointments", {
    csrf: false,
  });
}

export async function POST(request: NextRequest) {
  return proxyRequest(request, "appointment", "/appointments");
}
