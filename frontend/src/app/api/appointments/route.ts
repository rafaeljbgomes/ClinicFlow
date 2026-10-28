import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";
import { forwardedQuery } from "@/lib/server/list-query";

const LIST_PARAMETERS = ["from", "to", "status", "order", "page", "size"] as const;

export async function GET(request: NextRequest) {
  return proxyRequest(request, "appointment", `/appointments${forwardedQuery(request, LIST_PARAMETERS)}`, {
    csrf: false,
  });
}

export async function POST(request: NextRequest) {
  return proxyRequest(request, "appointment", "/appointments");
}
