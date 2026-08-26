import { BottomSheet } from "@/shared/ui/bottom-sheet";
import { NAME_ENTRY_TITLE, NameEntryForm, type NameEntryFormProps } from "./NameEntryForm";

/**
 * 뒤에 살아 있는 화면이 있을 때 쓰는 형태.
 * 005 방장 초기설정처럼 방 설정 화면 위로 올라오는 경우다.
 * 002 입장은 뒤에 아무것도 없어서 시트가 아니라 화면 자체로 그린다.
 */
export const NameEntryBottomSheet = (props: NameEntryFormProps) => {
  return (
    <BottomSheet title={NAME_ENTRY_TITLE}>
      <NameEntryForm {...props} />
    </BottomSheet>
  );
};
