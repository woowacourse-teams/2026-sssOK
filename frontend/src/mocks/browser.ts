import { setupWorker } from "msw/browser";

import type { MockMode } from "@/shared/config";
import { handlers } from "./handlers";
import { hybridHandlers } from "./handlers/hybrid";

/**
 * 목 워커를 모드에 맞게 만든다. `off` 는 여기까지 오지 않는다 (`index.tsx` 가 먼저 끊는다).
 *
 * `hybrid` 는 여기 없는 경로를 전부 실서버로 흘려보내야 하므로 경고를 끈다 —
 * 그게 정상 동작이라 켜두면 콘솔이 요청마다 경고로 덮인다.
 * `full` 은 반대로 빠진 핸들러를 바로 알아야 해서 경고를 남긴다.
 */
export const startWorker = (mode: Exclude<MockMode, "off">) => {
  const worker = setupWorker(...(mode === "full" ? handlers : hybridHandlers));

  return worker.start({ onUnhandledRequest: mode === "full" ? "warn" : "bypass" });
};
