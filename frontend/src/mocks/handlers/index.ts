import { adminHandlers } from "./admin";
import { authHandlers } from "./auth";
import { downloadHandlers } from "./download";
import { feedbackHandlers } from "./feedback";
import { mediaHandlers } from "./media";
import { roomHandlers } from "./room";
import { uploadHandlers } from "./upload";

export const handlers = [
  ...adminHandlers,
  ...feedbackHandlers,
  ...authHandlers,
  ...roomHandlers,
  ...uploadHandlers,
  ...downloadHandlers,
  ...mediaHandlers,
];
