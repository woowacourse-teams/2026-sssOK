import styled from "@emotion/styled";

import { ApiError } from "@/shared/api";
import { Modal } from "@/shared/ui/modal";
import { Stack } from "@/shared/ui/stack";
import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { useLeaveRoomMutation } from "../model/useLeaveRoomMutation";

interface LeaveRoomModalProps {
  roomId: number;
  roomName: string;
  accessToken?: string;
  onClose: () => void;
  onSuccess: () => void;
}

const errorMessageOf = (error: unknown) =>
  error instanceof ApiError && error.code === "ROOM_HOST_CANNOT_LEAVE"
    ? "방장은 방에서 나갈 수 없어요."
    : "방에서 나가지 못했어요. 잠시 후 다시 시도해 주세요.";

export const LeaveRoomModal = ({
  roomId,
  roomName,
  accessToken,
  onClose,
  onSuccess,
}: LeaveRoomModalProps) => {
  const leaveRoomMutation = useLeaveRoomMutation({ roomId, accessToken, onSuccess });

  return (
    <Modal onClose={onClose} showClose={false}>
      <Stack gap={20}>
        <Stack gap={8}>
          <Title>
            ‘{roomName}’에서
            <br />
            나갈까요?
          </Title>
          <Description>
            올린 사진은 방에 그대로 남아요.
            <br />
            다시 들어오려면 초대 링크가 필요해요.
          </Description>
          {leaveRoomMutation.isError && (
            <ErrorMessage role="alert">{errorMessageOf(leaveRoomMutation.error)}</ErrorMessage>
          )}
        </Stack>

        <Actions>
          <ActionButton type="button" onClick={onClose}>
            취소
          </ActionButton>
          <ActionButton
            type="button"
            $tone="danger"
            disabled={leaveRoomMutation.isPending}
            onClick={() => leaveRoomMutation.mutate()}
          >
            나가기
          </ActionButton>
        </Actions>
      </Stack>
    </Modal>
  );
};

const Title = styled.h2`
  color: ${colors.textStrong};
  text-align: center;
  overflow-wrap: anywhere;

  ${typography.heading3}
`;

const Description = styled.p`
  color: ${colors.textSecondary};
  font-size: 14px;
  line-height: 21px;
  text-align: center;
`;

const Actions = styled.div`
  display: flex;
  gap: ${spacing[8]};
`;

const ActionButton = styled.button<{ $tone?: "danger" }>`
  flex: 1;
  height: 48px;
  border-radius: ${radius[12]};
  background: ${({ $tone }) => ($tone === "danger" ? colors.danger : colors.backgroundSubtle)};
  color: ${({ $tone }) => ($tone === "danger" ? colors.textInverse : colors.textStrong)};
  font-size: 15px;
  font-weight: 700;

  &:disabled {
    background: ${colors.disabled};
    color: ${colors.textInverse};
  }
`;

const ErrorMessage = styled.p`
  color: ${colors.danger};
  text-align: center;

  ${typography.caption1}
`;
