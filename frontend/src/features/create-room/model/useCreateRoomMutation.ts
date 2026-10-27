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
          nickname: values.nickname,
          name: values.name,
          uploadPolicy: values.uploadPolicy,
        },
        session.accessToken,
      );

      saveRoomSession(room.code, session);

      return room;
    },
    onSuccess: (room, values) => {
      track("Room Created", {
        room_code: room.code,
        upload_policy: values.uploadPolicy,
      });
    },
  });
