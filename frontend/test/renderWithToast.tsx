import type { RenderResult } from "@testing-library/react";
import { render as renderRTL, type RenderOptions } from "@testing-library/react";
import type { ReactElement } from "react";

import { ToastProvider } from "@/shared/ui/toast";

export const render = (
  ui: ReactElement,
  options: RenderOptions = {},
): RenderResult<typeof import("@testing-library/dom/types/queries"), HTMLElement, HTMLElement> => {
  const Wrapper = options.wrapper;
  return renderRTL(ui, {
    ...options,
    wrapper: ({ children }) => (
      <ToastProvider>{Wrapper ? <Wrapper>{children}</Wrapper> : children}</ToastProvider>
    ),
  });
};
