import type { FormEvent } from "react";

import type { Room } from "@/entities/room";
import { ApiError } from "@/shared/api";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { RadioGroup } from "@/shared/ui/radio-group";
import { Stack } from "@/shared/ui/stack";
import type { UpdateRoomResponse } from "../api/types";
import { useUpdateRoomForm } from "../model/useUpdateRoomForm";
import { useUpdateRoomMutation } from "../model/useUpdateRoomMutation";
import { Form, SubmitArea, SubmitError } from "./UpdateRoomForm.styles";

interface UpdateRoomFormProps {
  room: Room;
  accessToken: string;
  onSuccess: (room: UpdateRoomResponse) => void;
}

export const UpdateRoomForm = ({ room, accessToken, onSuccess }: UpdateRoomFormProps) => {
  const { formValues, updateField, request, isValid, hasChanges } = useUpdateRoomForm(room);
  const updateRoomMutation = useUpdateRoomMutation({ roomId: room.roomId, accessToken });

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    updateRoomMutation.mutate(request, { onSuccess });
  };

  const errorMessage =
    updateRoomMutation.error instanceof ApiError
      ? updateRoomMutation.error.message
      : updateRoomMutation.isError
        ? "방 설정을 변경하지 못했어요."
        : undefined;

  return (
    <Form onSubmit={handleSubmit}>
      <Stack gap={16}>
        <Input
          label="방 이름"
          name="name"
          value={formValues.name}
          maxLength={12}
          placeholder="예) 제주 여행"
          errorMessage={!isValid ? "방 이름을 입력해주세요." : undefined}
          onValueChange={updateField("name")}
        />

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
          label="방 만료 시간 다시 설정"
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
        <Button
          size="lg"
          type="submit"
          disabled={!hasChanges || !isValid || updateRoomMutation.isPending}
        >
          {updateRoomMutation.isPending ? "저장하는 중..." : "변경 사항 저장"}
        </Button>
      </SubmitArea>
    </Form>
  );
};
