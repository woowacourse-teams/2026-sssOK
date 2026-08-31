import { createPortal } from "react-dom";
import { HiCheckCircle, HiExclamationCircle } from "react-icons/hi2";

import { CloseButton, Dock, Frame, StatusIcon, Text } from "./Toast.styles";

export interface ToastProps {
  message: string;
  tone?: "success" | "error";
  onClose: () => void;
}

/** 공통 토스트 틀과 표시 위치. 자동 닫힘 시점은 사용하는 쪽에서 관리한다. */
export const Toast = ({ message, tone = "success", onClose }: ToastProps) => {
  return createPortal(
    <Dock>
      <Frame>
        <StatusIcon $tone={tone} aria-hidden="true">
          {tone === "error" ? <HiExclamationCircle /> : <HiCheckCircle />}
        </StatusIcon>
        <Text role={tone === "error" ? "alert" : "status"}>{message}</Text>
        <CloseButton type="button" onClick={onClose} aria-label="알림 닫기">
          닫기
        </CloseButton>
      </Frame>
    </Dock>,
    document.body,
  );
};
