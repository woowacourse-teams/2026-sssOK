import type { Room } from "@/entities/room";
import type { UpdateRoomRequest } from "../api/types";

export interface UpdateRoomFormValues {
  name: string;
  uploadPolicy: "everyone" | "host";
  expiryHours: "" | "24" | "72";
}

export const createInitialUpdateRoomForm = (room: Room): UpdateRoomFormValues => ({
  name: room.name,
  uploadPolicy: room.uploadPolicy,
  // 응답에는 기존 expiryHours가 없으므로 사용자가 새로 선택하기 전에는 보내지 않는다.
  expiryHours: "",
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
  if (formValues.expiryHours !== "") {
    request.expiryHours = Number(formValues.expiryHours) as 24 | 72;
  }

  return request;
};
