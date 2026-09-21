import { createContext, useState, type Dispatch, type ReactNode, type SetStateAction } from "react";
import { emptySelection, type SelectionState } from "./types";

interface PhotoSelectionState {
  selection: SelectionState;
  setSelection: Dispatch<SetStateAction<SelectionState>>;
}

export const PhotoSelectionContext = createContext<PhotoSelectionState | null>(null);

/** 갤러리와 상세 화면이 같은 선택 상태를 사용한다. 방·사용자가 바뀌면 초기화한다. */
export const PhotoSelectionProvider = ({ children }: { children: ReactNode }) => {
  const [selection, setSelection] = useState<SelectionState>(emptySelection);
  return (
    <PhotoSelectionContext.Provider value={{ selection, setSelection }}>
      {children}
    </PhotoSelectionContext.Provider>
  );
};
