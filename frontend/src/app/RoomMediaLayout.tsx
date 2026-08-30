import { Outlet, useParams } from "react-router-dom";

import { readValidRoomSession } from "@/entities/session";
import { PhotoSelectionProvider } from "@/features/select-media";

export const RoomMediaLayout = () => {
  const { code = "" } = useParams();
  const session = readValidRoomSession(code);

  return (
    <PhotoSelectionProvider key={`${code}:${session?.userId ?? "guest"}`}>
      <Outlet />
    </PhotoSelectionProvider>
  );
};
