type ListParameter = string | number | readonly string[] | undefined;

// Builds the URL of a paged list request; undefined values are skipped and arrays repeat the parameter.
export function listPath(path: string, parameters: Record<string, ListParameter>): string {
  const query = new URLSearchParams();
  for (const [name, value] of Object.entries(parameters)) {
    if (value === undefined) continue;
    for (const item of Array.isArray(value) ? value : [value]) {
      query.append(name, String(item));
    }
  }
  const search = query.toString();
  return search ? `${path}?${search}` : path;
}
