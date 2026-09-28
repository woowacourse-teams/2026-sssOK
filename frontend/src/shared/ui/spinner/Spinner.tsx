import { useDelayedVisibility } from "@/shared/hooks";
import { FullPage, HiddenLabel, Ring, Status } from "./Spinner.styles";

export const SPINNER_DELAY_MS = 250;

export interface SpinnerProps {
  label: string;
  size?: "md" | "lg";
  tone?: "default" | "inverse";
  delayMs?: number;
}

export const Spinner = ({
  label,
  size = "md",
  tone = "default",
  delayMs = SPINNER_DELAY_MS,
}: SpinnerProps) => {
  const isVisible = useDelayedVisibility(delayMs);

  return (
    <Status role="status">
      {isVisible && <Ring size={size} tone={tone} aria-hidden />}
      <HiddenLabel>{label}</HiddenLabel>
    </Status>
  );
};

export const PageSpinner = (props: SpinnerProps) => (
  <FullPage>
    <Spinner size="lg" {...props} />
  </FullPage>
);
