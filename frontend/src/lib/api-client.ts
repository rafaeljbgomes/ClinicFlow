"use client";

export class ApiClientError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
    public readonly correlationId?: string,
    public readonly fieldErrors?: Record<string, string[]>
  ) {
    super(message);
    this.name = "ApiClientError";
  }
}

type ApiFetchOptions = Omit<RequestInit, "body"> & {
  body?: BodyInit | object | null;
};

export async function apiFetch<T>(
  path: string,
  options: ApiFetchOptions = {}
): Promise<T> {
  const method = options.method ?? "GET";
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");

  let body = options.body;
  if (body && typeof body === "object" && !(body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(body);
  }

  if (["POST", "PUT", "PATCH", "DELETE"].includes(method.toUpperCase())) {
    const csrf = readCookie("clinicflow_csrf");
    if (csrf) {
      headers.set("X-CSRF-Token", csrf);
    }
  }

  const response = await fetch(path, {
    ...options,
    method,
    headers,
    body: body as BodyInit | undefined,
  });

  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    if (response.status === 401 && !path.endsWith("/auth/login") && typeof window !== "undefined") {
      window.location.assign("/login");
    }
    throw new ApiClientError(
      payload?.message ?? "The request could not be completed",
      response.status,
      payload?.code,
      payload?.correlationId,
      payload?.fieldErrors
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export const swrFetcher = <T>(path: string) => apiFetch<T>(path);

function readCookie(name: string) {
  if (typeof document === "undefined") {
    return null;
  }
  return document.cookie
    .split("; ")
    .find((entry) => entry.startsWith(`${name}=`))
    ?.split("=")[1];
}
