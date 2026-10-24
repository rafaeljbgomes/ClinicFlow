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
  // The version a write is based on (ADR-024): a number sends If-Match, null means the record must not
  // exist yet (If-None-Match: *). Pass the version captured when the form opened, not a refreshed one.
  expectedVersion?: number | null;
};

// The record changed elsewhere after it was loaded, so the write was rejected and nothing was saved.
export function isStaleVersion(error: unknown) {
  return error instanceof ApiClientError && error.status === 412;
}

export async function apiFetch<T>(
  path: string,
  { expectedVersion, ...options }: ApiFetchOptions = {}
): Promise<T> {
  const method = options.method ?? "GET";
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (expectedVersion === null) {
    headers.set("If-None-Match", "*");
  } else if (expectedVersion !== undefined) {
    headers.set("If-Match", `"${expectedVersion}"`);
  }

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
