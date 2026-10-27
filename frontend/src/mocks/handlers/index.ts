import { adminHandlers } from "./admin";
import { authHandlers } from "./auth";
import { downloadHandlers } from "./download";
import { feedbackHandlers } from "./feedback";
import { mediaHandlers } from "./media";
import { myRoomsHandlers } from "./myRooms";
import { roomHandlers } from "./room";
import { uploadHandlers } from "./upload";

export const handlers = [
  ...adminHandlers,
  ...feedbackHandlers,
  ...authHandlers,
  ...myRoomsHandlers,
  ...roomHandlers,
  ...uploadHandlers,
  ...downloadHandlers,
  ...mediaHandlers,
];
