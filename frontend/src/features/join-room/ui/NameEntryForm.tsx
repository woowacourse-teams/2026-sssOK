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
  const hasWhitespaceError = name.length > 0 && trimmedName.length === 0;
  const buttonLabel = isPending
    ? "입장하기. 입장 요청을 처리하고 있어요."
    : trimmedName.length === 0
      ? hasWhitespaceError
        ? "입장하기. 공백을 제외한 이름을 입력해야 입장할 수 있어요."
        : "입장하기. 이름을 입력해야 입장할 수 있어요."
      : "입장하기";

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
          aria-label="표시할 이름"
          aria-description={
            hasWhitespaceError
              ? "공백을 제외한 이름을 입력해주세요. 최대 12자까지 입력할 수 있어요. 입력한 이름은 다른 사람에게 보여요."
              : "최대 12자까지 입력할 수 있어요. 입력한 이름은 다른 사람에게 보여요."
          }
          aria-invalid={hasWhitespaceError}
          required
          placeholder="이름을 입력하세요"
          value={name}
          maxLength={MAX_NAME_LENGTH}
          countLabel={`최대 ${MAX_NAME_LENGTH}자 중 ${name.length}자 입력`}
          onValueChange={setName}
          disabled={isPending}
          autoFocus
        />

        <Button type="submit" size="sm" disabled={!canSubmit} aria-label={buttonLabel}>
          입장하기
        </Button>
      </Stack>
    </form>
  );
};
