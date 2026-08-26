import { apiClient } from "@/shared/api";
import type { Room } from "../model/types";

export const getRoom = (code: string, token?: string) =>
  apiClient<Room>(`/rooms/${code}`, { token });
