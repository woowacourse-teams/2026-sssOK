import { useMutation } from "@tanstack/react-query";

import { createAnonymous, saveRoomSession } from "@/entities/session";
import { track } from "@/shared/lib";
import { createRoom } from "../api/createRoom";
import type { CreateRoomResponse } from "../api/types";
import type { CreateRoomFormValues } from "./createRoomForm";

export const useCreateRoomMutation = () =>
  useMutation({
    mutationFn: async (values: CreateRoomFormValues): Promise<CreateRoomResponse> => {
      const session = await createAnonymous({
        nickname: values.nickname,
      });

      const room = await createRoom(
        {
          name: values.name,
          uploadPolicy: values.uploadPolicy,
          expiryHours: values.expiryHours === "72" ? 72 : 24,
        },
        session.accessToken,
      );

      saveRoomSession(room.code, session);

      return room;
    },
    onSuccess: (room, values) => {
      track("Room Created", {
        room_code: room.code,
        expiry_hours: values.expiryHours === "72" ? 72 : 24,
        upload_policy: values.uploadPolicy,
      });
    },
  });
