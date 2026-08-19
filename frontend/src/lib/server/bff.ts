import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";
import { ACCESS_TOKEN_COOKIE, CSRF_COOKIE } from "@/lib/server/auth-cookies";
import type { Role, UserView } from "@/lib/types";

type ServiceName = "auth" | "patient" | "appointment" | "clinical" | "notification";

const serviceUrls: Record<ServiceName, string> = {
  auth: process.env.AUTH_SERVICE_URL ?? "http://localhost:8081",
  patient: process.env.PATIENT_SERVICE_URL ?? "http://localhost:8082",
  appointment: process.env.APPOINTMENT_SERVICE_URL ?? "http://localhost:8083",
  clinical: process.env.CLINICAL_SERVICE_URL ?? "http://localhost:8085",
  notification: process.env.NOTIFICATION_SERVICE_URL ?? "http://localhost:8084",
};

const serviceHealthUrls: Record<ServiceName, string> = {
  auth: process.env.AUTH_SERVICE_HEALTH_URL ?? serviceUrls.auth,
  patient: process.env.PATIENT_SERVICE_HEALTH_URL ?? serviceUrls.patient,
  appointment: process.env.APPOINTMENT_SERVICE_HEALTH_URL ?? serviceUrls.appointment,
  clinical: process.env.CLINICAL_SERVICE_HEALTH_URL ?? serviceUrls.clinical,
  notification: process.env.NOTIFICATION_SERVICE_HEALTH_URL ?? serviceUrls.notification,
};

type ProxyOptions = {
  auth?: boolean;
  csrf?: boolean;
  roles?: Role[];
  project?: (payload: unknown, user: UserView | null) => unknown;
};

const BACKEND_TIMEOUT_MS = 10_000;

export type ApiError = {
  status: number;
  message: string;
  code?: string;
  correlationId?: string;
  fieldErrors?: Record<string, string[]>;
};

type SafeProblemFields = Omit<ApiError, "status" | "message">;

export function jsonError(status: number, message: string, fields: SafeProblemFields = {}) {
  return NextResponse.json<ApiError>({ status, message, ...fields }, { status });
}

export async function proxyRequest(
  request: NextRequest,
  service: ServiceName,
  path: string,
  options: ProxyOptions = {}
) {
  const shouldAuthenticate = options.auth ?? true;
  const shouldCheckCsrf = options.csrf ?? isMutating(request.method);

  if (shouldCheckCsrf) {
    const csrfError = await validateCsrf(request);
    if (csrfError) {
      return csrfError;
    }
  }

  const cookieStore = await cookies();
  const token = cookieStore.get(ACCESS_TOKEN_COOKIE)?.value;
  let sessionUser: UserView | null = null;
  if (shouldAuthenticate && !token) {
    return jsonError(401, "Authentication is required");
  }

  if (token && options.roles?.length) {
    const identity = await backendGet("auth", "/users/me", token);
    const user = identity.data as Partial<UserView> | null;
    if (!identity.ok) return jsonError(identity.status === 401 ? 401 : 503, "Session could not be verified");
    if (!user?.role || !options.roles.includes(user.role)) {
      return jsonError(403, "You are not allowed to perform this action");
    }
    sessionUser = user as UserView;
  }

  try {
    const body = await request.text();
    const response = await fetch(`${serviceUrls[service]}${path}`, {
      method: request.method,
      headers: backendHeaders(request, token),
      body: body.length > 0 ? body : undefined,
      cache: "no-store",
      signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
    });

    if (response.ok && response.status !== 204 && options.project) {
      return NextResponse.json(options.project(await readJson(response), sessionUser), { status: response.status });
    }
    return normalizeBackendResponse(response);
  } catch {
    return jsonError(503, "The service is temporarily unavailable");
  }
}

export async function backendGet(service: ServiceName, path: string, token?: string) {
  return backendGetFromBaseUrl(serviceUrls[service], path, token);
}

export async function backendHealthGet(service: ServiceName, path: string, token?: string) {
  return backendGetFromBaseUrl(serviceHealthUrls[service], path, token);
}

async function backendGetFromBaseUrl(baseUrl: string, path: string, token?: string) {
  const started = performance.now();
  try {
    const response = await fetch(`${baseUrl}${path}`, {
      method: "GET",
      headers: backendHeaders(undefined, token),
      cache: "no-store",
      signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
    });
    const latencyMs = Math.round(performance.now() - started);
    const parsed = await readJsonResult(response);
    return {
      ok: response.ok,
      status: response.status,
      latencyMs,
      data: parsed.data,
      invalidJson: parsed.invalidJson,
      unavailable: false,
    };
  } catch {
    return {
      ok: false,
      status: 503,
      latencyMs: Math.round(performance.now() - started),
      data: null,
      invalidJson: false,
      unavailable: true,
    };
  }
}

export async function readTokenCookie() {
  const cookieStore = await cookies();
  return cookieStore.get(ACCESS_TOKEN_COOKIE)?.value;
}

function backendHeaders(request?: NextRequest, token?: string): HeadersInit {
  const correlationId =
    request?.headers.get("x-correlation-id") ?? crypto.randomUUID();
  const headers: HeadersInit = {
    Accept: "application/json",
    "Content-Type": "application/json",
    "X-Correlation-Id": correlationId,
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

async function normalizeBackendResponse(response: Response) {
  const payload = await readJson(response);
  if (!response.ok) {
    return jsonError(
      response.status,
      safeMessage(payload, response.status),
      sanitizeProblemFields(payload)
    );
  }
  if (response.status === 204) {
    return new NextResponse(null, { status: 204 });
  }
  return NextResponse.json(payload ?? {}, { status: response.status });
}

async function readJson(response: Response) {
  return (await readJsonResult(response)).data;
}

async function readJsonResult(response: Response) {
  const text = await response.text();
  if (!text) {
    return { data: null, invalidJson: false };
  }
  try {
    return { data: JSON.parse(text) as unknown, invalidJson: false };
  } catch {
    return { data: null, invalidJson: true };
  }
}

function safeMessage(payload: unknown, status: number) {
  if (payload && typeof payload === "object") {
    const maybe = payload as { detail?: unknown; title?: unknown; message?: unknown };
    if (typeof maybe.detail === "string") {
      return maybe.detail;
    }
    if (typeof maybe.message === "string") {
      return maybe.message;
    }
    if (typeof maybe.title === "string") {
      return maybe.title;
    }
  }
  if (status === 401) {
    return "Authentication failed";
  }
  if (status === 403) {
    return "You are not allowed to perform this action";
  }
  return "The request could not be completed";
}

export function sanitizeProblemFields(payload: unknown): SafeProblemFields {
  if (!payload || typeof payload !== "object") return {};
  const problem = payload as Record<string, unknown>;
  const fields: SafeProblemFields = {};

  if (typeof problem.code === "string" && /^[a-z0-9_]{1,64}$/.test(problem.code)) {
    fields.code = problem.code;
  }
  if (
    typeof problem.correlationId === "string" &&
    problem.correlationId.length > 0 &&
    problem.correlationId.length <= 128
  ) {
    fields.correlationId = problem.correlationId;
  }
  const fieldErrors = safeFieldErrors(problem.fieldErrors);
  if (fieldErrors) fields.fieldErrors = fieldErrors;
  return fields;
}

function safeFieldErrors(value: unknown): Record<string, string[]> | undefined {
  if (!value || typeof value !== "object" || Array.isArray(value)) return undefined;
  const result: Record<string, string[]> = {};
  for (const [field, messages] of Object.entries(value).slice(0, 50)) {
    if (!/^[A-Za-z0-9_.\[\]-]{1,100}$/.test(field) || !Array.isArray(messages)) continue;
    const safeMessages = messages
      .filter((message): message is string => typeof message === "string")
      .slice(0, 20)
      .map((message) => message.slice(0, 500));
    if (safeMessages.length > 0) result[field] = safeMessages;
  }
  return Object.keys(result).length > 0 ? result : undefined;
}

function isMutating(method: string) {
  return ["POST", "PUT", "PATCH", "DELETE"].includes(method.toUpperCase());
}

export async function validateCsrf(request: NextRequest) {
  const cookieStore = await cookies();
  const cookieToken = cookieStore.get(CSRF_COOKIE)?.value;
  const headerToken = request.headers.get("x-csrf-token");
  if (!cookieToken || !headerToken || cookieToken !== headerToken) {
    return jsonError(403, "Invalid CSRF token");
  }
  return null;
}
