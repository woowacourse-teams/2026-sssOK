import { useState } from "react";

import type { Room } from "@/entities/room";
import {
  createInitialUpdateRoomForm,
  createUpdateRoomRequest,
  type UpdateRoomFormValues,
} from "./updateRoomForm";

export const useUpdateRoomForm = (room: Room) => {
  const [formValues, setFormValues] = useState<UpdateRoomFormValues>(() =>
    createInitialUpdateRoomForm(room),
  );

  const updateField = (name: keyof UpdateRoomFormValues) => (value: string) => {
    setFormValues((previous) => ({ ...previous, [name]: value }));
  };

  const request = createUpdateRoomRequest(formValues, room);
  const isValid = formValues.name.trim().length > 0;
  const hasChanges = Object.keys(request).length > 0;

  return {
    formValues,
    updateField,
    request,
    isValid,
    hasChanges,
  };
};
