import { FEEDBACK_STATUSES } from "@/entities/feedback";
import type { StatusTab } from "../model/useTriageFilter";
import { Count, Tab, TabRow } from "./StatusTabs.styles";

const TABS: readonly { value: StatusTab; label: string }[] = [
  { value: "ALL", label: "전체" },
  ...FEEDBACK_STATUSES,
];

interface StatusTabsProps {
  selected: StatusTab;
  counts: Record<StatusTab, number>;
  onSelect: (tab: StatusTab) => void;
}

/** 탭마다 바뀌는 패널이 없는 거름 조건이라 `tablist` 대신 눌림 버튼으로 둔다. */
export const StatusTabs = ({ selected, counts, onSelect }: StatusTabsProps) => (
  <TabRow role="group" aria-label="처리 상태">
    {TABS.map(({ value, label }) => {
      const active = value === selected;

      return (
        <Tab
          key={value}
          type="button"
          $active={active}
          aria-pressed={active}
          onClick={() => onSelect(value)}
        >
          {label}
          <Count $active={active} aria-label={`${counts[value]}건`}>
            {counts[value]}
          </Count>
        </Tab>
      );
    })}
  </TabRow>
);
