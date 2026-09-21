import { useEffect } from "react";

import { setAnalyticsRoom } from "@/shared/lib";

/**
 * 갤러리에 머무는 동안 보내는 이벤트에 방 코드와 방장 여부를 붙인다.
 * 핵심 지표(공유 성사 방)는 방 단위로, 게스트 활성 비율은 방장을 빼고 센다.
 */
export const useAnalyticsRoom = (roomCode: string, isHost: boolean) => {
  useEffect(() => {
    setAnalyticsRoom({ room_code: roomCode, role: isHost ? "host" : "guest" });
    return () => setAnalyticsRoom(null);
  }, [roomCode, isHost]);
};
