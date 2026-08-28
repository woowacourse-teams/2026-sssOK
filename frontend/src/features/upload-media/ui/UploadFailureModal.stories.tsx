import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { UploadFailureModal } from "./UploadFailureModal";
import type { FailedUpload, UploadFailureCode } from "../model/types";

/** 스토리에서 목록을 그리는 데 필요한 것만 채운다. */
const failureOf = (fileName: string, code: UploadFailureCode = "UPLOAD_FAILED"): FailedUpload => ({
  mediaId: 1,
  fileName,
  code,
  message: "",
  file: new File([], fileName),
});

const meta = {
  title: "features/upload-media/UploadFailureModal",
  component: UploadFailureModal,
  parameters: {
    // 오버레이가 화면을 덮는 모달이다. 캔버스를 꽉 채워야 실제 비율이 보인다.
    layout: "fullscreen",
  },
} satisfies Meta<typeof UploadFailureModal>;

export default meta;
type Story = StoryObj<typeof meta>;

/** 12 · 업로드 실패 — 시안 그대로의 상태 */
export const Default: Story = {
  args: {
    failures: [failureOf("IMG_3390.png"), failureOf("movie_02.mov", "MEDIA_NOT_FOUND")],
    onRetry: () => {},
    onClose: () => {},
  },
};

/** 한 장만 깨졌을 때. 한국어라 단수·복수로 문장이 갈리지 않는다 */
export const SingleFile: Story = {
  args: {
    failures: [failureOf("IMG_3390.png")],
    onRetry: () => {},
    onClose: () => {},
  },
};

/** 회선이 통째로 끊겨 고른 것이 거의 다 깨진 경우. 두 자리 수에서도 제목이 한 줄에 들어가야 한다 */
export const ManyFiles: Story = {
  args: {
    failures: Array.from({ length: 24 }, (_, index) => failureOf(`IMG_${3000 + index}.png`)),
    onRetry: () => {},
    onClose: () => {},
  },
};
