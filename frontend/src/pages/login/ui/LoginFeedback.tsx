import type { ReactNode } from "react";

import { Notice } from "./LoginPage.styles";

interface LoginFeedbackProps {
  error: string;
  children: ReactNode;
}

export const LoginFeedback = ({ error, children }: LoginFeedbackProps) => (
  <>
    <Notice role={error ? "alert" : undefined} aria-hidden={error ? undefined : true}>
      {error}
    </Notice>
    {children}
  </>
);
