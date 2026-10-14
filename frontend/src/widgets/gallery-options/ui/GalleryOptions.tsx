import { useState } from "react";
import { HiCheck, HiChevronDown } from "react-icons/hi2";

import type { PhotoFilter, PhotoSort } from "@/entities/media";
import { DropdownMenu, DropdownMenuItem } from "@/shared/ui/dropdown-menu";
import {
  Controls,
  DropdownButton,
  DropdownContainer,
  MenuCheck,
  Options,
  SelectAllButton,
  SelectMark,
} from "./GalleryOptions.styles";

const FILTER_OPTIONS: Array<{ value: PhotoFilter; label: string }> = [
  { value: "all", label: "전체" },
  { value: "mine", label: "내 사진" },
  { value: "others", label: "다른 사람 사진" },
  { value: "liked", label: "좋아요한 사진" },
];

const SORT_OPTIONS: Array<{ value: PhotoSort; label: string }> = [
  { value: "upload", label: "업로드 순" },
  { value: "popular", label: "인기순" },
];

interface GalleryOptionsProps {
  selectedOption: PhotoFilter;
  onSelectOption: (option: PhotoFilter) => void;
  selectedSort: PhotoSort;
  onSelectSort: (sort: PhotoSort) => void;
  hideViewControls?: boolean;
  isAllSelected: boolean;
  canSelectAll: boolean;
  onToggleAll: () => void;
}

export const GalleryOptions = ({
  selectedOption,
  onSelectOption,
  selectedSort,
  onSelectSort,
  hideViewControls = false,
  isAllSelected,
  canSelectAll,
  onToggleAll,
}: GalleryOptionsProps) => {
  const [openMenu, setOpenMenu] = useState<"filter" | "sort" | null>(null);
  const selectedFilterLabel =
    FILTER_OPTIONS.find((option) => option.value === selectedOption)?.label ?? "전체";
  const selectedSortLabel =
    SORT_OPTIONS.find((option) => option.value === selectedSort)?.label ?? "업로드 순";

  const selectFilter = (option: PhotoFilter) => {
    onSelectOption(option);
    setOpenMenu(null);
  };

  const selectSort = (sort: PhotoSort) => {
    onSelectSort(sort);
    setOpenMenu(null);
  };

  return (
    <Options>
      {!hideViewControls && (
        <Controls>
          <DropdownContainer>
            <DropdownButton
              type="button"
              aria-label={`${selectedFilterLabel} 필터 열기`}
              aria-expanded={openMenu === "filter"}
              onClick={() => setOpenMenu((current) => (current === "filter" ? null : "filter"))}
            >
              {selectedFilterLabel}
              <HiChevronDown />
            </DropdownButton>
            {openMenu === "filter" && (
              <DropdownMenu align="start" onClose={() => setOpenMenu(null)}>
                {FILTER_OPTIONS.map((option) => (
                  <DropdownMenuItem
                    key={option.value}
                    icon={
                      <MenuCheck $visible={selectedOption === option.value}>
                        <HiCheck />
                      </MenuCheck>
                    }
                    aria-pressed={selectedOption === option.value}
                    onClick={() => selectFilter(option.value)}
                  >
                    {option.label}
                  </DropdownMenuItem>
                ))}
              </DropdownMenu>
            )}
          </DropdownContainer>

          <DropdownContainer>
            <DropdownButton
              type="button"
              aria-label={`${selectedSortLabel} 정렬 열기`}
              aria-expanded={openMenu === "sort"}
              onClick={() => setOpenMenu((current) => (current === "sort" ? null : "sort"))}
            >
              {selectedSortLabel}
              <HiChevronDown />
            </DropdownButton>
            {openMenu === "sort" && (
              <DropdownMenu align="start" onClose={() => setOpenMenu(null)}>
                {SORT_OPTIONS.map((option) => (
                  <DropdownMenuItem
                    key={option.value}
                    icon={
                      <MenuCheck $visible={selectedSort === option.value}>
                        <HiCheck />
                      </MenuCheck>
                    }
                    aria-pressed={selectedSort === option.value}
                    onClick={() => selectSort(option.value)}
                  >
                    {option.label}
                  </DropdownMenuItem>
                ))}
              </DropdownMenu>
            )}
          </DropdownContainer>
        </Controls>
      )}

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
