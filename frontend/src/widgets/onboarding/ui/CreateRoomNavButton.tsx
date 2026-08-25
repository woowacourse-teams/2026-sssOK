import { useNavigate } from "react-router-dom";

import { Button } from "@/shared/ui/button";
import { ROUTES } from "@/shared/config";

export const CreateRoomNavButton = () => {
  const navigate = useNavigate();

  const handleClick = () => {
    navigate(ROUTES.createRoom);
  };

  return (
    <Button size="lg" onClick={handleClick}>
      방 만들기
    </Button>
  );
};
