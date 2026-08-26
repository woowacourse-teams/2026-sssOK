import { authHandlers } from "./auth";
import { roomHandlers } from "./room";
import { uploadHandlers } from "./upload";

export const handlers = [...authHandlers, ...roomHandlers, ...uploadHandlers];
