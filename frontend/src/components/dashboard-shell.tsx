"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ComponentType } from "react";
import {
  BellIcon,
  CalendarClockIcon,
  ClipboardListIcon,
  HeartPulseIcon,
  LayoutDashboardIcon,
  LogOutIcon,
  MenuIcon,
  ServerIcon,
  SettingsIcon,
  UsersIcon,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { ThemeToggle } from "@/components/theme-toggle";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { apiFetch } from "@/lib/api-client";
import type { Role, UserView } from "@/lib/types";
import { cn } from "@/lib/utils";

type NavItem = {
  href: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
};

const psychologistNav: NavItem[] = [
  { href: "/dashboard", label: "Today", icon: LayoutDashboardIcon },
  { href: "/dashboard/patients", label: "Patients", icon: UsersIcon },
  { href: "/dashboard/appointments", label: "Calendar", icon: CalendarClockIcon },
  { href: "/dashboard/clinical", label: "Clinical", icon: ClipboardListIcon },
  { href: "/dashboard/notifications", label: "Messages", icon: BellIcon },
  { href: "/dashboard/settings", label: "Settings", icon: SettingsIcon },
];

const adminNav: NavItem[] = [
  { href: "/dashboard/system", label: "System", icon: ServerIcon },
  { href: "/dashboard/notifications", label: "Event history", icon: BellIcon },
  { href: "/dashboard/settings", label: "Settings", icon: SettingsIcon },
];

const patientNav: NavItem[] = [
  { href: "/dashboard/access-limited", label: "Account", icon: HeartPulseIcon },
  { href: "/dashboard/settings", label: "Settings", icon: SettingsIcon },
];

export function DashboardShell({ children, user }: { children: React.ReactNode; user: UserView }) {
  const router = useRouter();
  const pathname = usePathname();
  const isAdmin = user?.role === "ADMIN";
  const navItems = isAdmin ? adminNav : user.role === "PATIENT" ? patientNav : psychologistNav;

  async function logout() {
    await apiFetch("/api/auth/logout", { method: "POST" });
    toast.success("Signed out");
    router.replace("/login");
    router.refresh();
  }

  return (
    <div className="min-h-screen bg-surface-container-low">
      <DesktopSidebar
        isLoading={false}
        navItems={navItems}
        pathname={pathname}
        user={user}
        onLogout={logout}
      />

      <MobileHeader
        isLoading={false}
        navItems={navItems}
        pathname={pathname}
        user={user}
        onLogout={logout}
      />

      <div className="min-h-screen lg:pl-72">
        <main className="mx-auto flex w-full max-w-[1440px] flex-col gap-8 px-5 py-6 sm:px-8 lg:px-10 lg:py-8">
          <WorkspaceHeader isAdmin={isAdmin} />
          {children}
        </main>
      </div>
    </div>
  );
}

function DesktopSidebar({
  isLoading,
  navItems,
  pathname,
  user,
  onLogout,
}: {
  isLoading: boolean;
  navItems: NavItem[];
  pathname: string;
  user?: UserView;
  onLogout: () => Promise<void>;
}) {
  return (
    <aside className="fixed left-0 top-0 hidden h-screen w-72 lg:block">
      <div className="flex h-full flex-col border-r border-border bg-card p-5 shadow-[8px_0_32px_rgb(23_27_25/0.03)]">
        <BrandBlock role={user?.role} />

        <nav className="mt-10 flex flex-1 flex-col gap-1.5">
          {navItems.map((item) => (
            <NavLink key={item.href} item={item} pathname={pathname} />
          ))}
        </nav>

        <div className="mt-6 border-t border-border pt-5">
          {isLoading ? <Skeleton className="h-14 rounded-xl" /> : <AccountMenu user={user} onLogout={onLogout} />}
        </div>
      </div>
    </aside>
  );
}

function MobileHeader({
  isLoading,
  navItems,
  pathname,
  user,
  onLogout,
}: {
  isLoading: boolean;
  navItems: NavItem[];
  pathname: string;
  user?: UserView;
  onLogout: () => Promise<void>;
}) {
  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-border bg-card/95 px-4 backdrop-blur-xl lg:hidden">
      <Sheet>
        <SheetTrigger render={<Button variant="ghost" size="icon" />}>
          <MenuIcon />
          <span className="sr-only">Open navigation</span>
        </SheetTrigger>
        <SheetContent side="left" className="flex w-72 p-5">
          <SheetHeader className="text-left">
            <SheetTitle>ClinicFlow</SheetTitle>
            <SheetDescription>{user?.role === "ADMIN" ? "System console" : "Practice workspace"}</SheetDescription>
          </SheetHeader>
          <nav className="mt-8 flex flex-1 flex-col gap-1.5">
            {navItems.map((item) => (
              <NavLink key={item.href} item={item} pathname={pathname} />
            ))}
          </nav>
          <div className="border-t border-border pt-5">
            {isLoading ? <Skeleton className="h-14 rounded-xl" /> : <AccountMenu user={user} onLogout={onLogout} />}
          </div>
        </SheetContent>
      </Sheet>

      <Link href={homeForRole(user?.role)} className="flex items-center gap-2 text-sm font-semibold">
        <span className="flex size-8 items-center justify-center rounded-full bg-primary text-primary-foreground">
          <HeartPulseIcon className="size-4" />
        </span>
        ClinicFlow
      </Link>

      {isLoading ? (
        <Skeleton className="size-9 rounded-full" />
      ) : (
        <AccountMenu user={user} onLogout={onLogout} compact />
      )}
    </header>
  );
}

function WorkspaceHeader({ isAdmin }: { isAdmin: boolean }) {
  return (
    <div className="hidden items-center justify-between border-b border-border pb-4 lg:flex">
      <div className="flex items-center gap-2.5">
        <span className="size-2 rounded-full bg-clinical-blue" />
        <span className="text-sm font-medium text-muted-foreground">
          {isAdmin ? "Admin console" : "Practice workspace"}
        </span>
      </div>
      <ThemeToggle />
    </div>
  );
}

function BrandBlock({ role }: { role?: Role }) {
  return (
    <Link href={homeForRole(role)} className="flex items-center gap-3 rounded-xl px-1 py-1">
      <span className="flex size-11 items-center justify-center rounded-full bg-primary text-primary-foreground">
        <HeartPulseIcon className="size-5" />
      </span>
      <span className="min-w-0">
        <span className="block text-base font-semibold tracking-tight">ClinicFlow</span>
        <span className="block text-xs text-muted-foreground">
          {role === "ADMIN" ? "System console" : "Practice workspace"}
        </span>
      </span>
    </Link>
  );
}

function AccountMenu({
  compact = false,
  user,
  onLogout,
}: {
  compact?: boolean;
  user?: UserView;
  onLogout: () => Promise<void>;
}) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger render={<Button variant={compact ? "outline" : "ghost"} size={compact ? "icon-sm" : "default"} className={compact ? undefined : "h-auto w-full justify-start px-2 py-2"} />}>
        <AvatarInitials name={user?.fullName} compact={compact} />
        {!compact ? <span className="min-w-0 text-left"><span className="block truncate text-sm font-semibold">{user?.fullName ?? "Account"}</span><span className="block truncate text-xs font-normal text-muted-foreground">{user?.email ?? "Account settings"}</span></span> : null}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuGroup>
          <DropdownMenuLabel>Account</DropdownMenuLabel>
          <DropdownMenuItem render={<Link href="/dashboard/settings" />}>Settings</DropdownMenuItem>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <DropdownMenuItem onClick={onLogout}>
            <LogOutIcon />
            Sign out
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function AvatarInitials({ compact = false, name }: { compact?: boolean; name?: string }) {
  return (
    <span
      className={cn(
        "flex shrink-0 items-center justify-center rounded-full bg-secondary text-xs font-semibold text-secondary-foreground",
        compact ? "size-7" : "size-9"
      )}
    >
      {initials(name)}
    </span>
  );
}

function NavLink({ item, pathname }: { item: NavItem; pathname: string }) {
  const active = pathname === item.href;
  const Icon = item.icon;

  return (
    <Link
      href={item.href}
      className={cn(
        "flex h-11 items-center gap-3 rounded-xl px-3.5 text-sm font-medium text-muted-foreground transition-all duration-150 hover:bg-secondary/65 hover:text-primary focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/25",
        active && "bg-primary text-primary-foreground shadow-[0_5px_14px_rgb(23_75_58/18%)] hover:bg-primary hover:text-primary-foreground"
      )}
      aria-current={active ? "page" : undefined}
    >
      <Icon className="size-4" />
      {item.label}
    </Link>
  );
}

function initials(name?: string) {
  if (!name) return "CF";
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

function homeForRole(role?: Role) {
  if (role === "ADMIN") return "/dashboard/system";
  if (role === "PATIENT") return "/dashboard/access-limited";
  return "/dashboard";
}
