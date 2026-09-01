import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";

import type { RoomFolder } from "@/entities/room";
import { isApiError } from "@/shared/api";
import { BottomSheet } from "@/shared/ui/bottom-sheet";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Stack } from "@/shared/ui/stack";
import { createFolder } from "../api/createFolder";

interface CreateFolderBottomSheetProps {
  roomId: number;
  accessToken: string;
  onClose: () => void;
  onSuccess: (folder: RoomFolder) => void | Promise<void>;
}

const MAX_FOLDER_NAME_LENGTH = 12;

export const CreateFolderBottomSheet = ({
  roomId,
  accessToken,
  onClose,
  onSuccess,
}: CreateFolderBottomSheetProps) => {
  const [name, setName] = useState("");
  const mutation = useMutation({
    mutationFn: (folderName: string) => createFolder({ roomId, accessToken, name: folderName }),
    onSuccess,
  });

  const trimmedName = name.trim();
  const errorMessage = mutation.isError
    ? isApiError(mutation.error)
      ? mutation.error.message
      : "폴더를 만들지 못했어요."
    : "";

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!trimmedName || mutation.isPending) return;
    mutation.mutate(trimmedName);
  };

  return (
    <BottomSheet title="새 폴더 만들기" onClose={onClose}>
      <form onSubmit={submit}>
        <Stack gap={16}>
          <Input
            autoFocus
            label="폴더 이름"
            placeholder="폴더 이름을 입력하세요"
            value={name}
            maxLength={MAX_FOLDER_NAME_LENGTH}
            errorMessage={errorMessage}
            disabled={mutation.isPending}
            onValueChange={(value) => {
              setName(value);
              if (mutation.isError) mutation.reset();
            }}
          />
          <Button type="submit" disabled={!trimmedName || mutation.isPending}>
            {mutation.isPending ? "만드는 중..." : "만들기"}
          </Button>
        </Stack>
      </form>
    </BottomSheet>
  );
};
