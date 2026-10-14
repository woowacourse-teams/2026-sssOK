import type { Room } from "@/entities/room";
import type { UpdateRoomRequest } from "../api/types";

export interface UpdateRoomFormValues {
  name: string;
  uploadPolicy: "everyone" | "host";
}

export const createInitialUpdateRoomForm = (room: Room): UpdateRoomFormValues => ({
  name: room.name,
  uploadPolicy: room.uploadPolicy,
});

export const createUpdateRoomRequest = (
  formValues: UpdateRoomFormValues,
  room: Room,
): UpdateRoomRequest => {
  const request: UpdateRoomRequest = {};
  const trimmedName = formValues.name.trim();

  if (trimmedName !== room.name) request.name = trimmedName;
  if (formValues.uploadPolicy !== room.uploadPolicy) {
    request.uploadPolicy = formValues.uploadPolicy;
  }

  return request;
};
