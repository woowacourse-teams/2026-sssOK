import { Chip, FilterRow } from "./VersionFilter.styles";

interface VersionFilterProps {
  versions: string[];
  selectedVersion: string | null;
  onToggle: (version: string) => void;
}

export const VersionFilter = ({ versions, selectedVersion, onToggle }: VersionFilterProps) => {
  // 버전이 하나도 없으면 줄 자체를 그리지 않는다 — 빈 줄은 자리만 차지하고 알려주는 게 없다.
  if (versions.length === 0) return null;

  return (
    <FilterRow>
      {versions.map((version) => {
        const selected = version === selectedVersion;

        return (
          <Chip
            key={version}
            type="button"
            $selected={selected}
            aria-pressed={selected}
            onClick={() => onToggle(version)}
          >
            {version}
          </Chip>
        );
      })}
    </FilterRow>
  );
};
