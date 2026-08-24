import { createRoot } from "react-dom/client";

import { App } from "@/app/App";
import { GlobalStyles } from "@/shared/styles/GlobalStyles";

async function enableMocking() {
  if (process.env.NODE_ENV !== "development") {
    return;
  }
  const { worker } = await import("@/mocks/browser");
  await worker.start();
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
