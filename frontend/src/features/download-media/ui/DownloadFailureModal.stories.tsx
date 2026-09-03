import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { DownloadFailureModal } from "./DownloadFailureModal";

const meta = {
  title: "features/download-media/DownloadFailureModal",
  component: DownloadFailureModal,
  parameters: {
    // 오버레이가 화면을 덮는 모달이다. 캔버스를 꽉 채워야 실제 비율이 보인다.
    layout: "fullscreen",
  },
} satisfies Meta<typeof DownloadFailureModal>;

export default meta;
type Story = StoryObj<typeof meta>;

/** 13 · 다운로드 실패 — 여러 장 중 일부만 못 받았고, 기다렸다 다시 누르면 되는 실패 */
export const Default: Story = {
  args: {
    count: 2,
    message: "아직 처리 중이에요",
    isRetryable: true,
    onRetry: () => {},
    onClose: () => {},
  },
};

/**
 * 없는 사진이라 재시도가 빠진 상태.
 *
 * 버튼이 하나만 남는다. 다시 눌러도 결과가 같은 실패에 재시도를 내주면
 * 사용자가 같은 벽에 반복해서 부딪힌다 (`downloadErrorMessage` 참고).
 */
export const NotRetryable: Story = {
  args: {
    count: 1,
    message: "찾을 수 없어요",
    isRetryable: false,
    onRetry: () => {},
    onClose: () => {},
  },
};

/**
 * 판 전체가 무너진 경우 — 압축 잡을 만들지도 못해 셀 장수가 없다.
 *
 * `count` 가 0 이면 제목이 장수를 말하지 않는다.
 */
export const WholeRunFailed: Story = {
  args: {
    count: 0,
    message: "받는 중인 요청이 많아요",
    isRetryable: true,
    onRetry: () => {},
    onClose: () => {},
  },
};

/** 회선이 끊겨 고른 것이 통째로 깨진 경우. 세 자리 수에서도 제목이 한 줄에 들어가야 한다 */
export const ManyFiles: Story = {
  args: {
    count: 128,
    message: "네트워크 연결을 확인해주세요",
    isRetryable: true,
    onRetry: () => {},
    onClose: () => {},
  },
};
