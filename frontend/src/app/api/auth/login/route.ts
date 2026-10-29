import { NextRequest } from "next/server";
import { beginAuthorization } from "@/lib/server/auth-routes";

export async function GET(request: NextRequest) {
  return beginAuthorization(request, "login");
}
