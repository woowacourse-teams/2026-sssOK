import { Chip, Chips } from "./RoomListPage.styles";

export type RoomFilter = "all" | "host" | "member";

const FILTERS: { value: RoomFilter; label: string }[] = [
  { value: "all", label: "전체" },
  { value: "host", label: "내가 만든 방" },
  { value: "member", label: "참여한 방" },
];

interface RoomListFiltersProps {
  counts: Record<RoomFilter, number>;
  selectedFilter: RoomFilter;
  onFilterChange: (filter: RoomFilter) => void;
}

export const RoomListFilters = ({
  counts,
  selectedFilter,
  onFilterChange,
}: RoomListFiltersProps) => {
  return (
    <Chips role="group" aria-label="방 종류">
      {FILTERS.map(({ value, label }) => (
        <Chip
          key={value}
          type="button"
          $selected={selectedFilter === value}
          aria-pressed={selectedFilter === value}
          onClick={() => onFilterChange(value)}
        >
          {label} <b>{counts[value]}</b>
        </Chip>
      ))}
    </Chips>
  );
};
