import { useNavigate } from "react-router-dom";

import { Button } from "@/shared/ui/button";

export const CreateRoomNavButton = () => {
  const navigate = useNavigate();

  const handleClick = () => {
    navigate("/rooms/create");
  };

  return (
    <Button size="lg" onClick={handleClick}>
      방 만들기
    </Button>
  );
};
