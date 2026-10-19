import { FEEDBACK_ASSIGNEES } from "@/entities/feedback";
import type { AssigneeFilter as AssigneeFilterValue } from "../model/useTriageFilter";
import { Chip, FilterRow } from "./VersionFilter.styles";
import { FilterLabel } from "./AdminFeedbacksPage.styles";

const OPTIONS: readonly { value: AssigneeFilterValue; label: string }[] = [
  { value: "ALL", label: "전체" },
  ...FEEDBACK_ASSIGNEES,
  { value: null, label: "없음" },
];

interface AssigneeFilterProps {
  selected: AssigneeFilterValue;
  onSelect: (value: AssigneeFilterValue) => void;
}

export const AssigneeFilter = ({ selected, onSelect }: AssigneeFilterProps) => (
  <FilterRow role="group" aria-labelledby="assignee-filter-label">
    <FilterLabel id="assignee-filter-label">담당</FilterLabel>
    {OPTIONS.map(({ value, label }) => {
      const active = value === selected;

      return (
        <Chip
          key={value ?? "none"}
          type="button"
          $selected={active}
          aria-pressed={active}
          onClick={() => onSelect(value)}
        >
          {label}
        </Chip>
      );
    })}
  </FilterRow>
);
