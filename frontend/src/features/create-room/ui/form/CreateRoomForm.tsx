import { useState, type FormEvent } from "react";

import { ApiError } from "@/shared/api";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { RadioGroup } from "@/shared/ui/radio-group";
import { Stack } from "@/shared/ui/stack";
import type { CreateRoomResponse } from "../../api/types";
import { INITIAL_CREATE_ROOM_FORM, type CreateRoomFormValues } from "../../model/createRoomForm";
import { useCreateRoomMutation } from "../../model/useCreateRoomMutation";
import { Form, SubmitArea, SubmitError } from "./CreateRoomForm.styles";

interface CreateRoomFormProps {
  onSuccess?: (room: CreateRoomResponse) => void;
}

export const CreateRoomForm = ({ onSuccess }: CreateRoomFormProps) => {
  const [formValues, setFormValues] = useState<CreateRoomFormValues>(INITIAL_CREATE_ROOM_FORM);
  const { mutate, isPending, error } = useCreateRoomMutation();

  const updateField = (name: keyof CreateRoomFormValues) => (value: string) => {
    setFormValues((previous) => ({ ...previous, [name]: value }));
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    mutate(formValues, { onSuccess });
  };

  const isValid = Boolean(formValues.nickname.trim() && formValues.name.trim());
  const errorMessage =
    error instanceof ApiError ? error.message : error ? "방을 만들지 못했습니다." : undefined;

  return (
    <Form onSubmit={handleSubmit}>
      <Input
        label="내 이름"
        name="nickname"
        value={formValues.nickname}
        maxLength={12}
        placeholder="예) 민수"
        onValueChange={updateField("nickname")}
      />

      <Input
        label="방 이름"
        name="name"
        value={formValues.name}
        maxLength={12}
        placeholder="예) 제주 여행"
        onValueChange={updateField("name")}
      />

      <Stack gap={16}>
        <RadioGroup
          label="업로드 권한"
          name="uploadPolicy"
          value={formValues.uploadPolicy}
          options={[
            { label: "누구나", value: "everyone" },
            { label: "방장만", value: "host" },
          ]}
          onValueChange={updateField("uploadPolicy")}
        />

        <RadioGroup
          label="방 만료 시간"
          name="expiryHours"
          value={formValues.expiryHours}
          options={[
            { label: "1일", value: "24" },
            { label: "3일", value: "72" },
          ]}
          onValueChange={updateField("expiryHours")}
        />
      </Stack>

      <SubmitArea>
        {errorMessage && <SubmitError role="alert">{errorMessage}</SubmitError>}
        <Button size="lg" type="submit" disabled={!isValid || isPending}>
          {isPending ? "방 만드는 중..." : "방 만들기"}
        </Button>
      </SubmitArea>
    </Form>
  );
};
