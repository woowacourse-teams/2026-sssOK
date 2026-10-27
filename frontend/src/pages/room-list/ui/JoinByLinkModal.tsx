import styled from "@emotion/styled";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Modal } from "@/shared/ui/modal";
import { Stack } from "@/shared/ui/stack";
import { colors, typography } from "@/shared/styles/tokens";
import { parseRoomCode } from "../lib/parseRoomCode";

interface JoinByLinkModalProps {
  onClose: () => void;
}

export const JoinByLinkModal = ({ onClose }: JoinByLinkModalProps) => {
  const navigate = useNavigate();
  const [link, setLink] = useState("");
  const [errorMessage, setErrorMessage] = useState("");

  const submit = () => {
    const code = parseRoomCode(link);

    if (!code) {
      setErrorMessage("쏙 초대 링크가 아니에요. 링크를 다시 확인해 주세요.");
      return;
    }

    navigate(ROUTES.roomInvite(code));
  };

  return (
    <Modal onClose={onClose} title={<Title>초대 링크로 들어가기</Title>}>
      <Stack gap={20}>
        <Input
          label="초대 링크"
          placeholder="받은 링크를 붙여넣어 주세요"
          value={link}
          errorMessage={errorMessage}
          onValueChange={(value) => {
            setLink(value);
            setErrorMessage("");
          }}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.nativeEvent.isComposing) submit();
          }}
          autoFocus
        />
        <Button size="lg" disabled={link.trim().length === 0} onClick={submit}>
          들어가기
        </Button>
      </Stack>
    </Modal>
  );
};

const Title = styled.h2`
  color: ${colors.textStrong};

  ${typography.heading3}
`;
