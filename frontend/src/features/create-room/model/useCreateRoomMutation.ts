import { useMutation, useQueryClient } from "@tanstack/react-query";

import { memberSessionQueryOptions } from "@/entities/member-session";
import { createAnonymous, saveRoomSession } from "@/entities/session";
import { track } from "@/shared/lib";
import { createRoom } from "../api/createRoom";
import type { CreateRoomResponse } from "../api/types";
import type { CreateRoomFormValues } from "./createRoomForm";

export const useCreateRoomMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (values: CreateRoomFormValues): Promise<CreateRoomResponse> => {
      const member = await queryClient.fetchQuery(memberSessionQueryOptions());
      const session =
        member ??
        (await createAnonymous({
          nickname: values.nickname,
        }));

      const room = await createRoom(
        {
          nickname: values.nickname,
          name: values.name,
          uploadPolicy: values.uploadPolicy,
        },
        session.accessToken,
      );

      if (!member) saveRoomSession(room.code, session);

      return room;
    },
    onSuccess: (room, values) => {
      void queryClient.invalidateQueries({ queryKey: ["myRooms"] });
      track("Room Created", {
        room_code: room.code,
        upload_policy: values.uploadPolicy,
      });
    },
  });
};
