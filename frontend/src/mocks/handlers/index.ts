import { authHandlers } from "./auth";
import { roomHandlers } from "./room";

export const handlers = [...authHandlers, ...roomHandlers];
