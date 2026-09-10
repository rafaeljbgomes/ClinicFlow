import * as React from "react"

import { cn } from "@/lib/utils"

function Card({
  className,
  size = "default",
  tone = "default",
  ...props
}: React.ComponentProps<"div"> & {
  size?: "default" | "sm";
  tone?: "default" | "quiet" | "inset";
}) {
  return (
    <div
      data-slot="card"
      data-size={size}
      data-tone={tone}
      className={cn(
        "group/card flex flex-col gap-6 overflow-hidden rounded-[20px] border border-border/70 bg-card py-6 text-sm text-card-foreground shadow-[0_10px_28px_rgb(23_27_25/0.035)] has-data-[slot=card-footer]:pb-0 has-[>img:first-child]:pt-0 data-[size=sm]:gap-4 data-[size=sm]:py-5 data-[size=sm]:has-data-[slot=card-footer]:pb-0 data-[tone=quiet]:border-transparent data-[tone=quiet]:bg-card/80 data-[tone=quiet]:shadow-none data-[tone=inset]:border-border/60 data-[tone=inset]:bg-surface-subtle data-[tone=inset]:shadow-none *:[img:first-child]:rounded-t-[20px] *:[img:last-child]:rounded-b-[20px]",
        className
      )}
      {...props}
    />
  )
}

function CardHeader({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="card-header"
      className={cn(
        "group/card-header @container/card-header grid auto-rows-min items-start gap-1.5 rounded-t-[20px] px-6 group-data-[size=sm]/card:px-5 has-data-[slot=card-action]:grid-cols-[1fr_auto] has-data-[slot=card-description]:grid-rows-[auto_auto] [.border-b]:pb-6 group-data-[size=sm]/card:[.border-b]:pb-4",
        className
      )}
      {...props}
    />
  )
}

function CardTitle({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="card-title"
      className={cn(
        "font-sans text-lg font-semibold tracking-tight text-foreground group-data-[size=sm]/card:text-base",
        className
      )}
      {...props}
    />
  )
}

function CardDescription({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="card-description"
      className={cn("text-sm text-muted-foreground font-sans", className)}
      {...props}
    />
  )
}

function CardAction({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="card-action"
      className={cn(
        "col-start-2 row-span-2 row-start-1 self-start justify-self-end",
        className
      )}
      {...props}
    />
  )
}

function CardContent({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="card-content"
      className={cn("px-6 group-has-data-[slot=card-footer]/card:pb-6 group-data-[size=sm]/card:px-5 group-data-[size=sm]/card:group-has-data-[slot=card-footer]/card:pb-5", className)}
      {...props}
    />
  )
}

function CardFooter({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="card-footer"
      className={cn(
        "flex items-center rounded-b-[20px] border-t bg-muted/45 p-6 group-data-[size=sm]/card:p-5",
        className
      )}
      {...props}
    />
  )
}

export {
  Card,
  CardHeader,
  CardFooter,
  CardTitle,
  CardAction,
  CardDescription,
  CardContent,
}
