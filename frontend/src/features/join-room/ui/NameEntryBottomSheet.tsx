import { BottomSheet } from "@/shared/ui/bottom-sheet";
import { NAME_ENTRY_TITLE, NameEntryForm, type NameEntryFormProps } from "./NameEntryForm";

/**
 * 002 입장에서 쓰는 형태. 뒤에 들어갈 방의 갤러리를 깔고 그 위로 올린다 —
 * "이름만 정하면 이 갤러리로 들어간다" 를 보여 주려는 것이다.
 */
export const NameEntryBottomSheet = (props: NameEntryFormProps) => {
  return (
    <BottomSheet title={NAME_ENTRY_TITLE}>
      <NameEntryForm {...props} />
    </BottomSheet>
  );
};
