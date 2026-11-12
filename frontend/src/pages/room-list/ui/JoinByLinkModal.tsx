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

  const submit = (): void => {
    const code = parseRoomCode(link);

    if (!code) {
      setErrorMessage("방 코드나 링크를 다시 확인해 주세요.");
      return;
    }

    // 이미 쏙 안에 있으니 앱 열기를 권하는 /join 을 거치지 않는다.
    // 링크를 받아 방을 여는 처리가 없는 예전 앱에서 /join 이 앱을 다시 열면 방까지 가지 못한다.
    navigate(ROUTES.roomEntry(code));
  };

  return (
    <Modal onClose={onClose} title={<Title>링크·코드로 들어가기</Title>}>
      <Stack gap={20}>
        <Input
          label="방 코드 또는 초대 링크"
          placeholder="8자리 코드나 링크를 넣어 주세요"
          value={link}
          errorMessage={errorMessage}
          onValueChange={(value) => {
            setLink(value);
            setErrorMessage("");
          }}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.nativeEvent.isComposing) submit();
          }}
          autoCapitalize="characters"
          autoCorrect="off"
          autoComplete="off"
          spellCheck={false}
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
