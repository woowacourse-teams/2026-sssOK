import { type FormEvent, useEffect } from "react";

import { ApiError } from "@/shared/api";
import { track } from "@/shared/lib";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { RadioGroup } from "@/shared/ui/radio-group";
import { Slider } from "@/shared/ui/slider";
import { Stack } from "@/shared/ui/stack";
import type { CreateRoomResponse } from "../../api/types";
import { MAX_EXPIRY_DAYS, MIN_EXPIRY_DAYS } from "../../model/createRoomForm";
import { createExpiryDayMarks } from "../../model/expiryDayMarks";
import { useCreateRoomForm } from "../../model/useCreateRoomForm";
import { useCreateRoomMutation } from "../../model/useCreateRoomMutation";
import { Form, SubmitArea, SubmitError } from "./CreateRoomForm.styles";

const EXPIRY_DAY_MARKS = createExpiryDayMarks(MIN_EXPIRY_DAYS, MAX_EXPIRY_DAYS);

interface CreateRoomFormProps {
  onSuccess?: (room: CreateRoomResponse) => void;
}

export const CreateRoomForm = ({ onSuccess }: CreateRoomFormProps) => {
  const { formValues, updateField, isValid } = useCreateRoomForm();
  const { mutate, isPending, error } = useCreateRoomMutation();

  // 방 만들기 퍼널의 첫 단계. 폼까지 와서 만들지 않고 떠나는 사람을 본다.
  useEffect(() => {
    track("Room Create Started", {});
  }, []);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    mutate(formValues, { onSuccess });
  };

  const errorMessage =
    error instanceof ApiError ? error.message : error ? "방을 만들지 못했습니다." : undefined;

  return (
    <Form onSubmit={handleSubmit}>
      <Input
        label="방 제목"
        name="name"
        value={formValues.name}
        maxLength={12}
        placeholder="예) 제주 여행"
        onValueChange={updateField("name")}
      />

      <Input
        label="내 이름"
        name="nickname"
        value={formValues.nickname}
        maxLength={12}
        placeholder="예) 민수"
        onValueChange={updateField("nickname")}
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
          onValueChange={(value) => updateField("uploadPolicy")(value as "everyone" | "host")}
        />

        <Slider
          label="방 만료 기간"
          name="expiryDays"
          value={formValues.expiryDays}
          min={MIN_EXPIRY_DAYS}
          max={MAX_EXPIRY_DAYS}
          formatValue={(days) => `${days}일`}
          marks={EXPIRY_DAY_MARKS}
          onValueChange={updateField("expiryDays")}
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
