import { Button } from "@/shared/ui/button";
import { useNavigate } from "react-router-dom";

export const JoinRoomButton = () => {
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
