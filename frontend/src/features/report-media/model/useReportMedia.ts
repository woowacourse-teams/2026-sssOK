import type { UseMutationResult } from "@tanstack/react-query";
import { useMutation } from "@tanstack/react-query";
import { useParams } from "react-router-dom";

import { useRoomSession } from "@/entities/member-session";
import { reportMedia, type ReportReason } from "../api/reportMedia";

/** 현재 경로의 방 세션에서 인증 정보를 가져온다. */
export const useReportMedia = ({
  roomId,
  mediaId,
  onSuccess,
}: {
  roomId: number;
  mediaId: number;
  onSuccess: () => void;
}): UseMutationResult<Awaited<ReturnType<typeof reportMedia>>, Error, ReportReason> & {
  canReport: boolean;
  sessionError: string | null;
} => {
  const { code = "" } = useParams();
  const { session, isPending, isError } = useRoomSession(code);
  const mutation = useMutation({
    mutationFn: (reason: ReportReason) => {
      if (!session) throw new Error("로그인 정보를 확인하지 못했어요. 다시 입장해 주세요.");
      return reportMedia({ roomId, mediaId, token: session.accessToken, reason });
    },
    onSuccess,
    retry: false,
  });

  return {
    ...mutation,
    canReport: !isPending && !isError && session !== null,
    sessionError:
      isError || (!isPending && !session)
        ? "로그인 정보를 확인하지 못했어요. 다시 입장해 주세요."
        : null,
  };
};
