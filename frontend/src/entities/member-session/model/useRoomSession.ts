import { readValidRoomSession } from "@/entities/session";
import { useMemberSessionQuery } from "./useMemberSessionQuery";

/** 회원은 메모리의 공통 세션, 비회원은 방별 익명 세션을 사용한다. */
export const useRoomSession = (code: string) => {
  const member = useMemberSessionQuery();
  return {
    ...member,
    session:
      member.isPending || member.isError ? null : (member.data ?? readValidRoomSession(code)),
  };
};
