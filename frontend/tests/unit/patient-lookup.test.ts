import { describe, expect, it } from "vitest";
import { PATIENT_PICKER_SIZE, patientPickerPath } from "@/components/patient-picker";
import { PATIENT_COUNT_PATH, PATIENT_LOOKUP_LIMIT, patientLookupPaths } from "@/lib/patient-lookup";

function idsIn(path: string) {
  return new URL(path, "http://ui").searchParams.getAll("id");
}

describe("patient lookup", () => {
  it("asks for nothing when no ids are on screen", () => {
    // Act
    const paths = patientLookupPaths([null, undefined, ""]);

    // Assert
    expect(paths).toEqual([]);
  });

  it("looks up each distinct id once, in a stable order, sized to the ids", () => {
    // Act
    const paths = patientLookupPaths(["b", "a", "b", null, "c"]);

    // Assert
    expect(paths).toEqual(["/api/patients?id=a&id=b&id=c&size=3"]);
  });

  it("gives the same request for the same ids in any order", () => {
    // Act
    const first = patientLookupPaths(["c", "a", "b"]);
    const second = patientLookupPaths(["b", "c", "a"]);

    // Assert
    expect(first).toEqual(second);
  });

  it("splits more ids than one page into several requests", () => {
    // Arrange
    const ids = Array.from({ length: PATIENT_LOOKUP_LIMIT + 1 }, (_, index) => `id-${String(index).padStart(3, "0")}`);

    // Act
    const paths = patientLookupPaths(ids);

    // Assert
    expect(paths.map((path) => idsIn(path).length)).toEqual([PATIENT_LOOKUP_LIMIT, 1]);
    expect(paths.flatMap(idsIn)).toEqual(ids);
  });

  it("counts patients with a one-row page", () => {
    // Assert
    expect(PATIENT_COUNT_PATH).toBe("/api/patients?size=1");
  });
});

describe("patient picker", () => {
  it("offers the first page of patients when nothing is typed", () => {
    // Act
    const path = patientPickerPath("   ");

    // Assert
    expect(path).toBe(`/api/patients?size=${PATIENT_PICKER_SIZE}`);
  });

  it("searches the trimmed text on the server", () => {
    // Act
    const path = patientPickerPath("  Ana Martins ");

    // Assert
    expect(path).toBe(`/api/patients?q=Ana+Martins&size=${PATIENT_PICKER_SIZE}`);
  });
});
