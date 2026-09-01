import { setupWorker } from "msw/browser";

import { handlers } from "./handlers";

/**
 * 목 워커를 띄운다. `off` 는 여기까지 오지 않는다 (`index.tsx` 가 먼저 끊는다).
 * 빠진 핸들러는 바로 알아야 해서, 처리되지 않은 요청에는 경고를 남긴다.
 */
export const startWorker = () => {
  const worker = setupWorker(...handlers);

  return worker.start({ onUnhandledRequest: "warn" });
};
