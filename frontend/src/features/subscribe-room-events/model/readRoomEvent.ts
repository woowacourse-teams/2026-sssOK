import type { z } from "zod";
import { captureException } from "@/shared/lib";

/** 잘못된 이벤트는 캐시에 전달하지 않고 본문·인증값은 기록하지 않는다. */
export function readRoomEvent<T>(schema: z.ZodType<T>, raw: string): T | null {
  try {
    const value: unknown = JSON.parse(raw);
    const result = schema.safeParse(value);
    if (result.success) return result.data;
  } catch {
    // JSON 문법 오류도 동일한 계약 오류로 처리한다.
  }
  captureException(new Error("실시간 이벤트 형식이 올바르지 않습니다."), {
    level: "warning",
    operation: "room_events.parse",
    code: "INVALID_EVENT",
  });
  return null;
}
