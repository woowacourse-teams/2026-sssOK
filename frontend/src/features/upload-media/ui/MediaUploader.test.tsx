import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { MOCK_R2_BASE_URL } from "@/mocks/handlers/upload";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { MAX_IMAGE_BYTES } from "../lib/mediaFileRules";
import { MediaUploader } from "./MediaUploader";
import { UPLOAD_BUTTON_LABEL } from "./UploadButton";

// 백오프를 실제로 기다리면 실패 한 건마다 2초씩 잡아먹는다. 기다림 자체는 따로 확인한다.
jest.mock("../config", () => ({
  ...jest.requireActual("../config"),
  RETRY_BACKOFF_MS: [0, 0],
}));

const TOKEN = "mock-token-10234";

/** 고른 파일이 실제로 올라가므로, 발급이 403 으로 막히지 않게 먼저 방에 들어간다. */
const enterRoom = () =>
  fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
    method: "POST",
    headers: { Authorization: `Bearer ${TOKEN}` },
  });

const renderUploader = (props: { onUploaded?: () => void } = {}) =>
  render(<MediaUploader roomId={MOCK_ROOM_ID} token={TOKEN} {...props} />);

/**
 * 스토리지 키는 파일명을 버리지만 확장자는 남긴다. 그래서 확장자로 "이 파일만 깨진다" 를 만든다.
 *
 * 응답을 돌려주지 않으면 기본 핸들러로 넘어가 정상 업로드가 된다 — 가로채기만 하고
 * 목의 기록은 그대로 두려는 것이다. 목이 바이트를 기록해야 완료 등록이 통과한다.
 */
const failUploadsOf = (extension: string) => {
  const keys: string[] = [];

  server.use(
    http.put(`${MOCK_R2_BASE_URL}/*`, ({ request }) => {
      const key = new URL(request.url).pathname;

      keys.push(key);

      if (!key.endsWith(extension)) return undefined;

      return new HttpResponse(null, { status: 500 });
    }),
  );

  return keys;
};

/** 다음 PUT 들을 붙잡아 둔다. 목이 즉시 답하면 "올라가는 중" 인 화면을 볼 수가 없다. */
const holdUploads = () => {
  let release = () => {};
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });

  server.use(
    http.put(`${MOCK_R2_BASE_URL}/*`, async () => {
      await held;

      return new HttpResponse(null, { status: 200 });
    }),
  );

  return () => release();
};

const failureHeading = () => screen.queryByRole("heading", { name: /못 올렸어요/ });

const settled = () =>
  waitFor(() => expect(screen.queryByRole("progressbar")).not.toBeInTheDocument());

const fileOf = (name: string, size = 1024, type = "image/jpeg") => {
  const file = new File(["x"], name, { type });
  Object.defineProperty(file, "size", { value: size });

  return file;
};

const getUploadButton = () => screen.getByRole("button", { name: UPLOAD_BUTTON_LABEL });

/** 선택기는 접근성 트리에 없다. 버튼이 열어주는 자리라 DOM 에서 직접 집는다. */
const getFileInput = () => {
  const input = document.querySelector<HTMLInputElement>('input[type="file"]');

  if (input === null) throw new Error("파일 입력을 찾지 못했다");

  return input;
};

describe("MediaUploader", () => {
  beforeEach(enterRoom);

  it("사진과 영상을 여러 장 고를 수 있는 선택기를 둔다", () => {
    renderUploader();

    const input = getFileInput();

    expect(input).toHaveAttribute("accept", "image/*,video/*");
    expect(input).toHaveAttribute("multiple");
  });

  it("업로드 버튼을 누르면 기기 기본 사진 선택기가 열린다", async () => {
    const user = userEvent.setup();

    renderUploader();

    const openPicker = jest.spyOn(getFileInput(), "click");
    await user.click(getUploadButton());

    expect(openPicker).toHaveBeenCalled();
  });

  it("고르기 전에는 안내 모달이 없다", () => {
    renderUploader();

    expect(screen.queryByRole("heading", { name: /올릴 수 없|너무 커/ })).not.toBeInTheDocument();
  });

  /**
   * 올라갈 장수는 진행 바가 `0 / 3` 으로 말한다. 모달이 같은 말을 또 할 이유가 없고,
   * 아무 문제 없이 고른 사람의 앞을 막아서도 안 된다.
   */
  it("전부 올릴 수 있으면 모달 없이 바로 올라간다", async () => {
    const user = userEvent.setup();

    renderUploader();
    await user.upload(getFileInput(), [fileOf("a.jpg"), fileOf("b.png"), fileOf("c.mov")]);

    expect(screen.queryByRole("heading", { name: /올릴 수 없|너무 커/ })).not.toBeInTheDocument();
    await settled();
  });

  /**
   * 브라우저는 선택기가 직전과 같은 값을 돌려주면 change 를 보내지 않는다.
   * 그래서 고른 직후 입력을 비우는지를 본다 — jsdom 은 그 생략을 흉내내지 않아서,
   * 두 번 고르는 것만으로는 이 회귀를 잡지 못한다.
   */
  it("고른 직후 입력을 비워, 같은 사진을 두 번 골라도 선택 이벤트가 다시 발생한다", async () => {
    const user = userEvent.setup();

    renderUploader();
    await user.upload(getFileInput(), [fileOf("a.jpg"), fileOf("b.jpg")]);

    expect(getFileInput()).toHaveValue("");
    await settled();
  });

  it("확인을 누르면 모달이 사라진다", async () => {
    const user = userEvent.setup();

    renderUploader();
    await user.upload(getFileInput(), [fileOf("IMG_0001.HEIC")]);
    await user.click(screen.getByRole("button", { name: "확인" }));

    expect(screen.queryByRole("heading", { name: /올릴 수 없|너무 커/ })).not.toBeInTheDocument();
  });

  it("걸러진 파일이 있으면 파일마다 이름·크기·사유를 보여준다", async () => {
    const user = userEvent.setup();

    renderUploader();
    await user.upload(getFileInput(), [
      fileOf("a.jpg"),
      // 사파리는 파일명이 .HEIC 인데 image/jpeg 를 실어 보내기도 한다
      fileOf("IMG_0001.HEIC"),
      fileOf("big.png", MAX_IMAGE_BYTES + 1, "image/png"),
    ]);

    // 사유가 섞였으므로 제목은 어느 한쪽으로 단정하지 않는다.
    expect(screen.getByRole("heading", { name: "2장은 올릴 수 없어요" })).toBeInTheDocument();
    expect(screen.getByText(/IMG_0001\.HEIC/)).toBeInTheDocument();
    expect(screen.getByText("지원 안 함")).toBeInTheDocument();
    expect(screen.getByText("용량 초과")).toBeInTheDocument();

    await settled();
  });

  /**
   * 사유별로 접어서 "2장" 이라고만 하면 **어느 사진이 빠졌는지** 알 수 없다.
   * 다시 고를 때 같은 실수를 반복하게 되므로 파일마다 따로 보여준다 (시안 07d).
   */
  it("같은 사유로 걸러져도 파일을 각각 보여준다", async () => {
    const user = userEvent.setup();

    renderUploader();
    await user.upload(getFileInput(), [fileOf("IMG_0001.HEIC"), fileOf("IMG_0002.HEIC")]);

    expect(screen.getByRole("heading", { name: "올릴 수 없는 형식이에요" })).toBeInTheDocument();
    expect(screen.getByText(/IMG_0001\.HEIC/)).toBeInTheDocument();
    expect(screen.getByText(/IMG_0002\.HEIC/)).toBeInTheDocument();
  });

  it("용량만 넘었으면 제목이 그 사유를 그대로 말한다", async () => {
    const user = userEvent.setup();

    renderUploader();
    await user.upload(getFileInput(), [fileOf("big.png", MAX_IMAGE_BYTES + 1, "image/png")]);

    expect(screen.getByRole("heading", { name: "파일이 너무 커요" })).toBeInTheDocument();
  });

  // 한도는 파일마다 반복하지 않고 부제에서 한 번만 말한다.
  it("올릴 수 있는 한도를 함께 보여준다", async () => {
    const user = userEvent.setup();

    renderUploader();
    await user.upload(getFileInput(), [fileOf("big.png", MAX_IMAGE_BYTES + 1, "image/png")]);

    expect(screen.getByText(/이미지 10MB · 영상 1GB 까지 올릴 수 있어요/)).toBeInTheDocument();
  });

  it("accept 를 통과한 파일도 확장자로 다시 거른다", async () => {
    // accept 는 선택기의 권고일 뿐이라, 데스크톱에서 "모든 파일" 로 바꾸면 그대로 넘어온다
    const user = userEvent.setup({ applyAccept: false });

    renderUploader();
    await user.upload(getFileInput(), [fileOf("note.txt", 10, "text/plain")]);

    expect(screen.getByRole("heading", { name: "올릴 수 없는 형식이에요" })).toBeInTheDocument();
    expect(screen.getByText(/note\.txt/)).toBeInTheDocument();
  });

  it("전부 올라가면 실패 모달이 뜨지 않는다", async () => {
    const user = userEvent.setup();

    renderUploader();
    await user.upload(getFileInput(), [fileOf("첫째.jpg"), fileOf("둘째.jpg")]);
    await settled();

    expect(failureHeading()).not.toBeInTheDocument();
  });

  it("깨진 파일이 있으면 그 장수로 실패 모달이 뜬다", async () => {
    const user = userEvent.setup();

    failUploadsOf(".png");
    renderUploader();
    await user.upload(getFileInput(), [fileOf("첫째.jpg"), fileOf("둘째.png", 1024, "image/png")]);

    // 고른 건 두 장인데 깨진 건 한 장이다. 장수는 실패분만 센다.
    expect(
      await screen.findByRole("heading", { name: "앗, 1장을 못 올렸어요" }),
    ).toBeInTheDocument();
  });

  it("재시도를 누르면 깨진 파일만 다시 올라간다", async () => {
    const user = userEvent.setup();

    const keys = failUploadsOf(".png");

    renderUploader();
    await user.upload(getFileInput(), [fileOf("첫째.jpg"), fileOf("둘째.png", 1024, "image/png")]);
    await screen.findByRole("heading", { name: "앗, 1장을 못 올렸어요" });

    // 첫 판의 기록은 지우고, 재시도가 새로 쏘는 것만 본다.
    keys.length = 0;

    await user.click(screen.getByRole("button", { name: "실패만 재시도" }));
    await screen.findByRole("heading", { name: "앗, 1장을 못 올렸어요" });

    // 멀쩡히 올라간 첫째.jpg 를 또 올리면 갤러리에 같은 사진이 두 장 생긴다.
    expect(keys.length).toBeGreaterThan(0);
    expect(keys.every((key) => key.endsWith(".png"))).toBe(true);
  });

  it("재시도하는 동안에는 모달 대신 진행 바가 보인다", async () => {
    const user = userEvent.setup();

    failUploadsOf(".png");
    renderUploader();
    await user.upload(getFileInput(), [fileOf("둘째.png", 1024, "image/png")]);
    await screen.findByRole("heading", { name: "앗, 1장을 못 올렸어요" });

    const release = holdUploads();

    await user.click(screen.getByRole("button", { name: "실패만 재시도" }));

    expect(screen.getByRole("progressbar")).toBeInTheDocument();
    expect(failureHeading()).not.toBeInTheDocument();

    release();
    await settled();
  });

  it("모달을 닫으면 사라진다", async () => {
    const user = userEvent.setup();

    failUploadsOf(".png");
    renderUploader();
    await user.upload(getFileInput(), [fileOf("둘째.png", 1024, "image/png")]);
    await screen.findByRole("heading", { name: "앗, 1장을 못 올렸어요" });

    await user.click(screen.getByText("닫기"));

    expect(failureHeading()).not.toBeInTheDocument();
  });

  /**
   * 등록이 "이미 끝났다"고만 답하면 registered 가 빈 배열로 온다 (응답에 Media 가 없다).
   * 그래도 서버에는 올라가 있으므로 목록을 다시 불러와야 화면에 나타난다.
   */
  it("이미 등록된 것만 돌아와도 목록을 다시 불러온다", async () => {
    const user = userEvent.setup();

    await enterRoom();

    server.use(
      http.post(`${API_BASE_URL}/rooms/:roomId/media`, () =>
        HttpResponse.json(
          {
            data: {
              registered: [],
              failed: [
                { mediaId: 5013, code: "UPLOAD_ALREADY_COMPLETED", message: "이미 등록됐습니다" },
              ],
            },
          },
          { status: 201 },
        ),
      ),
    );

    const onUploaded = jest.fn();

    renderUploader({ onUploaded });
    await user.upload(getFileInput(), [fileOf("한라산.jpg")]);

    await waitFor(() => expect(onUploaded).toHaveBeenCalled());
  });
});
