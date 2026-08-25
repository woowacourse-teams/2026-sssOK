import { useNavigate } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { Button } from "@/shared/ui/button";

export const JoinRoomNavButton = () => {
  const navigate = useNavigate();

  const handleClick = () => {
    navigate(ROUTES.joinRoom);
  };

  return (
    <Button size="lg" variant="default" onClick={handleClick}>
      코드로 입장
    </Button>
  );
};
