import { render as renderRTL, type RenderOptions } from "@testing-library/react";
import type { ReactElement } from "react";

import { ToastProvider } from "@/shared/ui/toast";

export const render = (ui: ReactElement, options: RenderOptions = {}) => {
  const Wrapper = options.wrapper;
  return renderRTL(ui, {
    ...options,
    wrapper: ({ children }) => (
      <ToastProvider>{Wrapper ? <Wrapper>{children}</Wrapper> : children}</ToastProvider>
    ),
  });
};
