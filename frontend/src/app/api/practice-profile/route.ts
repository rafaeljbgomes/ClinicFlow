import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";

export async function GET(request: NextRequest) {
  return proxyRequest(request, "clinical", "/practice-profile", { csrf: false });
}

export async function PUT(request: NextRequest) {
  return proxyRequest(request, "clinical", "/practice-profile");
}
