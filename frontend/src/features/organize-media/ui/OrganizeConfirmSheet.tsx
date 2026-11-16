import type { ReactNode } from "react";

import { BottomSheet } from "@/shared/ui/bottom-sheet";

import avatar1 from "../assets/person-avatar-1.svg";
import avatar2 from "../assets/person-avatar-2.svg";
import avatar3 from "../assets/person-avatar-3.svg";
import { Actions, Avatar, Crowd, Later, Submit } from "./OrganizeSheet.styles";

interface OrganizeConfirmSheetProps {
  /** 로그인하지 않은 사람. 한 번만 체험할 수 있다는 걸 시작 전에 알린다. */
  isGuest: boolean;
  isStarting?: boolean;
  /** 지금 시작할 수 없는 까닭. 있으면 안내 문구 자리에 보이고 정리 버튼을 막는다. */
  blockedReason?: string | null;
  /** 기다리면 풀리는 막힘(정리·사진 처리 중)일 때 버튼에 시작 문구 대신 적을 말. */
  busyLabel?: string | null;
  onStart: () => void;
  onClose: () => void;
}

/** 제목 오른쪽의 겹친 얼굴 세 개(피그마 814:2727). 분석 전이라 방 사진 대신 예시 그림을 쓴다. */
const PeopleIllustration = () => (
  <Crowd aria-hidden="true">
    {[avatar1, avatar2, avatar3].map((src, order) => (
      // 순서가 바뀌지 않는 고정 그림이다. 테스트에서는 세 그림 경로가 같아 순서로 구분한다.
      <Avatar key={order}>
        <img src={src} alt="" width={30.8235} height={30.8235} />
      </Avatar>
    ))}
  </Crowd>
);

/**
 * 로그인하지 않은 사람은 한 번만 체험할 수 있다. 시작 전에 제목 아래 안내 자리에서 알린다.
 * 한도 모달·오류 문구처럼 웹과 앱 모두 로그인을 권한다.
 * 좁은 화면에서 "수 / 있어요" 처럼 어색하게 끊기지 않도록 조건 뒤에서 줄을 바꾼다.
 */
const GUEST_NOTICE: ReactNode = (
  <>
    로그인하지 않으면 <br />
    AI 정리는 한 번만 체험할 수 있어요.
  </>
);

/**
 * 방 헤더의 ✨ 버튼을 누르면 뜨는 사람별 정리 확인 시트
 * (피그마 AI-1 · 558:5390, 다른 사람이 정리 중이면 AI-1b · 817:3084).
 */
export const OrganizeConfirmSheet = ({
  isGuest,
  isStarting = false,
  blockedReason = null,
  busyLabel = null,
  onStart,
  onClose,
}: OrganizeConfirmSheetProps) => (
  <BottomSheet
    title="사진을 사람별로 모아드릴까요?"
    description={blockedReason ?? (isGuest ? GUEST_NOTICE : "같은 사람의 사진끼리 모아 드려요")}
    aside={<PeopleIllustration />}
    variant="action"
    onClose={onClose}
  >
    {(close) => (
      <Actions>
        <Submit type="button" disabled={isStarting || blockedReason !== null} onClick={onStart}>
          {busyLabel ?? "얼굴 분석하고 정리하기"}
        </Submit>
        <Later type="button" onClick={close}>
          다음에 할게요
        </Later>
      </Actions>
    )}
  </BottomSheet>
);
