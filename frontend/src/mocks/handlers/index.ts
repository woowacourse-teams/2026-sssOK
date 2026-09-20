import { adminHandlers } from "./admin";
import { authHandlers } from "./auth";
import { downloadHandlers } from "./download";
import { mediaHandlers } from "./media";
import { roomHandlers } from "./room";
import { uploadHandlers } from "./upload";

export const handlers = [
  ...adminHandlers,
  ...authHandlers,
  ...roomHandlers,
  ...uploadHandlers,
  ...downloadHandlers,
  ...mediaHandlers,
];
