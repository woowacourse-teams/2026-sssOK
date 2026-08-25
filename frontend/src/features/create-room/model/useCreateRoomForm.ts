import { useState } from "react";

import { INITIAL_CREATE_ROOM_FORM, type CreateRoomFormValues } from "./createRoomForm";

export const useCreateRoomForm = () => {
  const [formValues, setFormValues] = useState<CreateRoomFormValues>(INITIAL_CREATE_ROOM_FORM);

  const updateField = (name: keyof CreateRoomFormValues) => (value: string) => {
    setFormValues((previous) => ({ ...previous, [name]: value }));
  };

  const isValid = Boolean(formValues.nickname.trim() && formValues.name.trim());

  return {
    formValues,
    updateField,
    isValid,
  };
};
