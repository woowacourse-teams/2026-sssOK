import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { DownloadModeSheet } from "./DownloadModeSheet";

/**
 * 첫 항목이 기기에 따라 갈리는데(`prefersShareSheet` 참고), 스토리북은 데스크톱
 * 브라우저에서 열려서 폰 쪽 분기를 볼 방법이 없다. 그래서 그 함수가 보는 두 신호 —
 * **파일 공유 지원 여부와 터치 입력 여부** — 만 흉내 내 폰인 척하게 만든다.
 *
 * 컴포넌트가 렌더 중에 물어보므로 스토리가 그려지기 **전에** 갈아끼워야 하고,
 * 다른 스토리로 넘어가면 원래대로 돌려놓아야 한다 — 전역을 건드리는 일이라 이 파일
 * 밖으로 새면 안 된다.
 */
let restorePhone: (() => void) | null = null;

const pretendToBePhone = () => {
  // 이미 갈아끼운 상태다. 두 번 끼우면 되돌릴 원본을 잃는다.
  if (restorePhone !== null) {
    return;
  }

  const originalCanShare = navigator.canShare;
  const originalMatchMedia = window.matchMedia;

  navigator.canShare = () => true;
  window.matchMedia = ((query: string) =>
    query.includes("pointer: coarse")
      ? // 우리 코드가 `matches` 만 보는 질의다. 나머지 질의는 원래 것에 그대로 넘긴다.
        ({
          matches: true,
          media: query,
          onchange: null,
          addEventListener: () => {},
          removeEventListener: () => {},
          addListener: () => {},
          removeListener: () => {},
          dispatchEvent: () => false,
        } as unknown as MediaQueryList)
      : originalMatchMedia.call(window, query)) as typeof window.matchMedia;

  restorePhone = () => {
    navigator.canShare = originalCanShare;
    window.matchMedia = originalMatchMedia;
    restorePhone = null;
  };
};

const AsPhone = ({ children }: { children: ReactNode }) => {
  // 초기화 함수는 자식이 그려지기 전에 돈다. 시트가 물어볼 때는 이미 폰이다.
  useState(pretendToBePhone);
  useEffect(() => () => restorePhone?.(), []);

  return <>{children}</>;
};

const meta = {
  title: "features/download-media/DownloadModeSheet",
  component: DownloadModeSheet,
  parameters: {
    // 오버레이가 화면을 덮는 시트다. 캔버스를 꽉 채워야 실제 비율이 보인다.
    layout: "fullscreen",
  },
} satisfies Meta<typeof DownloadModeSheet>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * 14 · 받는 방식 고르기 (데스크톱) — 첫 항목이 개별 저장이다.
 *
 * 고르는 것과 시작하는 것이 나뉘어 있다. 항목을 눌러도 선택만 바뀌고,
 * 받기는 아래 버튼을 눌러야 시작한다.
 */
export const Desktop: Story = {
  args: {
    count: 7,
    roomCode: "7K93QX2S",
    onSubmit: () => {},
    onClose: () => {},
  },
};

/**
 * 14 · 받는 방식 고르기 (폰) — 첫 항목이 사진첩 저장으로 바뀌고 안내 문구도 따라 바뀐다.
 *
 * 폰에서 낱장 저장은 첫 장만 받아져서 그 자리를 공유 시트가 대신한다.
 * 실기기 없이는 볼 수 없는 분기라, 여기서만 기기 신호를 흉내 낸다.
 */
export const Phone: Story = {
  args: {
    count: 7,
    roomCode: "7K93QX2S",
    onSubmit: () => {},
    onClose: () => {},
  },
  decorators: [
    (Story) => (
      <AsPhone>
        <Story />
      </AsPhone>
    ),
  ],
};

/** 한 장만 골랐을 때. 한 장이어도 zip 이 기본이다 */
export const SingleFile: Story = {
  args: {
    count: 1,
    roomCode: "7K93QX2S",
    onSubmit: () => {},
    onClose: () => {},
  },
};

/** 방 코드가 길어도 zip 파일명이 항목 안에서 잘리지 않아야 한다 */
export const LongRoomCode: Story = {
  args: {
    count: 132,
    roomCode: "WEDDING2026SPRING",
    onSubmit: () => {},
    onClose: () => {},
  },
};
