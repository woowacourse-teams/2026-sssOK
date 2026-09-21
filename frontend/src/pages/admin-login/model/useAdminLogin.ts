import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { loginAdmin, saveAdminSession } from "@/entities/admin-session";
import { isApiError } from "@/shared/api";
import { ROUTES } from "@/shared/config";
import { useRetryCountdown } from "./useRetryCountdown";

const LOCK_NOTE =
  "로그인 실패가 여러 번 반복돼 잠시 막았어요. 시간이 지나면 다시 시도할 수 있어요.";

export const useAdminLogin = () => {
  const navigate = useNavigate();
  const { remainingSeconds, isCountingDown, start, clear } = useRetryCountdown();

  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [isPending, setIsPending] = useState(false);

  const isFilled = loginId.trim() !== "" && password !== "";
  const canSubmit = isFilled && !isPending && !isCountingDown;

  const submit = async () => {
    if (!canSubmit) return;

    setIsPending(true);
    setErrorMessage("");
    clear();

    try {
      const session = await loginAdmin({ loginId, password });

      saveAdminSession(session);
      navigate(ROUTES.adminFeedbacks, { replace: true });
    } catch (error) {
      if (!isApiError(error)) {
        setErrorMessage("로그인하지 못했어요. 잠시 후 다시 시도해주세요.");
        return;
      }

      if (error.status === 429) {
        // 언제 풀리는지 알면 카운트다운이 그 자체로 안내가 된다.
        // 모르면 안내할 말이 없으니 서버 문구라도 그대로 보여주고 버튼은 다시 열어 둔다.
        if (!start(error.retryAfterSeconds)) setErrorMessage(error.message);
        return;
      }

      // 401 은 아이디가 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다.
      // 서버 문구를 그대로 쓴다 — 프론트가 다시 쓰면 둘이 어긋난다.
      setErrorMessage(error.message);
    } finally {
      // 네트워크가 끊겨도 버튼이 로딩 상태에 갇히지 않게 항상 되돌린다.
      setIsPending(false);
    }
  };

  return {
    loginId,
    password,
    errorMessage,
    /** 잠긴 동안에만 보여준다 — 따로 들고 있으면 카운트다운이 끝나도 문구가 남는다. */
    lockNote: isCountingDown ? LOCK_NOTE : "",
    isPending,
    canSubmit,
    isCountingDown,
    remainingSeconds,
    changeLoginId: (value: string) => {
      setLoginId(value);
      setErrorMessage("");
    },
    changePassword: (value: string) => {
      setPassword(value);
      setErrorMessage("");
    },
    submit,
  };
};
