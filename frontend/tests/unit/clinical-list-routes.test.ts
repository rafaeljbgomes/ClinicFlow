import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { cookiesMock, verifyAccessTokenMock } = vi.hoisted(() => ({
  cookiesMock: vi.fn(),
  verifyAccessTokenMock: vi.fn(),
}));

vi.mock("next/headers", () => ({ cookies: cookiesMock }));
vi.mock("@/lib/server/jwt", () => ({ verifyAccessToken: verifyAccessTokenMock }));

import { GET as listCases } from "@/app/api/clinical-cases/route";
import { GET as listSessionRecords } from "@/app/api/clinical-cases/[id]/session-records/route";
import { GET as listPatients } from "@/app/api/patients/route";

const caseId = "6f1c1a3e-56d4-4f0e-9d37-4d1f3a2b9c10";
const patientId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
const emptyPage = { content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } };

describe("clinical list routes", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    cookiesMock.mockResolvedValue({
      get: (name: string) => (name === "clinicflow_access_token" ? { value: "signed-access-token" } : undefined),
    });
    verifyAccessTokenMock.mockResolvedValue({
      status: "verified",
      principal: {
        userId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        email: "psychologist@example.com",
        role: "PSYCHOLOGIST",
        expiresAt: 2_000_000_000,
      },
    });
    fetchMock.mockResolvedValue(new Response(JSON.stringify(emptyPage), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it("forwards the case filters and paging parameters", async () => {
    // Arrange
    const request = new NextRequest(
      `http://localhost/api/clinical-cases?patientId=${patientId}&status=INTAKE&status=ACTIVE&page=1&size=5`
    );

    // Act
    await listCases(request);

    // Assert
    expect(fetchMock.mock.calls[0][0]).toBe(
      `http://localhost:8085/clinical-cases?patientId=${patientId}&status=INTAKE&status=ACTIVE&page=1&size=5`
    );
  });

  it("forwards the patient search, filters, lookup ids and paging parameters", async () => {
    // Arrange
    const request = new NextRequest(
      `http://localhost/api/patients?q=ana&status=ACTIVE&consent=PENDING&consent=REVOKED` +
      `&createdFrom=2026-11-01T00%3A00%3A00.000Z&id=${patientId}&page=1&size=5`
    );

    // Act
    await listPatients(request);

    // Assert
    expect(fetchMock.mock.calls[0][0]).toBe(
      `http://localhost:8082/patients?q=ana&status=ACTIVE&consent=PENDING&consent=REVOKED` +
      `&createdFrom=2026-11-01T00%3A00%3A00.000Z&id=${patientId}&page=1&size=5`
    );
  });

  it("rejects a patient list request with a parameter the route does not document", async () => {
    // Arrange
    const request = new NextRequest("http://localhost/api/patients?q=ana&psychologistId=y");

    // Act
    const response = await listPatients(request);

    // Assert
    expect(response.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("forwards the paging parameters for a case's session records", async () => {
    // Arrange
    const request = new NextRequest(
      `http://localhost/api/clinical-cases/${caseId}/session-records?page=2&size=10`
    );

    // Act
    await listSessionRecords(request, { params: Promise.resolve({ id: caseId }) });

    // Assert
    expect(fetchMock.mock.calls[0][0]).toBe(
      `http://localhost:8085/clinical-cases/${caseId}/session-records?page=2&size=10`
    );
  });
  it("rejects a case list request with a parameter the route does not document", async () => {
    // Arrange
    const request = new NextRequest(`http://localhost/api/clinical-cases?status=INTAKE&psychologistId=x`);

    // Act
    const response = await listCases(request);

    // Assert
    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({ code: "unsupported_parameter" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects a session-record request with a filter the route does not take", async () => {
    // Arrange
    const request = new NextRequest(`http://localhost/api/clinical-cases/${caseId}/session-records?status=SIGNED`);

    // Act
    const response = await listSessionRecords(request, { params: Promise.resolve({ id: caseId }) });

    // Assert
    expect(response.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
