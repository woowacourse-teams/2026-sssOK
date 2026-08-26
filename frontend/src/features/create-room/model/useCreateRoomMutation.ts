import { useMutation } from "@tanstack/react-query";

import { createAnonymous, saveRoomSession } from "@/entities/session";
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
  });
