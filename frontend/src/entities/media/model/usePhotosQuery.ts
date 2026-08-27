import { useQuery } from "@tanstack/react-query";

import { getPhotos } from "../api/getPhotos";

interface UsePhotosQueryParams {
  roomId: number;
  token: string;
  userId: number;
}

/**
 * 목록을 다시 불러와야 하는 쪽(업로드 직후 등)도 같은 키를 써야 한다.
 * 키를 손으로 다시 적으면 한쪽만 바뀌었을 때 갱신이 조용히 안 먹는다.
 */
export const photosQueryKey = (roomId: number, userId: number) => ["photos", roomId, userId];

export const usePhotosQuery = ({ roomId, token, userId }: UsePhotosQueryParams) => {
  // userId로 사용자별 캐시를 나누므로 인증 정보인 token은 queryKey에 노출하지 않는다.
  // eslint-disable-next-line @tanstack/query/exhaustive-deps
  return useQuery({
    queryKey: photosQueryKey(roomId, userId),
    queryFn: () => getPhotos({ roomId, token }),
  });
};
