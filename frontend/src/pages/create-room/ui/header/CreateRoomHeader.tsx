import { HiArrowLeft } from "react-icons/hi2";
import { useNavigate } from "react-router-dom";

import { IconButton } from "@/shared/ui/icon-button";
import { Header, Title } from "./CreateRoomHeader.styles";

export const CreateRoomHeader = () => {
  const navigate = useNavigate();

  return (
    <Header>
      <IconButton aria-label="이전 화면" onClick={() => navigate(-1)}>
        <HiArrowLeft />
      </IconButton>
      <Title>방 만들기</Title>
    </Header>
  );
};
