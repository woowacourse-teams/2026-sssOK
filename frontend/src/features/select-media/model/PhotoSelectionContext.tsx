import { createContext, useState, type Dispatch, type ReactNode, type SetStateAction } from "react";

interface PhotoSelectionState {
  selectedIds: ReadonlySet<number>;
  setSelectedIds: Dispatch<SetStateAction<ReadonlySet<number>>>;
}

export const PhotoSelectionContext = createContext<PhotoSelectionState | null>(null);

/** 갤러리와 상세 화면이 같은 선택 상태를 사용한다. 방·사용자가 바뀌면 초기화한다. */
export const PhotoSelectionProvider = ({ children }: { children: ReactNode }) => {
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<number>>(() => new Set());
  return (
    <PhotoSelectionContext.Provider value={{ selectedIds, setSelectedIds }}>
      {children}
    </PhotoSelectionContext.Provider>
  );
};
