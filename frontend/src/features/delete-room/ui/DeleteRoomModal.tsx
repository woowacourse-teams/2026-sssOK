import styled from "@emotion/styled";

import { Button } from "@/shared/ui/button";
import { Modal } from "@/shared/ui/modal";
import { Row } from "@/shared/ui/row";
import { Stack } from "@/shared/ui/stack";
import { colors, typography } from "@/shared/styles/tokens";
import deleteRoomImage from "../assets/delete-room.png";
import { useDeleteRoomMutation } from "../model/useDeleteRoomMutation";

interface DeleteRoomModalProps {
  roomId: number;
  accessToken: string;
  onClose: () => void;
  onSuccess: () => void;
}

export const DeleteRoomModal = ({
  roomId,
  accessToken,
  onClose,
  onSuccess,
}: DeleteRoomModalProps) => {
  const deleteRoomMutation = useDeleteRoomMutation({ roomId, accessToken, onSuccess });

  return (
    <Modal onClose={onClose}>
      <Stack gap={20}>
        <DeleteRoomImage src={deleteRoomImage} alt="방 삭제 경고" />

        <Stack gap={8}>
          <Title>방을 삭제할까요?</Title>
          <Description>
            삭제한 방과 사진은
            <br />
            다시 복구할 수 없어요.
          </Description>
          {deleteRoomMutation.isError && <ErrorMessage>방을 삭제하지 못했어요.</ErrorMessage>}
        </Stack>

        <Row gap={12}>
          <Button variant="default" onClick={onClose}>
            취소
          </Button>
          <Button
            variant="danger"
            disabled={deleteRoomMutation.isPending}
            onClick={() => deleteRoomMutation.mutate()}
          >
            삭제하기
          </Button>
        </Row>
      </Stack>
    </Modal>
  );
};

const DeleteRoomImage = styled.img`
  align-self: center;
  width: 105px;
  height: auto;
`;

const Title = styled.h2`
  color: ${colors.textStrong};
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
