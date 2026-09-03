import { useEffect, useState } from "react";
import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { MOCK_ROOM_ID, mediaOfRoom } from "@/mocks/handlers/room";
import { API_BASE_URL } from "@/shared/config";
import { Button } from "@/shared/ui/button";
import { SelectionDownloadBar } from "./SelectionDownloadBar";
import type { DownloadTarget } from "../model/types";

const ROOM_CODE = "7K93QX2S";
/** 목이 인정하는 형식의 토큰이다 (`mock-token-<memberId>`). */
const TOKEN = "mock-token-10234";

const targetOf = (mediaId: number): DownloadTarget => ({
  mediaId,
  fileName: `IMG_${mediaId}.jpg`,
  size: 3 * 1024 * 1024,
  mimeType: "image/jpeg",
});

const meta = {
  title: "features/download-media/SelectionDownloadBar",
  component: SelectionDownloadBar,
  parameters: {
    // 화면 하단에 고정되는 바다. 캔버스를 꽉 채워야 실제로 앉는 자리가 보인다.
    layout: "fullscreen",
  },
} satisfies Meta<typeof SelectionDownloadBar>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * 11 · 골라서 받기 — 한 장 골랐을 때.
 *
 * 다운로드를 누르면 방식 시트가 열린다. 거기서 다운로드까지 누르면 목을 상대로
 * 실제 받기가 시작하는데, 이 스토리의 방·토큰은 목에 입장한 적이 없어 실패 모달로 끝난다.
 * 끝까지 굴러가는 판은 아래 `LiveRun` 이다.
 */
export const Default: Story = {
  args: {
    targets: [targetOf(5000)],
    roomId: MOCK_ROOM_ID,
    roomCode: ROOM_CODE,
    token: TOKEN,
    onClearSelection: () => {},
  },
};

/** 여러 장 골랐을 때. 장수가 세 자리가 되어도 바가 한 줄에 들어가야 한다 */
export const ManySelected: Story = {
  args: {
    targets: Array.from({ length: 132 }, (_, index) => targetOf(5000 + index)),
    roomId: MOCK_ROOM_ID,
    roomCode: ROOM_CODE,
    token: TOKEN,
    onClearSelection: () => {},
  },
};

/** 아무것도 안 골랐으면 바 자체를 그리지 않는다 — 평소에 화면을 차지하지 않기 위해서다 */
export const NothingSelected: Story = {
  args: {
    targets: [],
    roomId: MOCK_ROOM_ID,
    roomCode: ROOM_CODE,
    token: TOKEN,
    onClearSelection: () => {},
  },
};

/**
 * 받는 중·압축 중·사진첩 준비됨 상태는 **props 로 만들 수 없다.** 받기 한 판을 굴리는
 * 훅(`useMediaDownload`)이 안에서 들고 있는 상태라, 실제로 받아봐야 나온다.
 *
 * 그래서 여기서는 목(MSW)에 먼저 입장한 뒤 진짜 미디어를 대상으로 넘긴다.
 * 목이 서버 워커 역할을 그대로 해서 — 원본을 받아 zip 을 조립하고 그동안 진행률이 오른다 —
 * 시트부터 진행 바, 저장까지가 화면에서 그대로 굴러간다.
 */
/** 목이 들고 있는 진짜 미디어에서 받을 대상만 추린다. */
const mockTargets = (): DownloadTarget[] =>
  mediaOfRoom(MOCK_ROOM_ID)
    .slice(0, 3)
    .map(({ mediaId, fileName, size, mimeType }) => ({ mediaId, fileName, size, mimeType }));

const LiveDownload = () => {
  const [isJoined, setJoined] = useState(false);
  const [targets, setTargets] = useState<DownloadTarget[]>([]);

  useEffect(() => {
    const join = async () => {
      // 받기 API 는 입장한 방에서만 열린다. 목도 같은 검사를 한다.
      await fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
        method: "POST",
        headers: { Authorization: `Bearer ${TOKEN}` },
      });

      setTargets(mockTargets());
      setJoined(true);
    };

    void join();
  }, []);

  if (!isJoined) {
    return <p>목에 입장하는 중...</p>;
  }

  if (targets.length === 0) {
    return <Button onClick={() => setTargets(mockTargets())}>다시 고르기</Button>;
  }

  return (
    <SelectionDownloadBar
      targets={targets}
      roomId={MOCK_ROOM_ID}
      roomCode={ROOM_CODE}
      token={TOKEN}
      onClearSelection={() => setTargets([])}
    />
  );
};

/** 11 · 골라서 받기 — 시트부터 진행 바, 저장까지 한 판 (실제로 파일이 받아집니다) */
export const LiveRun: Story = {
  args: {
    targets: [],
    roomId: MOCK_ROOM_ID,
    roomCode: ROOM_CODE,
    token: TOKEN,
    onClearSelection: () => {},
  },
  render: () => <LiveDownload />,
};
