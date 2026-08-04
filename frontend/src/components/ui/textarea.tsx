import * as React from "react";
import { cn } from "@/lib/utils";

function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        "min-h-28 w-full min-w-0 resize-y rounded-[22px] border-none bg-background/70 px-4 py-3 text-base transition-all outline-none placeholder:text-muted-foreground focus-visible:bg-background focus-visible:ring-2 focus-visible:ring-ring/45 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:ring-2 aria-invalid:ring-destructive/20 md:text-sm",
        className
      )}
      {...props}
    />
  );
}

export { Textarea };
