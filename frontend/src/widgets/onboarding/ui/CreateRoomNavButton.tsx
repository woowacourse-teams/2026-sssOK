import { useNavigate } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { CreateRoomButton } from "./CreateRoomNavButton.styles";

export const CreateRoomNavButton = () => {
  const navigate = useNavigate();

  const handleClick = () => {
    navigate(ROUTES.createRoom);
  };

  return (
    <CreateRoomButton size="lg" onClick={handleClick}>
      링크 만들기
    </CreateRoomButton>
  );
};
