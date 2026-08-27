import { useQuery } from "@tanstack/react-query";

import { getPhotos } from "../api/getPhotos";

interface UsePhotosQueryParams {
  roomId: number;
  token: string;
  userId: number;
}

export const usePhotosQuery = ({ roomId, token, userId }: UsePhotosQueryParams) => {
  // userId로 사용자별 캐시를 나누므로 인증 정보인 token은 queryKey에 노출하지 않는다.
  // eslint-disable-next-line @tanstack/query/exhaustive-deps
  return useQuery({
    queryKey: ["photos", roomId, userId],
    queryFn: () => getPhotos({ roomId, token }),
  });
};
