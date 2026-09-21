export type SelectionState =
  | { mode: "include"; includedIds: ReadonlySet<number> }
  | { mode: "exclude"; excludedIds: ReadonlySet<number> };

export interface MediaSelectionRequest {
  mode: "include" | "exclude";
  ids: number[];
}

export const emptySelection = (): SelectionState => ({
  mode: "include",
  includedIds: new Set(),
});

export const toMediaSelectionRequest = (selection: SelectionState): MediaSelectionRequest => ({
  mode: selection.mode,
  ids: [...(selection.mode === "include" ? selection.includedIds : selection.excludedIds)],
});
