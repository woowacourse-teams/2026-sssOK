import { useNavigate } from "react-router-dom";

import { Button } from "@/shared/ui/button";

export const JoinRoomNavButton = () => {
  const navigate = useNavigate();

  const handleClick = () => {
    navigate("/rooms/join");
  };

  return (
    <Button size="lg" variant="default" onClick={handleClick}>
      코드로 입장
    </Button>
  );
};
