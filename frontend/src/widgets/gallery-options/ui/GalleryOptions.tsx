import { HiCheck } from "react-icons/hi2";

import type { PhotoFilter } from "@/entities/media";
import { Row } from "@/shared/ui/row";
import { OptionButton, Options, SelectAllButton, SelectMark } from "./GalleryOptions.styles";

const OPTIONS: Array<{ value: PhotoFilter; label: string }> = [
  { value: "all", label: "전체" },
  { value: "mine", label: "내 사진" },
  { value: "others", label: "다른 사람 사진" },
];

interface GalleryOptionsProps {
  selectedOption: PhotoFilter;
  onSelectOption: (option: PhotoFilter) => void;
  isAllSelected: boolean;
  canSelectAll: boolean;
  onToggleAll: () => void;
}

export const GalleryOptions = ({
  selectedOption,
  onSelectOption,
  isAllSelected,
  canSelectAll,
  onToggleAll,
}: GalleryOptionsProps) => {
  return (
    <Options>
      <Row align="center" gap={24}>
        {OPTIONS.map((option) => (
          <OptionButton
            key={option.value}
            type="button"
            $active={selectedOption === option.value}
            onClick={() => onSelectOption(option.value)}
          >
            {option.label}
          </OptionButton>
        ))}
      </Row>
      <SelectAllButton
        type="button"
        $active={isAllSelected}
        disabled={!canSelectAll}
        onClick={onToggleAll}
      >
        전체 선택
        <SelectMark $active={isAllSelected}>
          <HiCheck />
        </SelectMark>
      </SelectAllButton>
    </Options>
  );
};
