import { CreateRoomForm } from "@/features/create-room";
import { CreateRoomHeader } from "./header/CreateRoomHeader";
import { Page } from "./CreateRoomPage.styles";

export const CreateRoomPage = () => {
  return (
    <Page>
      <CreateRoomHeader />
      <CreateRoomForm />
    </Page>
  );
};
