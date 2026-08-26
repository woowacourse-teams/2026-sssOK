import { useNavigate } from "react-router-dom";

import { CreateRoomForm } from "@/features/create-room";
import { ROUTES } from "@/shared/config";
import { CreateRoomHeader } from "./header/CreateRoomHeader";
import { Page } from "./CreateRoomPage.styles";

export const CreateRoomPage = () => {
  const navigate = useNavigate();

  return (
    <Page>
      <CreateRoomHeader />
      <CreateRoomForm onSuccess={(room) => navigate(ROUTES.gallery(room.code))} />
    </Page>
  );
};
