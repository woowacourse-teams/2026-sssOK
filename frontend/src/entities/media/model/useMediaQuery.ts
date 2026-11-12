import type { UseQueryResult } from "@tanstack/react-query";
import { useQuery } from "@tanstack/react-query";

import { getMedia } from "../api/getMedia";

export const mediaQueryKey = (
  roomId: number,
  mediaId: number,
  userId: number,
): readonly ["media", number, number, number] => ["media", roomId, mediaId, userId] as const;

export const useMediaQuery = ({
  roomId,
  mediaId,
  userId,
  token,
}: {
  roomId: number;
  mediaId: number;
  userId: number;
  token: string;
}): UseQueryResult<
  NoInfer<{
    type: "IMAGE" | "VIDEO";
    status: "RESERVED" | "PROCESSING" | "READY" | "FAILED";
    size: number;
    mediaId: number;
    fileName: string;
    mimeType: string;
    displayUrl: string | null;
    width: number | null;
    height: number | null;
    duration: number | null;
    folderIds: number[];
    uploaderId: number;
    uploaderName: string;
    uploadedAt: string;
    takenAt: string | null;
    location: { latitude: number; longitude: number; name: string } | null;
    canDelete: boolean;
    thumbnailUrlExpiresAt?: string | null | undefined;
    displayUrlExpiresAt?: string | null | undefined;
    likeCount?: number | undefined;
    likedByMe?: boolean | undefined;
  }>,
  Error
> => {
  // 사용자별 캐시로 분리하되 토큰 자체는 캐시에 기록하지 않는다.
  // eslint-disable-next-line @tanstack/query/exhaustive-deps -- 인증 토큰은 캐시 식별자가 아니며 세션 변경 시 별도로 캐시를 정리한다
  return useQuery({
    queryKey: mediaQueryKey(roomId, mediaId, userId),
    queryFn: () => getMedia({ roomId, mediaId, token }),
    retry: false,
  });
};
