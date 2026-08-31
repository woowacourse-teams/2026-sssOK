import styled from "@emotion/styled";

import deleteRoomImage from "@/features/delete-room/assets/delete-room.png";
import { colors, typography } from "@/shared/styles/tokens";
import { Button } from "@/shared/ui/button";
import { Modal } from "@/shared/ui/modal";
import { Row } from "@/shared/ui/row";
import { Stack } from "@/shared/ui/stack";
import { useDeleteFolderMutation } from "../model/useDeleteFolderMutation";

interface DeleteFolderModalProps {
  roomId: number;
  folderId: number;
  folderName: string;
  accessToken: string;
  onClose: () => void;
  onSuccess: () => void | Promise<void>;
}

export const DeleteFolderModal = ({
  roomId,
  folderId,
  folderName,
  accessToken,
  onClose,
  onSuccess,
}: DeleteFolderModalProps) => {
  const mutation = useDeleteFolderMutation({ roomId, folderId, accessToken, onSuccess });

  return (
    <Modal onClose={onClose}>
      <Stack gap={20}>
        <DeleteFolderImage src={deleteRoomImage} alt="폴더 삭제 경고" />

        <Stack gap={8}>
          <Title>‘{folderName}’ 폴더를 삭제할까요?</Title>
          <Description>
            폴더만 삭제되고
            <br />
            사진은 그대로 유지돼요.
          </Description>
          {mutation.isError && <ErrorMessage>폴더를 삭제하지 못했어요.</ErrorMessage>}
        </Stack>

        <Row gap={12}>
          <Button variant="default" onClick={onClose}>
            취소
          </Button>
          <Button variant="danger" disabled={mutation.isPending} onClick={() => mutation.mutate()}>
            삭제
          </Button>
        </Row>
      </Stack>
    </Modal>
  );
};

const DeleteFolderImage = styled.img`
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
