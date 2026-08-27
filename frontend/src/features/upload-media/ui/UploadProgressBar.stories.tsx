import { useEffect, useRef, useState } from "react";
import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { Button } from "@/shared/ui/button";
import type { UploadProgressState } from "../model/uploadProgress";
import {
  snapshotOf,
  startUploadProgress,
  withProgress,
  withTargets,
  withUploaded,
} from "../model/uploadProgress";
import { UploadProgressBar } from "./UploadProgressBar";

const meta = {
  title: "features/upload-media/UploadProgressBar",
  component: UploadProgressBar,
  parameters: {
    // 화면 하단에 고정되는 바다. 캔버스를 꽉 채워야 실제로 앉는 자리가 보인다.
    layout: "fullscreen",
  },
} satisfies Meta<typeof UploadProgressBar>;

export default meta;
type Story = StoryObj<typeof meta>;

/** 03 · 업로드 진행 — 막 시작해 아직 한 장도 못 올린 상태 */
export const Starting: Story = {
  args: {
    completedCount: 0,
    totalCount: 24,
    percent: 0,
    onCancel: () => {},
  },
};

/** 03 · 업로드 진행 — 절반쯤 올라간 상태 */
export const Uploading: Story = {
  args: {
    completedCount: 20,
    totalCount: 24,
    percent: 62,
    onCancel: () => {},
  },
};

/**
 * 03 · 업로드 진행 — 큰 영상 한 장만 남은 상태.
 *
 * 장수는 2/3 인데 퍼센트는 2% 다. 어긋난 게 아니라, 장수는 파일을 세고
 * 퍼센트는 바이트를 세기 때문이다 (#73 완료 조건).
 */
export const LargeVideoRemaining: Story = {
  args: {
    completedCount: 2,
    totalCount: 3,
    percent: 2,
    onCancel: () => {},
  },
};

const MB = 1024 * 1024;
const TICK_MS = 100;
const BYTES_PER_TICK = 2 * MB;

const TARGETS = [
  { mediaId: 1, fileName: "사진1.jpg", size: 3 * MB },
  { mediaId: 2, fileName: "사진2.jpg", size: 5 * MB },
  { mediaId: 3, fileName: "영상.mp4", size: 92 * MB },
];

const initialState = () => withTargets(startUploadProgress([]), TARGETS);

/**
 * 실제 진행을 흉내 내 바가 차오르는 것을 눈으로 본다.
 *
 * 목(MSW)은 즉시 응답해서 진행률 이벤트가 단계적으로 오지 않는다. 그래서 여기서는
 * 화면이 쓰는 것과 **같은 계산 함수**(`withProgress`·`withUploaded`)에 바이트를 흘려
 * 채움 애니메이션·자동 사라짐·취소를 확인한다.
 */
const UploadSimulation = () => {
  const [state, setState] = useState<UploadProgressState | null>(initialState);
  const cursor = useRef({ index: 0, loaded: 0 });

  const isRunning = state !== null;

  useEffect(() => {
    if (!isRunning) {
      return;
    }

    const timer = setInterval(() => {
      const { index, loaded } = cursor.current;
      const target = TARGETS[index];

      // 마지막 장까지 끝났다. 부르는 쪽이 바를 치우는 것으로 업로드가 끝난다.
      if (target === undefined) {
        setState(null);

        return;
      }

      const next = loaded + BYTES_PER_TICK;

      if (next >= target.size) {
        cursor.current = { index: index + 1, loaded: 0 };
        setState((current) => (current === null ? current : withUploaded(current, target)));

        return;
      }

      cursor.current = { index, loaded: next };
      setState((current) =>
        current === null
          ? current
          : withProgress(current, {
              mediaId: target.mediaId,
              fileName: target.fileName,
              loaded: next,
              total: target.size,
            }),
      );
    }, TICK_MS);

    return () => clearInterval(timer);
  }, [isRunning]);

  const restart = () => {
    cursor.current = { index: 0, loaded: 0 };
    setState(initialState());
  };

  if (state === null) {
    return <Button onClick={restart}>다시 올리기</Button>;
  }

  return <UploadProgressBar {...snapshotOf(state)} onCancel={() => setState(null)} />;
};

/** 03 · 업로드 진행 — 차오르는 것부터 사라지는 것까지 (취소를 눌러보세요) */
export const Simulation: Story = {
  args: {
    completedCount: 0,
    totalCount: 0,
    percent: 0,
    onCancel: () => {},
  },
  render: () => <UploadSimulation />,
};
