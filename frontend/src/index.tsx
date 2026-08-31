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

  try {
    await startWorker();
  } catch (error) {
    // 목은 개발 편의일 뿐이라 실패해도 화면은 떠야 한다.
    // 여기서 던지면 아래 render 가 실행되지 않아 앱이 통째로 백지가 된다.
    console.error("[MSW] 목을 띄우지 못했습니다. 실서버 응답으로 계속합니다.", error);
  }
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
