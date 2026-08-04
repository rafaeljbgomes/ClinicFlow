import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ACCESS_TOKEN_COOKIE } from "@/lib/server/auth-cookies";

export default async function Home() {
  const cookieStore = await cookies();
  redirect(cookieStore.has(ACCESS_TOKEN_COOKIE) ? "/dashboard" : "/login");
}
