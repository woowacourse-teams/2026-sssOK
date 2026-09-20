import { authHandlers } from "./auth";
import { downloadHandlers } from "./download";
import { mediaHandlers } from "./media";
import { roomHandlers } from "./room";
import { uploadHandlers } from "./upload";

export const handlers = [
  ...authHandlers,
  ...roomHandlers,
  ...uploadHandlers,
  ...downloadHandlers,
  ...mediaHandlers,
];
