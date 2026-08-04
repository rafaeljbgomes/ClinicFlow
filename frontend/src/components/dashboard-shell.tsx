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
  PlusIcon,
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

      <div className="min-h-screen md:pl-[17rem]">
        <main className="mx-auto flex w-full max-w-[1380px] flex-col gap-8 px-4 py-6 sm:px-6 md:px-8 md:py-8">
          <WorkspaceHeader isAdmin={isAdmin} role={user?.role} />
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
    <aside className="fixed left-0 top-0 hidden h-screen w-[17rem] p-4 md:block">
      <div className="glass-panel flex h-full flex-col rounded-[30px] p-4">
        <BrandBlock role={user?.role} />

        <div className="mt-8">
          {isLoading ? <Skeleton className="h-12 rounded-full" /> : <UserPill user={user} />}
        </div>

        <nav className="mt-8 flex flex-1 flex-col gap-1">
          {navItems.map((item) => (
            <NavLink key={item.href} item={item} pathname={pathname} />
          ))}
        </nav>

        <div className="mt-6 flex flex-col gap-3">
          {user?.role === "PSYCHOLOGIST" ? (
            <Button render={<Link href="/dashboard/appointments" />} nativeButton={false}>
              <PlusIcon data-icon="inline-start" />
              Schedule
            </Button>
          ) : null}
          <Button variant="ghost" onClick={onLogout}>
            <LogOutIcon data-icon="inline-start" />
            Sign out
          </Button>
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
    <header className="sticky top-0 flex h-16 items-center justify-between border-b bg-background/80 px-4 backdrop-blur-xl md:hidden">
      <Sheet>
        <SheetTrigger render={<Button variant="ghost" size="icon" />}>
          <MenuIcon />
          <span className="sr-only">Open navigation</span>
        </SheetTrigger>
        <SheetContent side="left" className="w-72 p-4">
          <SheetHeader className="text-left">
            <SheetTitle>ClinicFlow</SheetTitle>
            <SheetDescription>{user?.role === "ADMIN" ? "System console" : "Practice workspace"}</SheetDescription>
          </SheetHeader>
          <nav className="mt-6 flex flex-col gap-1">
            {navItems.map((item) => (
              <NavLink key={item.href} item={item} pathname={pathname} />
            ))}
          </nav>
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

function WorkspaceHeader({ isAdmin, role }: { isAdmin: boolean; role?: Role }) {
  return (
    <div className="hidden items-center justify-between rounded-full bg-background/60 px-3 py-3 backdrop-blur-xl md:flex">
      <div className="flex items-center gap-3 px-2">
        <span className="size-2 rounded-full bg-clinical-blue" />
        <span className="text-sm text-muted-foreground">
          {isAdmin ? "Admin console" : "Practice workspace"}
        </span>
      </div>
      <div className="flex items-center gap-2">
        <div className="rounded-full bg-secondary px-3 py-1 text-xs font-medium text-secondary-foreground">
          {role ?? "Loading"}
        </div>
        <ThemeToggle />
      </div>
    </div>
  );
}

function BrandBlock({ role }: { role?: Role }) {
  return (
    <Link href={homeForRole(role)} className="flex items-center gap-3 rounded-[22px] px-2 py-1">
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

function UserPill({ user }: { user?: UserView }) {
  return (
    <div className="flex items-center gap-3 rounded-full bg-background/55 p-2">
      <AvatarInitials name={user?.fullName} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{user?.fullName ?? "Account"}</p>
        <p className="truncate text-xs text-muted-foreground">{user?.email ?? user?.role}</p>
      </div>
    </div>
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
      <DropdownMenuTrigger render={<Button variant="outline" size={compact ? "icon-sm" : "sm"} />}>
        <AvatarInitials name={user?.fullName} compact={compact} />
        {!compact ? user?.fullName?.split(" ")[0] ?? "Account" : null}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuGroup>
          <DropdownMenuLabel>Signed in</DropdownMenuLabel>
          <DropdownMenuItem>{user?.email ?? "Not available"}</DropdownMenuItem>
          <DropdownMenuItem>{user?.role ?? "Role unavailable"}</DropdownMenuItem>
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
        "flex h-11 items-center gap-3 rounded-full px-4 text-sm font-medium text-muted-foreground transition-all hover:bg-background/60 hover:text-foreground",
        active && "bg-background text-foreground shadow-sm"
      )}
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
