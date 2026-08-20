import { createRoot } from "react-dom/client";
import { App } from "@/app/App";
import { GlobalStyles } from "@/shared/styles/GlobalStyles";

createRoot(document.getElementById("root")!).render(
  <>
    <GlobalStyles />
    <App />
  </>,
);
