import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import styled from "@emotion/styled";
import { LuCheck, LuFolder, LuFolderPlus } from "react-icons/lu";

import type { MediaUploaderFilter } from "@/entities/media";
import type { RoomFolder } from "@/entities/room";
import type { MediaSelectionRequest } from "@/features/select-media";
import { isApiError } from "@/shared/api";
import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { BottomSheet } from "@/shared/ui/bottom-sheet";
import { Button } from "@/shared/ui/button";
import { Stack } from "@/shared/ui/stack";
import { addMediaToFolder } from "../api/addMediaToFolder";
import { removeMediaFromFolder } from "../api/removeMediaFromFolder";

interface MoveMediaFolderBottomSheetProps {
  roomId: number;
  selection: MediaSelectionRequest;
  selectedCount: number;
  folders: RoomFolder[];
  currentFolderId: number | null;
  uploader: MediaUploaderFilter;
  token: string;
  onCreateFolder: () => Promise<RoomFolder | null>;
  onClose: () => void;
  onSuccess: (folderId: number | null) => void | Promise<void>;
}

export const MoveMediaFolderBottomSheet = ({
  roomId,
  selection,
  selectedCount,
  folders,
  currentFolderId,
  uploader,
  token,
  onCreateFolder,
  onClose,
  onSuccess,
}: MoveMediaFolderBottomSheetProps) => {
  const [folderId, setFolderId] = useState<number | null>(null);
  const [isCreatingFolder, setIsCreatingFolder] = useState(false);
  const mutation = useMutation({
    mutationFn: (targetFolderId: number) =>
      addMediaToFolder({ roomId, selection, folderId: targetFolderId, uploader, token }),
    onSuccess: (_, targetFolderId) => onSuccess(targetFolderId),
  });
  const removeMutation = useMutation({
    mutationFn: (folderId: number) =>
      removeMediaFromFolder({ roomId, selection, folderId, uploader, token }),
    onSuccess: (_, folderId) => onSuccess(folderId),
  });
  const createAndMoveMutation = useMutation({
    mutationFn: async () => {
      const folder = await onCreateFolder();
      if (!folder) return null;

      await addMediaToFolder({ roomId, selection, folderId: folder.id, uploader, token });
      return folder.id;
    },
    onSuccess: (createdFolderId) => {
      if (createdFolderId !== null) void onSuccess(createdFolderId);
    },
  });
  const isPending =
    mutation.isPending || removeMutation.isPending || createAndMoveMutation.isPending;

  if (isCreatingFolder) return null;

  return (
    <BottomSheet
      title={`${selectedCount}개를 어디로 옮길까요?`}
      onClose={isPending ? undefined : onClose}
    >
      <Stack gap={16}>
        <FolderList>
          {folders.map((folder) => {
            const selected = folderId === folder.id;
            return (
              <FolderButton
                key={folder.id}
                type="button"
                $selected={selected}
                disabled={isPending}
                onClick={() => setFolderId((current) => (current === folder.id ? null : folder.id))}
              >
                <FolderIcon>
                  <LuFolder />
                </FolderIcon>
                <FolderName>{folder.name}</FolderName>
                {selected ? (
                  <CheckSlot>
                    <LuCheck aria-hidden="true" />
                  </CheckSlot>
                ) : (
                  <PhotoCount>{folder.photoCount}</PhotoCount>
                )}
              </FolderButton>
            );
          })}
          <FolderButton
            type="button"
            $selected={false}
            disabled={isPending}
            onClick={() => {
              setIsCreatingFolder(true);
              createAndMoveMutation.mutate(undefined, {
                onSettled: () => setIsCreatingFolder(false),
              });
            }}
          >
            <FolderIcon>
              <LuFolderPlus />
            </FolderIcon>
            <FolderName>새 폴더 만들어 옮기기</FolderName>
          </FolderButton>
        </FolderList>
        {folders.length > 0 && <Notice>사진을 담을 폴더 하나를 선택해 주세요.</Notice>}
        {currentFolderId !== null && (
          <Button
            variant="default"
            disabled={isPending}
            onClick={() => removeMutation.mutate(currentFolderId)}
          >
            {removeMutation.isPending ? "꺼내는 중..." : "폴더에서 꺼내기"}
          </Button>
        )}
        {mutation.isError && (
          <ErrorMessage role="alert">
            {isApiError(mutation.error)
              ? mutation.error.message
              : "사진을 폴더에 추가하지 못했어요."}
          </ErrorMessage>
        )}
        {removeMutation.isError && (
          <ErrorMessage role="alert">
            {isApiError(removeMutation.error)
              ? removeMutation.error.message
              : "사진을 폴더에서 꺼내지 못했어요."}
          </ErrorMessage>
        )}
        {createAndMoveMutation.isError && (
          <ErrorMessage role="alert">
            {isApiError(createAndMoveMutation.error)
              ? createAndMoveMutation.error.message
              : "새 폴더를 만들고 사진을 옮기지 못했어요."}
          </ErrorMessage>
        )}
        <Button
          disabled={folderId === null || isPending}
          onClick={() => folderId !== null && mutation.mutate(folderId)}
        >
          {mutation.isPending ? "이동 중..." : "여기로 이동"}
        </Button>
      </Stack>
    </BottomSheet>
  );
};

const FolderList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${spacing[12]};
  max-height: 240px;
  overflow-y: auto;
`;

const FolderButton = styled.button<{ $selected: boolean }>`
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: ${spacing[8]};
  width: 100%;
  min-height: 60px;
  padding: ${spacing[16]};
  border: 1.5px solid ${({ $selected }) => ($selected ? colors.primary : colors.borderDefault)};
  border-radius: ${radius[12]};
  color: ${({ $selected }) => ($selected ? colors.textAccent : colors.textStrong)};
  text-align: left;
`;

const FolderIcon = styled.span`
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  color: inherit;

  svg {
    width: 22px;
    height: 22px;
  }
`;

const FolderName = styled.span`
  ${typography.label5}
`;

const PhotoCount = styled.span`
  color: ${colors.textSecondary};
  ${typography.caption1}
`;

const CheckSlot = styled.span`
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: ${radius.full};
  background: ${colors.primary};
  color: ${colors.textInverse};

  svg {
    width: 15px;
    height: 15px;
  }
`;

const Notice = styled.p`
  color: ${colors.textSecondary};
  ${typography.caption3}
`;

const ErrorMessage = styled.p`
  color: ${colors.danger};
  text-align: center;
  ${typography.caption1}
`;
