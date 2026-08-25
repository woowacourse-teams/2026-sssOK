import { useState, type FormEvent } from "react";

import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Stack } from "@/shared/ui/stack";

export const MAX_NAME_LENGTH = 12;

/** 002 는 화면 제목으로, 005 는 시트 제목으로 쓴다. 묻는 말은 같다. */
export const NAME_ENTRY_TITLE = "표시할 이름을 입력해주세요";

export interface NameEntryFormProps {
  onSubmit: (name: string) => void;
  isPending?: boolean;
}

export const NameEntryForm = ({ onSubmit, isPending = false }: NameEntryFormProps) => {
  const [name, setName] = useState("");

  const trimmedName = name.trim();
  const canSubmit = trimmedName.length > 0 && !isPending;

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!canSubmit) return;

    onSubmit(trimmedName);
  };

  return (
    <form onSubmit={handleSubmit}>
      <Stack gap={20}>
        <Input
          label="입력한 이름은 다른 사람에게 보여요"
          placeholder="이름을 입력하세요"
          value={name}
          maxLength={MAX_NAME_LENGTH}
          onValueChange={setName}
          disabled={isPending}
          autoFocus
        />

        <Button type="submit" size="sm" disabled={!canSubmit}>
          입장하기
        </Button>
      </Stack>
    </form>
  );
};
