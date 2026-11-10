import { createPortal } from "react-dom";
import { HiCheckCircle, HiExclamationCircle, HiXMark } from "react-icons/hi2";

import { CloseButton, DEFAULT_TOAST_BOTTOM, Dock, Frame, StatusIcon, Text } from "./Toast.styles";

export interface ToastProps {
  container?: Element | null;
  message: string;
  tone?: "success" | "error";
  /** 닫기 버튼을 눌렀을 때. 저절로 닫히기 전에 치우고 싶은 사람을 위해 모든 알림에 버튼을 둔다. */
  onClose: () => void;
  /** 화면 아래에서 띄울 거리(CSS 값). 하단에 버튼이 있는 화면은 버튼 위로 올려 겹치지 않게 한다. */
  bottom?: string;
}

/** 공통 토스트 틀과 표시 위치. 자동 닫힘 시점은 ToastProvider 가 관리한다. */
export const Toast = ({
  message,
  tone = "success",
  onClose,
  container,
  bottom = DEFAULT_TOAST_BOTTOM,
}: ToastProps) => {
  return createPortal(
    <Dock $bottom={bottom}>
      <Frame>
        <StatusIcon $tone={tone} aria-hidden="true">
          {tone === "error" ? <HiExclamationCircle /> : <HiCheckCircle />}
        </StatusIcon>
        <Text role={tone === "error" ? "alert" : "status"}>{message}</Text>
        <CloseButton type="button" onClick={onClose} aria-label="알림 닫기">
          <HiXMark aria-hidden="true" />
        </CloseButton>
      </Frame>
    </Dock>,
    container ?? document.body,
  );
};
