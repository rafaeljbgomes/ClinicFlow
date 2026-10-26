"use client";

import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { Page } from "@/lib/types";

// Previous/next controls for a paged list (ADR-029); hidden when everything fits on one page.
export function ListPager({
  page,
  noun,
  onPageChange,
  disabled = false,
}: {
  page: Page<unknown>["page"] | undefined;
  noun: string;
  onPageChange: (number: number) => void;
  disabled?: boolean;
}) {
  if (!page || page.totalPages <= 1) return null;
  const lastPage = page.totalPages - 1;
  return (
    <nav aria-label={`${noun} pages`} className="flex items-center justify-between gap-3 pt-4">
      <p className="text-xs text-muted-foreground" aria-live="polite">
        Page {page.number + 1} of {page.totalPages} · {page.totalElements} {noun}
      </p>
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page.number - 1)}
          disabled={disabled || page.number <= 0}
        >
          <ChevronLeftIcon data-icon="inline-start" />
          Previous
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(Math.min(page.number + 1, lastPage))}
          disabled={disabled || page.number >= lastPage}
        >
          Next
          <ChevronRightIcon data-icon="inline-end" />
        </Button>
      </div>
    </nav>
  );
}
