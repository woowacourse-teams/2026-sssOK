import { render, screen, waitFor, waitForElementToBeRemoved, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { setAnalyticsRoom, track } from "@/shared/lib/analytics";
import { SelectionDownloadBar } from "./SelectionDownloadBar";
import { prefersShareSheet } from "../lib/prefersShareSheet";
import { shareFiles, type ShareFilesResult } from "../lib/shareFiles";
import type { DownloadTarget } from "../model/types";

jest.mock("../lib/saveBlob", () => ({ saveBlob: jest.fn() }));
jest.mock("@/shared/lib/analytics", () => ({
  ...jest.requireActual("@/shared/lib/analytics"),
  track: jest.fn(),
}));
jest.mock("../lib/prefersShareSheet", () => ({ prefersShareSheet: jest.fn(() => false) }));
// jsdom 에는 공유 시트가 없다. 몇 장을 넘겼는지와, 시트가 어떻게 닫혔는지만 흉내 낸다.
jest.mock("../lib/shareFiles", () => ({ shareFiles: jest.fn() }));
jest.mock("../config", () => ({
  ...jest.requireActual("../config"),
  INDIVIDUAL_SAVE_GAP_MS: 0,
  POLL_INTERVAL_MS: 0,
  // 묶음 기준을 넘는 판을 몇 장만으로 만든다.
  SHARE_BATCH_SIZE: 2,
}));

const TOKEN = "mock-token-10234";
const prefersShareSheetMock = prefersShareSheet as jest.MockedFunction<typeof prefersShareSheet>;
const shareFilesMock = shareFiles as jest.MockedFunction<typeof shareFiles>;

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

/** 압축 잡을 만들자마자 다 묶인 것으로 답한다. 고른 장수가 그대로 zip 에 들어간다. */
const serveZip = () => {
  let mediaCount = 0;

  server.use(
    http.post(`${API_BASE_URL}/rooms/:roomId/downloads/zip`, async ({ request }) => {
      const { mediaIds } = (await request.json()) as { mediaIds: number[] };

      mediaCount = mediaIds.length;

      return HttpResponse.json(
        {
          data: {
            jobId: "job-1",
            status: "QUEUED",
            mediaCount,
            totalSize: 100,
            fileName: "ShareDrop_7K93QX2S.zip",
          },
        },
        { status: 202 },
      );
    }),

    http.get(`${API_BASE_URL}/rooms/:roomId/downloads/zip/:jobId`, () =>
      HttpResponse.json({
        data: {
          jobId: "job-1",
          status: "READY",
          progress: 100,
          mediaCount,
          fileName: "ShareDrop_7K93QX2S.zip",
          downloadUrl: `${API_BASE_URL}/zip/job-1.zip`,
          expiresAt: new Date(Date.now() + 300_000).toISOString(),
          failureReason: null,
        },
      }),
    ),

    http.get(`${API_BASE_URL}/zip/:fileName`, () => new HttpResponse(new Blob(["zip"]))),
  );
};

const renderBar = (targets: DownloadTarget[], isAllSelected = false) => {
  const onClearSelection = jest.fn();

  render(
    <SelectionDownloadBar
      targets={targets}
      roomId={MOCK_ROOM_ID}
      roomCode="7K93QX2S"
      roomPhotoCount={10}
      isAllSelected={isAllSelected}
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

/** 시트를 열고 zip 을 골라 받기 한 판을 굴린다. */
const downloadAsZip = async () => {
  await userEvent.click(screen.getByRole("button", { name: /다운로드/ }));
  await userEvent.click(sheet().getByRole("radio", { name: /zip 일괄 다운로드/ }));
  await userEvent.click(sheet().getByRole("button", { name: "다운로드" }));
};

/** 시트를 열고 사진첩 저장을 골라 받기 한 판을 굴린다. */
const downloadToPhotos = async () => {
  await userEvent.click(screen.getByRole("button", { name: /다운로드/ }));
  await userEvent.click(sheet().getByRole("radio", { name: /사진첩에 저장/ }));
  await userEvent.click(sheet().getByRole("button", { name: "다운로드" }));
};

/** 공유 시트가 떠 있는 상태를 붙잡아 두고, 원하는 때에 닫히게 한다. */
const holdShareSheet = () => {
  let close: (result: ShareFilesResult) => void = () => {};

  shareFilesMock.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        close = resolve;
      }),
  );

  return (result: ShareFilesResult) => close(result);
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
        roomPhotoCount={10}
        token={TOKEN}
        onClearSelection={jest.fn()}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("고른 장수를 보여준다", () => {
    renderBar([targetOf(5000)]);

    expect(screen.getByText("1개")).toBeInTheDocument();
  });

  it("왼쪽 체크 버튼을 누르면 선택을 모두 해제한다", async () => {
    const { onClearSelection } = renderBar([targetOf(5000)], true);

    await userEvent.click(screen.getByRole("button", { name: "선택 모두 해제" }));

    expect(onClearSelection).toHaveBeenCalledTimes(1);
  });

  it("다운로드를 누르면 시트가 열린다", async () => {
    renderBar([targetOf(5000), targetOf(5001)]);

    await userEvent.click(screen.getByRole("button", { name: /다운로드/ }));

    expect(screen.getByText("2개를 어떻게 받을까요?")).toBeInTheDocument();
  });

  /**
   * 숫자만 있는 바는 폰에서 거의 안 읽힌다. 업로드 바와 같은 자리에 같은 알약으로 뜨는데
   * 한쪽만 차오르면 받기가 멈춘 것으로 읽힌다 — 띠와 진행률 의미를 함께 내준다.
   */
  it("받는 동안 차오르는 띠를 진행률만큼 그린다", async () => {
    let release = () => {};
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });

    serveSingle();
    server.use(
      http.get(`${API_BASE_URL}/rooms/:roomId/downloads/media/:mediaId`, async () => {
        await held;

        return new HttpResponse(new Blob(["bytes"]), {
          headers: { "Content-Type": "image/jpeg" },
        });
      }),
    );

    renderBar([targetOf(5000)]);
    await downloadIndividually();

    const bar = await screen.findByRole("progressbar", { name: "다운로드 진행률" });

    expect(bar).toHaveAttribute("aria-valuenow", "0");
    // 띠는 알약 안에 있고, 너비가 곧 퍼센트다.
    expect(bar.firstElementChild).toHaveStyle({ width: "0%" });

    release();

    await waitFor(() => expect(screen.queryByRole("progressbar")).not.toBeInTheDocument());
  });

  it("다 받으면 고른 상태를 풀어준다", async () => {
    serveSingle();

    const { onClearSelection } = renderBar([targetOf(5000)]);

    await downloadIndividually();

    await waitFor(() => expect(onClearSelection).toHaveBeenCalled());
  });

  it("개별로 다 받으면 저장했다고 알린다", async () => {
    serveSingle();

    renderBar([targetOf(5000), targetOf(5001)]);

    await downloadIndividually();

    expect(await screen.findByText("2장을 저장했어요.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "닫기" })).not.toBeInTheDocument();
  });

  it("zip 으로 다 받으면 압축 파일로 저장했다고 알린다", async () => {
    serveZip();

    renderBar([targetOf(5000), targetOf(5001)]);

    await downloadAsZip();

    expect(await screen.findByText("2장을 zip 파일로 저장했어요.")).toBeInTheDocument();
  });

  it("못 받은 것이 섞이면 저장했다는 알림을 띄우지 않는다", async () => {
    serveSingle(409);

    renderBar([targetOf(5000)]);

    await downloadIndividually();

    expect(await screen.findByText("아직 처리 중이에요")).toBeInTheDocument();
    expect(screen.queryByText(/저장했어요/)).not.toBeInTheDocument();
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

  describe("다운로드 이벤트", () => {
    const ROOM_CONTEXT = { room_code: "7K93QX2S", role: "guest" } as const;

    // 앞 테스트가 공유 시트 쪽으로 돌려놨을 수 있다. 개별 저장이 보이는 데스크톱으로 되돌린다.
    beforeEach(() => {
      prefersShareSheetMock.mockReturnValue(false);
      setAnalyticsRoom(ROOM_CONTEXT);
    });

    afterEach(() => setAnalyticsRoom(null));

    it("전체 선택으로 고른 첫 판부터 전체 받기로 남긴다", async () => {
      serveSingle();
      renderBar([targetOf(5000), targetOf(5001)], true);

      await downloadIndividually();

      await waitFor(() =>
        expect(track).toHaveBeenCalledWith(
          "Photo Downloaded",
          {
            mode: "individual",
            source: "gallery",
            photo_count: 2,
            failed_count: 0,
            total_count: 10,
            is_select_all: true,
            duration_ms: expect.any(Number),
          },
          ROOM_CONTEXT,
        ),
      );
    });

    it("받기를 시작하면 끝나기 전에 시작한 판을 남긴다", async () => {
      serveSingle();
      renderBar([targetOf(5000), targetOf(5001)]);

      await downloadIndividually();

      expect(track).toHaveBeenCalledWith(
        "Photo Download Started",
        expect.objectContaining({ mode: "individual", source: "gallery", photo_count: 2 }),
        ROOM_CONTEXT,
      );
    });

    it("한 장도 못 받으면 이번에 고른 방식으로 실패를 남긴다", async () => {
      serveSingle(500);
      renderBar([targetOf(5000)]);

      await downloadIndividually();

      await waitFor(() =>
        expect(track).toHaveBeenCalledWith(
          "Photo Download Failed",
          expect.objectContaining({ mode: "individual", failed_count: 1 }),
          ROOM_CONTEXT,
        ),
      );
    });

    it("받는 도중 갤러리를 떠나도 받기를 시작한 방으로 남긴다", async () => {
      let release = () => {};
      const held = new Promise<void>((resolve) => {
        release = resolve;
      });
      serveSingle();
      server.use(
        http.get(`${API_BASE_URL}/rooms/:roomId/downloads/media/:mediaId`, async () => {
          await held;
          return new HttpResponse(new Blob(["bytes"]), {
            headers: { "Content-Type": "image/jpeg" },
          });
        }),
      );
      renderBar([targetOf(5000)]);

      await downloadIndividually();
      // 받기가 끝나기 전에 갤러리를 떠나 방 정보가 지워졌다
      setAnalyticsRoom(null);
      release();

      await waitFor(() =>
        expect(track).toHaveBeenCalledWith("Photo Downloaded", expect.anything(), ROOM_CONTEXT),
      );
    });
  });

  describe("사진첩에 저장 — 묶음으로 나눠 넘기기", () => {
    beforeEach(() => {
      prefersShareSheetMock.mockReturnValue(true);
      shareFilesMock.mockReset();
    });

    afterEach(() => prefersShareSheetMock.mockReturnValue(false));

    it("묶음 기준을 넘게 고르면 몇 번 나눠 저장하는지 미리 알린다", async () => {
      renderBar([targetOf(5000), targetOf(5001), targetOf(5002)]);

      await userEvent.click(screen.getByRole("button", { name: /다운로드/ }));
      await userEvent.click(sheet().getByRole("radio", { name: /사진첩에 저장/ }));

      expect(sheet().getByText("사진이 많아서 최대 2장씩 2번 나눠 저장해요")).toBeInTheDocument();
    });

    it("묶음 기준 이하면 나눠 저장한다고 알리지 않는다", async () => {
      renderBar([targetOf(5000), targetOf(5001)]);

      await userEvent.click(screen.getByRole("button", { name: /다운로드/ }));
      await userEvent.click(sheet().getByRole("radio", { name: /사진첩에 저장/ }));

      expect(sheet().queryByText(/나눠 저장해요/)).not.toBeInTheDocument();
    });

    it("묶음 기준 이하면 한 번에 넘기고, 끝나면 저장했다고 알린다", async () => {
      serveSingle();
      shareFilesMock.mockResolvedValue({ type: "shared" });
      const { onClearSelection } = renderBar([targetOf(5000), targetOf(5001)]);

      await downloadToPhotos();

      expect(await screen.findByText("2장을 사진첩에 저장했어요.")).toBeInTheDocument();
      expect(shareFilesMock).toHaveBeenCalledTimes(1);
      expect(shareFilesMock.mock.calls[0][0]).toHaveLength(2);
      expect(onClearSelection).toHaveBeenCalled();
    });

    it("저장했다는 알림은 닫기 버튼 없이 3초 뒤 스스로 사라진다", async () => {
      serveSingle();
      shareFilesMock.mockResolvedValue({ type: "shared" });
      renderBar([targetOf(5000)]);

      await downloadToPhotos();

      expect(await screen.findByText("1장을 사진첩에 저장했어요.")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "알림 닫기" })).not.toBeInTheDocument();

      await waitForElementToBeRemoved(() => screen.queryByText("1장을 사진첩에 저장했어요."), {
        timeout: 4000,
      });
    });

    it("탭할 때마다 앞 묶음 하나만 넘기고, 다 넘기면 한 번만 저장했다고 알린다", async () => {
      serveSingle();
      const { onClearSelection } = renderBar([targetOf(5000), targetOf(5001), targetOf(5002)]);

      await downloadToPhotos();

      expect(await screen.findByText("3장 준비됨")).toBeInTheDocument();
      // 기준을 넘는 판은 받은 뒤 곧장 시트를 열지 않는다
      expect(shareFilesMock).not.toHaveBeenCalled();

      const closeFirst = holdShareSheet();
      await userEvent.click(screen.getByRole("button", { name: "사진첩에 저장" }));

      expect(shareFilesMock.mock.calls[0][0]).toHaveLength(2);
      // 묶음 사이에는 토스트를 띄우지 않는다
      closeFirst({ type: "shared" });

      expect(await screen.findByText("2장 저장 / 1장 남음")).toBeInTheDocument();
      expect(screen.queryByText(/사진첩에 저장했어요/)).not.toBeInTheDocument();
      expect(onClearSelection).not.toHaveBeenCalled();

      shareFilesMock.mockResolvedValueOnce({ type: "shared" });
      await userEvent.click(screen.getByRole("button", { name: "다음 1장 저장" }));

      expect(shareFilesMock.mock.calls[1][0]).toHaveLength(1);
      expect(await screen.findByText("3장을 사진첩에 저장했어요.")).toBeInTheDocument();
      expect(onClearSelection).toHaveBeenCalledTimes(1);
      expect(track).toHaveBeenCalledWith(
        "Photo Downloaded",
        expect.objectContaining({ mode: "share", photo_count: 3 }),
        null,
      );
    });

    it("시트가 떠 있는 동안 버튼과 닫기를 잠그고, 다시 눌러도 한 번만 넘긴다", async () => {
      serveSingle();
      renderBar([targetOf(5000), targetOf(5001), targetOf(5002)]);

      await downloadToPhotos();
      await screen.findByText("3장 준비됨");

      const close = holdShareSheet();
      await userEvent.click(screen.getByRole("button", { name: "사진첩에 저장" }));

      const busy = screen.getByRole("button", { name: /사진첩에 저장하는 중/ });
      expect(busy).toBeDisabled();
      expect(screen.getByRole("button", { name: "받아둔 사진 버리기" })).toBeDisabled();

      await userEvent.click(busy);
      expect(shareFilesMock).toHaveBeenCalledTimes(1);

      close({ type: "shared" });
      expect(await screen.findByRole("button", { name: "다음 1장 저장" })).toBeEnabled();
    });

    it.each<ShareFilesResult["type"]>(["dismissed", "blocked"])(
      "시트가 %s 로 끝나면 같은 묶음을 그대로 남긴다",
      async (result) => {
        serveSingle();
        renderBar([targetOf(5000), targetOf(5001), targetOf(5002)]);

        await downloadToPhotos();
        await screen.findByText("3장 준비됨");

        shareFilesMock.mockResolvedValueOnce({ type: result } as ShareFilesResult);
        await userEvent.click(screen.getByRole("button", { name: "사진첩에 저장" }));

        expect(await screen.findByRole("button", { name: "사진첩에 저장" })).toBeEnabled();
        expect(screen.getByText("3장 준비됨")).toBeInTheDocument();
        expect(screen.queryByText(/사진첩에 저장했어요/)).not.toBeInTheDocument();
      },
    );

    it("묶음을 넘길 때마다 장수·용량·결과를 남긴다", async () => {
      serveSingle();
      renderBar([targetOf(5000), targetOf(5001), targetOf(5002)]);

      await downloadToPhotos();
      await screen.findByText("3장 준비됨");

      shareFilesMock.mockResolvedValueOnce({ type: "dismissed" });
      await userEvent.click(screen.getByRole("button", { name: "사진첩에 저장" }));

      await waitFor(() =>
        expect(track).toHaveBeenCalledWith(
          "Photo Share Batch Settled",
          {
            batch_index: 1,
            batch_count: 2,
            photo_count: 2,
            // jsdom 의 Blob 은 실제 바이트 수를 그대로 세지 않는다. 묶음 합계가 실리는지만 본다.
            total_bytes: expect.any(Number),
            result: "dismissed",
          },
          null,
        ),
      );
    });

    it("중간에 그만두면 한 번 묻고, 그때까지 넘긴 장수로 끝낸다", async () => {
      serveSingle();
      renderBar([targetOf(5000), targetOf(5001), targetOf(5002)]);

      await downloadToPhotos();
      await screen.findByText("3장 준비됨");

      shareFilesMock.mockResolvedValueOnce({ type: "shared" });
      await userEvent.click(screen.getByRole("button", { name: "사진첩에 저장" }));
      await screen.findByText("2장 저장 / 1장 남음");

      await userEvent.click(screen.getByRole("button", { name: "받아둔 사진 버리기" }));

      const dialog = within(screen.getByRole("dialog"));
      expect(dialog.getByText("남은 1장은 저장하지 않을까요?")).toBeInTheDocument();
      expect(dialog.getByText("저장한 2장은 사진첩에 그대로 있어요")).toBeInTheDocument();

      await userEvent.click(dialog.getByRole("button", { name: "그만두기" }));

      expect(await screen.findByText("2장을 사진첩에 저장했어요.")).toBeInTheDocument();
      expect(track).toHaveBeenCalledWith(
        "Photo Downloaded",
        expect.objectContaining({ mode: "share", photo_count: 2 }),
        null,
      );
    });

    it("그만두기를 묻는 창에서 계속 저장을 누르면 남은 묶음을 그대로 둔다", async () => {
      serveSingle();
      renderBar([targetOf(5000), targetOf(5001), targetOf(5002)]);

      await downloadToPhotos();
      await screen.findByText("3장 준비됨");

      await userEvent.click(screen.getByRole("button", { name: "받아둔 사진 버리기" }));
      await userEvent.click(screen.getByRole("button", { name: "계속 저장" }));

      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      expect(screen.getByText("3장 준비됨")).toBeInTheDocument();
    });
  });
});
