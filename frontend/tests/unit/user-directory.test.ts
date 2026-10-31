import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { INTERNAL_ISSUER, stubOidcEnvironment, USER_ID } from "./realm";

const LOOKUP_SECRET = "user-lookup-secret-for-unit-tests";
const USERS_ENDPOINT = "http://keycloak:8080/admin/realms/clinicflow/users";
const NOW = 1_800_000_000_000;
const fetchMock = vi.fn();

type LookUpUser = typeof import("@/lib/server/user-directory").lookUpUser;
let lookUpUser: LookUpUser;

beforeEach(async () => {
  stubOidcEnvironment();
  vi.stubEnv("CLINICFLOW_USER_LOOKUP_CLIENT_SECRET", LOOKUP_SECRET);
  vi.stubGlobal("fetch", fetchMock);
  // The service-account token is cached per module; every test starts without one.
  vi.resetModules();
  ({ lookUpUser } = await import("@/lib/server/user-directory"));
});

afterEach(() => {
  fetchMock.mockReset();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("lookUpUser", () => {
  it("asks the private token endpoint for a client-credentials token as the lookup client", async () => {
    // Arrange
    keycloakAnswers();

    // Act
    await lookUpUser(USER_ID, NOW);

    // Assert
    const [url, init] = fetchMock.mock.calls[0];
    const basic = Buffer.from(`clinicflow-user-lookup:${LOOKUP_SECRET}`).toString("base64");
    expect([url, init.method, init.headers.Authorization, String(init.body)]).toEqual([
      `${INTERNAL_ISSUER}/protocol/openid-connect/token`,
      "POST",
      `Basic ${basic}`,
      "grant_type=client_credentials",
    ]);
  });

  it("reads the account and its effective realm roles from the private Admin API", async () => {
    // Arrange
    keycloakAnswers();

    // Act
    await lookUpUser(USER_ID, NOW);

    // Assert
    const reads = fetchMock.mock.calls.slice(1).map(([url, init]) => [url, init.headers.Authorization]);
    expect(reads).toEqual([
      [`${USERS_ENDPOINT}/${USER_ID}`, "Bearer service-account-token"],
      [`${USERS_ENDPOINT}/${USER_ID}/role-mappings/realm/composite`, "Bearer service-account-token"],
    ]);
  });

  it("maps the account to a directory user", async () => {
    // Arrange
    keycloakAnswers();

    // Act
    const result = await lookUpUser(USER_ID, NOW);

    // Assert
    expect(result).toEqual({
      status: "found",
      user: { id: USER_ID, email: "sofia.almeida@demo.clinicflow.local", fullName: "Sofia Almeida", role: "PSYCHOLOGIST", enabled: true },
    });
  });

  it("counts a role held through the default-role composite", async () => {
    // Arrange
    keycloakAnswers({ roles: [{ name: "default-roles-clinicflow" }, { name: "PATIENT" }, { name: "offline_access" }] });

    // Act
    const result = await lookUpUser(USER_ID, NOW);

    // Assert
    expect(result.status === "found" && result.user.role).toBe("PATIENT");
  });

  it.each([
    ["no ClinicFlow role", [{ name: "offline_access" }]],
    ["two ClinicFlow roles", [{ name: "PSYCHOLOGIST" }, { name: "PATIENT" }]],
  ])("reports no role for an account with %s", async (_case, roles) => {
    // Arrange
    keycloakAnswers({ roles });

    // Act
    const result = await lookUpUser(USER_ID, NOW);

    // Assert
    expect(result.status === "found" && result.user.role).toBeNull();
  });

  it("names an account without first or last name by its username", async () => {
    // Arrange
    keycloakAnswers({ account: { firstName: undefined, lastName: " " } });

    // Act
    const result = await lookUpUser(USER_ID, NOW);

    // Assert
    expect(result.status === "found" && result.user.fullName).toBe("sofia.almeida@demo.clinicflow.local");
  });

  it("reuses the service-account token until shortly before it expires", async () => {
    // Arrange
    keycloakAnswers();
    await lookUpUser(USER_ID, NOW);
    keycloakAnswers({ withToken: false });

    // Act
    await lookUpUser(USER_ID, NOW + 269_000);

    // Assert
    expect(tokenRequests()).toBe(1);
  });

  it("renews the service-account token within 30 seconds of its expiry", async () => {
    // Arrange
    keycloakAnswers();
    await lookUpUser(USER_ID, NOW);
    keycloakAnswers();

    // Act
    await lookUpUser(USER_ID, NOW + 271_000);

    // Assert
    expect(tokenRequests()).toBe(2);
  });

  it("retries once with a new token when Keycloak refuses the cached one", async () => {
    // Arrange
    keycloakAnswers();
    await lookUpUser(USER_ID, NOW);
    fetchMock
      .mockResolvedValueOnce(json({}, 401))
      .mockResolvedValueOnce(json({}, 401));
    keycloakAnswers();

    // Act
    const result = await lookUpUser(USER_ID, NOW + 1_000);

    // Assert
    expect([result.status, tokenRequests()]).toEqual(["found", 2]);
  });

  it("reports an unknown id as not found", async () => {
    // Arrange
    fetchMock
      .mockResolvedValueOnce(json({ access_token: "service-account-token", expires_in: 300 }))
      .mockResolvedValueOnce(json({ error: "User not found" }, 404))
      .mockResolvedValueOnce(json({ error: "User not found" }, 404));

    // Act
    const result = await lookUpUser(USER_ID, NOW);

    // Assert
    expect(result).toEqual({ status: "not-found" });
  });

  it("is unavailable without a client secret, before calling Keycloak", async () => {
    // Arrange
    vi.stubEnv("CLINICFLOW_USER_LOOKUP_CLIENT_SECRET", "");

    // Act
    const result = await lookUpUser(USER_ID, NOW);

    // Assert
    expect([result.status, fetchMock.mock.calls.length]).toEqual(["unavailable", 0]);
  });

  it("is unavailable for an issuer that names no realm, before calling Keycloak", async () => {
    // Arrange
    vi.stubEnv("CLINICFLOW_OIDC_INTERNAL_ISSUER", "http://keycloak:8080/");

    // Act
    const result = await lookUpUser(USER_ID, NOW);

    // Assert
    expect([result.status, fetchMock.mock.calls.length]).toEqual(["unavailable", 0]);
  });

  it("is unavailable when the token endpoint refuses the client", async () => {
    // Arrange
    fetchMock.mockResolvedValueOnce(json({ error: "unauthorized_client" }, 401));

    // Act
    const result = await lookUpUser(USER_ID, NOW);

    // Assert
    expect(result).toEqual({ status: "unavailable" });
  });

  it("is unavailable when Keycloak cannot be reached", async () => {
    // Arrange
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));

    // Act
    const result = await lookUpUser(USER_ID, NOW);

    // Assert
    expect(result).toEqual({ status: "unavailable" });
  });

  it("is unavailable when the Admin API forbids the read", async () => {
    // Arrange
    fetchMock
      .mockResolvedValueOnce(json({ access_token: "service-account-token", expires_in: 300 }))
      .mockResolvedValueOnce(json({ error: "unknown_error" }, 403))
      .mockResolvedValueOnce(json({ error: "unknown_error" }, 403));

    // Act
    const result = await lookUpUser(USER_ID, NOW);

    // Assert
    expect(result).toEqual({ status: "unavailable" });
  });

  it("is unavailable for an account without the fields it maps", async () => {
    // Arrange
    keycloakAnswers({ account: { enabled: "yes" } });

    // Act
    const result = await lookUpUser(USER_ID, NOW);

    // Assert
    expect(result).toEqual({ status: "unavailable" });
  });
});

type Answers = { account?: Record<string, unknown>; roles?: unknown[]; withToken?: boolean };

function keycloakAnswers({ account = {}, roles = [{ name: "PSYCHOLOGIST" }], withToken = true }: Answers = {}) {
  if (withToken) fetchMock.mockResolvedValueOnce(json({ access_token: "service-account-token", expires_in: 300 }));
  fetchMock
    .mockResolvedValueOnce(json({
      id: USER_ID,
      username: "sofia.almeida@demo.clinicflow.local",
      email: "sofia.almeida@demo.clinicflow.local",
      firstName: "Sofia",
      lastName: "Almeida",
      enabled: true,
      ...account,
    }))
    .mockResolvedValueOnce(json(roles));
}

function tokenRequests() {
  return fetchMock.mock.calls.filter(([url]) => String(url).endsWith("/protocol/openid-connect/token")).length;
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
