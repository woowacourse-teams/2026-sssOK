import { createPortal } from "react-dom";
import { HiCheckCircle, HiExclamationCircle } from "react-icons/hi2";

import { CloseButton, Dock, Frame, StatusIcon, Text } from "./Toast.styles";

export interface ToastProps {
  message: string;
  tone?: "success" | "error";
  onClose: () => void;
  /**
   * 닫기 버튼을 그릴지. 기본은 그린다.
   *
   * 잠깐 보여주고 스스로 사라지는 알림이면 끄는 쪽이 낫다. 곧 사라질 것에 버튼을 두면
   * 눌러야 하는 줄 알고 손이 간다. 그때는 부르는 쪽이 정한 시간 뒤에 `onClose` 로 닫는다.
   */
  showClose?: boolean;
}

/** 공통 토스트 틀과 표시 위치. 자동 닫힘 시점은 사용하는 쪽에서 관리한다. */
export const Toast = ({ message, tone = "success", onClose, showClose = true }: ToastProps) => {
  return createPortal(
    <Dock>
      <Frame>
        <StatusIcon $tone={tone} aria-hidden="true">
          {tone === "error" ? <HiExclamationCircle /> : <HiCheckCircle />}
        </StatusIcon>
        <Text role={tone === "error" ? "alert" : "status"}>{message}</Text>
        {showClose && (
          <CloseButton type="button" onClick={onClose} aria-label="알림 닫기">
            닫기
          </CloseButton>
        )}
      </Frame>
    </Dock>,
    document.body,
  );
};
