import { createRoot } from "react-dom/client";

import { App } from "@/app/App";
import { MOCK_MODE } from "@/shared/config";
import { GlobalStyles } from "@/shared/styles/GlobalStyles";

async function enableMocking() {
  // 배포 빌드에는 목이 아예 없다. 이 비교는 빌드 시점에 상수로 접혀서
  // 아래 동적 import 가 죽은 코드가 되고, 목 번들이 딸려가지 않는다.
  if (process.env.NODE_ENV !== "development" || MOCK_MODE === "off") {
    return;
  }
  const { startWorker } = await import("@/mocks/browser");
  await startWorker(MOCK_MODE);
}

async function bootstrap() {
  await enableMocking();

  createRoot(document.getElementById("root")!).render(
    <>
      <GlobalStyles />
      <App />
    </>,
  );
}

bootstrap();
