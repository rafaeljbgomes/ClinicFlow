import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

export function ErrorAlert({ message }: { message: string }) {
  return (
    <Alert variant="destructive">
      <AlertTitle>Unable to load data</AlertTitle>
      <AlertDescription>{message}</AlertDescription>
    </Alert>
  );
}
