import { useNavigate } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { TextLink } from "./JoinRoomNavButton.styles";

/**
 * 코드로 들어오는 사람은 이미 초대를 받은 사람이다. 처음 온 사람의 눈이 방 만들기에
 * 먼저 가도록, 같은 무게의 버튼이 아니라 보조 링크로 둔다.
 */
export const JoinRoomNavButton = () => {
  const navigate = useNavigate();

  const handleClick = () => {
    navigate(ROUTES.joinRoom);
  };

  return (
    <TextLink type="button" onClick={handleClick}>
      초대 코드를 받았나요? <strong>코드로 입장하기</strong>
    </TextLink>
  );
};
