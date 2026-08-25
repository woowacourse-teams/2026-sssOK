import { useState, type FormEvent } from "react";

import { BottomSheet } from "@/shared/ui/bottom-sheet";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Stack } from "@/shared/ui/stack";

export const MAX_NAME_LENGTH = 12;

export interface NameEntryBottomSheetProps {
  onSubmit: (name: string) => void;
  isPending?: boolean;
}

export const NameEntryBottomSheet = ({
  onSubmit,
  isPending = false,
}: NameEntryBottomSheetProps) => {
  const [name, setName] = useState("");

  const trimmedName = name.trim();
  const canSubmit = trimmedName.length > 0 && !isPending;

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!canSubmit) return;

    onSubmit(trimmedName);
  };

  return (
    <BottomSheet title="표시할 이름을 입력해주세요">
      <form onSubmit={handleSubmit}>
        <Stack gap={20}>
          <Input
            label="입력한 이름은 다른 사람에게 보여요"
            placeholder="이름을 입력하세요"
            value={name}
            maxLength={MAX_NAME_LENGTH}
            onChange={(event) => setName(event.target.value)}
            disabled={isPending}
            autoFocus
          />

          <Button type="submit" size="sm" disabled={!canSubmit}>
            입장하기
          </Button>
        </Stack>
      </form>
    </BottomSheet>
  );
};
