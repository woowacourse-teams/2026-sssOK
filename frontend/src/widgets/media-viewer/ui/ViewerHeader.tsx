import { ViewerButton } from "./ViewerControls.styles";
import { HiArrowLeft, HiCheck } from "react-icons/hi2";
import { Header, Counter, SelectionButton } from "./ViewerHeader.styles";

interface ViewerHeaderProps {
  index: number;
  totalCount: number;
  selected: boolean;
  disabled: boolean;
  onClose: () => void;
  onToggle: () => void;
}

export const ViewerHeader = ({
  index,
  totalCount,
  selected,
  disabled,
  onClose,
  onToggle,
}: ViewerHeaderProps) => (
  <Header>
    <ViewerButton type="button" aria-label="갤러리로 돌아가기" onClick={onClose}>
      <HiArrowLeft size={20} />
    </ViewerButton>
    <Counter aria-live="polite">{index < 0 ? "사진 보기" : `${index + 1} / ${totalCount}`}</Counter>
    <SelectionButton
      type="button"
      aria-label="사진 선택"
      aria-pressed={selected}
      disabled={disabled}
      onClick={onToggle}
    >
      <HiCheck size={19} />
    </SelectionButton>
  </Header>
);
