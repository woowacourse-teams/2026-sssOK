import { Button } from "@/shared/ui/button";
import { useNavigate } from "react-router-dom";

export const CreateRoomButton = () => {
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
