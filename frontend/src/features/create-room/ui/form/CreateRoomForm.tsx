import { useState, type FormEvent } from "react";

import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { RadioGroup } from "@/shared/ui/radio-group";
import { INITIAL_CREATE_ROOM_FORM, type CreateRoomFormValues } from "../../model/createRoomForm";
import { Form, SubmitArea } from "./CreateRoomForm.styles";
import { Stack } from "@/shared/ui/stack";

interface CreateRoomFormProps {
  onSubmit?: (values: CreateRoomFormValues) => void;
}

export const CreateRoomForm = ({ onSubmit }: CreateRoomFormProps) => {
  const [formValues, setFormValues] = useState<CreateRoomFormValues>(INITIAL_CREATE_ROOM_FORM);

  const updateField = (name: keyof CreateRoomFormValues) => (value: string) => {
    setFormValues((previous) => ({ ...previous, [name]: value }));
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    onSubmit?.(formValues);
  };

  const isValid = Boolean(formValues.nickname.trim() && formValues.name.trim());

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
        <Button size="lg" type="submit" disabled={!isValid}>
          방 만들기
        </Button>
      </SubmitArea>
    </Form>
  );
};
