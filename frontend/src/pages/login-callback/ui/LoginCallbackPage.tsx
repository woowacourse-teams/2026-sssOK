import { Navigate } from "react-router-dom";

import { PageSpinner } from "@/shared/ui/spinner";
import { useCompleteLogin } from "../model/useCompleteLogin";
import { LoginCallbackError } from "./LoginCallbackError";

export const LoginCallbackPage = () => {
  const { isLoading, isError, error, returnTo } = useCompleteLogin();

  if (isLoading) return <PageSpinner label="로그인 완료 중" />;
  if (isError) return <LoginCallbackError message={error.message} />;

  return <Navigate to={returnTo} replace />;
};
