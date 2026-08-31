import { useMutation } from "@tanstack/react-query";
import styled from "@emotion/styled";

import { isApiError } from "@/shared/api";
import { colors, typography } from "@/shared/styles/tokens";
import { Button } from "@/shared/ui/button";
import { Modal } from "@/shared/ui/modal";
import { Row } from "@/shared/ui/row";
import { Stack } from "@/shared/ui/stack";
import { deleteMediaBatch, type DeleteMediaBatchResponse } from "../api/deleteMediaBatch";

interface DeleteSelectedMediaModalProps {
  roomId: number;
  mediaIds: number[];
  token: string;
  onClose: () => void;
  onSuccess: (result: DeleteMediaBatchResponse) => void | Promise<void>;
}

export const DeleteSelectedMediaModal = ({
  roomId,
  mediaIds,
  token,
  onClose,
  onSuccess,
}: DeleteSelectedMediaModalProps) => {
  const mutation = useMutation({
    mutationFn: () => deleteMediaBatch({ roomId, mediaIds, token }),
    onSuccess,
  });

  return (
    <Modal onClose={mutation.isPending ? () => undefined : onClose} showClose={!mutation.isPending}>
      <Stack gap={20}>
        <Title>{mediaIds.length}개의 사진을 삭제할까요?</Title>
        <Description>삭제한 사진은 다시 복구할 수 없어요.</Description>
        {mutation.isError && (
          <ErrorMessage role="alert">
            {isApiError(mutation.error)
              ? mutation.error.message
              : "사진을 삭제하지 못했어요. 다시 시도해 주세요."}
          </ErrorMessage>
        )}
        <Row gap={12}>
          <Button variant="default" disabled={mutation.isPending} onClick={onClose}>
            취소
          </Button>
          <Button variant="danger" disabled={mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? "삭제 중…" : "삭제하기"}
          </Button>
        </Row>
      </Stack>
    </Modal>
  );
};

const Title = styled.h2`
  text-align: center;
  ${typography.heading3}
`;
const Description = styled.p`
  color: ${colors.textSecondary};
  text-align: center;
  ${typography.body}
`;
const ErrorMessage = styled.p`
  color: ${colors.danger};
  text-align: center;
  ${typography.caption1}
`;
