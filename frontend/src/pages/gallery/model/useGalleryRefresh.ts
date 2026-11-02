import { useQueryClient } from "@tanstack/react-query";

import { photosQueryKey } from "@/entities/media";
import { roomQueryKey } from "@/entities/room";

interface UseGalleryRefreshParams {
  roomCode: string;
  roomId: number;
  userId: number;
}

/**
 * 방 정보, 사진 목록을 다시 불러온다.
 *
 * 내 동작은 성공 처리와, 서버가 나에게도 돌려보내는 SSE 이벤트 양쪽에서 갱신을 부른다.
 * 기본값(`cancelRefetch: true`)이면 뒤에 온 쪽이 앞 요청을 취소하고 새로 보내, 서버는
 * 같은 조회를 두 번 처리한다. 진행 중인 요청이 있으면 그 결과를 같이 기다린다.
 */
export const useGalleryRefresh = ({ roomCode, roomId, userId }: UseGalleryRefreshParams) => {
  const queryClient = useQueryClient();

  const refreshRoom = () =>
    queryClient.invalidateQueries(
      { queryKey: roomQueryKey(roomCode, userId), exact: true },
      { cancelRefetch: false },
    );

  const refreshPhotos = () =>
    queryClient.invalidateQueries(
      { queryKey: photosQueryKey(roomId, userId), exact: true },
      { cancelRefetch: false },
    );

  return { refreshRoom, refreshPhotos };
};
