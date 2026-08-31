import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { SelectionDownloadBar } from "./SelectionDownloadBar";
import { prefersShareSheet } from "../lib/prefersShareSheet";
import type { DownloadTarget } from "../model/types";

jest.mock("../lib/saveBlob", () => ({ saveBlob: jest.fn() }));
jest.mock("../lib/prefersShareSheet", () => ({ prefersShareSheet: jest.fn(() => false) }));
jest.mock("../config", () => ({
  ...jest.requireActual("../config"),
  INDIVIDUAL_SAVE_GAP_MS: 0,
  POLL_INTERVAL_MS: 0,
}));

const TOKEN = "mock-token-10234";
const prefersShareSheetMock = prefersShareSheet as jest.MockedFunction<typeof prefersShareSheet>;

const targetOf = (mediaId: number): DownloadTarget => ({
  mediaId,
  fileName: `IMG_${mediaId}.jpg`,
  size: 100,
  mimeType: "image/jpeg",
});

const enterRoom = () =>
  fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
    method: "POST",
    headers: { Authorization: `Bearer ${TOKEN}` },
  });

const serveSingle = (status = 200) =>
  server.use(
    // 개별 저장은 서명 URL 을 먼저 한 번에 받는다. 목에서는 아래 GET 이 그 자리다.
    http.post(`${API_BASE_URL}/rooms/:roomId/downloads/batch`, async ({ request, params }) => {
      const { mediaIds } = (await request.json()) as { mediaIds: number[] };

      return HttpResponse.json({
        data: {
          files: mediaIds.map((mediaId) => ({
            mediaId,
            fileName: `IMG_${mediaId}.jpg`,
            downloadUrl: `${API_BASE_URL}/rooms/${params.roomId}/downloads/media/${mediaId}`,
            expiresAt: new Date(Date.now() + 300_000).toISOString(),
          })),
        },
      });
    }),

    http.get(`${API_BASE_URL}/rooms/:roomId/downloads/media/:mediaId`, () =>
      status === 200
        ? new HttpResponse(new Blob(["bytes"]), { headers: { "Content-Type": "image/jpeg" } })
        : HttpResponse.json({ code: "ERR", message: "" }, { status }),
    ),
  );

const renderBar = (targets: DownloadTarget[]) => {
  const onClearSelection = jest.fn();

  render(
    <SelectionDownloadBar
      targets={targets}
      roomId={MOCK_ROOM_ID}
      roomCode="7K93QX2S"
      token={TOKEN}
      onClearSelection={onClearSelection}
    />,
  );

  return { onClearSelection };
};

/** 바의 다운로드와 시트의 다운로드가 이름이 같다. 시트 안쪽으로 범위를 좁힌다. */
const sheet = () => within(screen.getByTestId("bottom-sheet-overlay"));

/** 시트를 열고 개별 저장을 골라 받기 한 판을 굴린다. */
const downloadIndividually = async () => {
  await userEvent.click(screen.getByRole("button", { name: /다운로드/ }));
  await userEvent.click(sheet().getByRole("radio", { name: /개별로 저장/ }));
  await userEvent.click(sheet().getByRole("button", { name: "다운로드" }));
};

beforeEach(enterRoom);

describe("SelectionDownloadBar", () => {
  // 평소에 화면을 차지하지 않아야 한다 (#120 완료 조건).
  it("아무것도 안 골랐으면 아무것도 그리지 않는다", () => {
    const { container } = render(
      <SelectionDownloadBar
        targets={[]}
        roomId={MOCK_ROOM_ID}
        roomCode="7K93QX2S"
        token={TOKEN}
        onClearSelection={jest.fn()}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("고른 장수를 보여준다", () => {
    renderBar([targetOf(5000)]);

    expect(screen.getByText("선택 1개")).toBeInTheDocument();
  });

  it("다운로드를 누르면 시트가 열린다", async () => {
    renderBar([targetOf(5000), targetOf(5001)]);

    await userEvent.click(screen.getByRole("button", { name: /다운로드/ }));

    expect(screen.getByText("2개를 어떻게 받을까요?")).toBeInTheDocument();
  });

  it("다 받으면 고른 상태를 풀어준다", async () => {
    serveSingle();

    const { onClearSelection } = renderBar([targetOf(5000)]);

    await downloadIndividually();

    await waitFor(() => expect(onClearSelection).toHaveBeenCalled());
  });

  /**
   * 실패가 섞였는데 선택을 풀면, 다시 시도할 대상을 사용자가 처음부터 다시 골라야 한다.
   */
  it("실패하면 사유를 알리고 고른 상태를 그대로 둔다", async () => {
    serveSingle(409);

    const { onClearSelection } = renderBar([targetOf(5000)]);

    await downloadIndividually();

    expect(await screen.findByText("아직 처리 중이에요")).toBeInTheDocument();
    expect(onClearSelection).not.toHaveBeenCalled();
  });

  it("없는 사진은 재시도를 내주지 않는다", async () => {
    serveSingle(404);

    renderBar([targetOf(5000)]);

    await downloadIndividually();

    expect(await screen.findByText("찾을 수 없어요")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "재시도" })).not.toBeInTheDocument();
  });

  it("폰에서는 시트가 사진첩 항목을 내준다", async () => {
    prefersShareSheetMock.mockReturnValue(true);

    renderBar([targetOf(5000)]);

    await userEvent.click(screen.getByRole("button", { name: /다운로드/ }));

    expect(screen.getByText("사진첩에 저장")).toBeInTheDocument();
  });
});
