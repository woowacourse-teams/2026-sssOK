import { screen } from "@testing-library/react";
import { render } from "../../../../test/renderWithToast";
import userEvent from "@testing-library/user-event";

import type { MediaItem } from "@/entities/media";
import type { OrganizeResult } from "../api/types";
import { SimilarReviewScreen } from "./SimilarReviewScreen";

const photo = (
  mediaId: number,
): {
  mediaId: number;
  type: "IMAGE" | "VIDEO";
  fileName: string;
  mimeType: string;
  size: number;
  duration: number | null;
  folderIds: number[];
  uploaderId: number;
  uploaderName: string;
  uploadedAt: string;
  thumbnailUrl: string;
  displayUrl: string;
  width: number;
  height: number;
  status: "READY";
  likeCount: number;
  likedByMe: boolean;
  thumbnailUrlExpiresAt?: string | null | undefined;
  displayUrlExpiresAt?: string | null | undefined;
} => ({ mediaId, fileName: `IMG_${mediaId}.jpg`, thumbnailUrl: `/t/${mediaId}.webp` }) as MediaItem;

const resultOf = (sizes: number[]): OrganizeResult => {
  let next = 1;
  return {
    jobId: "job",
    kind: "SIMILAR",
    resultVersion: 1,
    photoCount: sizes.reduce((sum, size) => sum + size, 0),
    removedCount: 0,
    groups: sizes.map((size, index) => ({
      groupId: `s${index + 1}`,
      photoCount: size,
      photos: Array.from({ length: size }, () => photo(next++)),
    })),
    unassigned: { groupId: null, photoCount: 0, photos: [] },
    errors: [],
  };
};

interface ScreenOptions {
  isSubmitting?: boolean;
  submitError?: string | null;
}

const renderScreen = (
  sizes = [9, 5, 2],
  folderNames: string[] = [],
  { isSubmitting, submitError }: ScreenOptions = {},
): { onSubmit: ReturnType<typeof jest.fn>; onDismissError: ReturnType<typeof jest.fn> } => {
  const onSubmit = jest.fn();
  const onDismissError = jest.fn();
  render(
    <SimilarReviewScreen
      source={{ state: "ready", result: resultOf(sizes) }}
      folderNames={folderNames}
      onClose={jest.fn()}
      onRetry={jest.fn()}
      onSubmit={onSubmit}
      isSubmitting={isSubmitting}
      submitError={submitError}
      onDismissError={onDismissError}
    />,
  );
  return { onSubmit, onDismissError };
};

describe("SimilarReviewScreen", () => {
  it("묶은 모음 수와 모음마다 이름 입력칸을 보여 주고, 이름이 없으면 폴더 만들기를 막는다", () => {
    renderScreen();

    expect(screen.getByRole("heading", { name: "사진을 3개로 묶었어요." })).toBeInTheDocument();
    expect(screen.getAllByText("어떤 사진인가요?")).toHaveLength(3);
    expect(screen.getAllByPlaceholderText("폴더 이름 입력")).toHaveLength(3);
    expect(screen.getByText("+5")).toBeInTheDocument();
    expect(screen.getByText("이름을 붙이면 폴더가 생겨요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "폴더 만들기" })).toBeDisabled();
  });

  it("이름을 붙이면 남는 모음 안내와 함께 그 모음으로 폴더를 만든다", async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderScreen();

    await user.type(screen.getAllByRole("textbox", { name: "폴더 이름" })[0], "잔디밭 풍경");

    expect(screen.getByText("이름을 안 붙인 2개는 폴더 없이 전체에 남아요")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "1개 폴더 만들기" }));
    expect(onSubmit).toHaveBeenCalledWith([
      { groupId: "s1", folderName: "잔디밭 풍경", mediaIds: [1, 2, 3, 4, 5, 6, 7, 8, 9] },
    ]);
  });

  it("폴더를 만드는 중이면 버튼을 막고 진행 중이라고 보여 준다", () => {
    renderScreen([9, 5, 2], [], { isSubmitting: true });

    expect(screen.getByRole("button", { name: "폴더를 만드는 중" })).toBeDisabled();
  });

  it("폴더를 만들지 못하면 오류를 알리고 화면에 남아 다시 누를 수 있다", async () => {
    const user = userEvent.setup();
    const { onSubmit, onDismissError } = renderScreen([9, 5, 2], [], {
      submitError: "폴더를 만들지 못했어요. 다시 시도해 주세요.",
    });

    expect(
      await screen.findByText("폴더를 만들지 못했어요. 다시 시도해 주세요."),
    ).toBeInTheDocument();
    expect(onDismissError).toHaveBeenCalled();

    await user.type(screen.getAllByRole("textbox", { name: "폴더 이름" })[0], "잔디밭 풍경");
    await user.click(screen.getByRole("button", { name: "1개 폴더 만들기" }));
    expect(onSubmit).toHaveBeenCalledTimes(1);
  });

  it("엔터를 치면 키보드를 닫고 이름을 주황 칩으로 바꾸며, 칩을 누르면 다시 고칠 수 있다", async () => {
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getAllByRole("textbox", { name: "폴더 이름" })[0], "잔디밭 풍경{Enter}");

    expect(screen.getAllByRole("textbox", { name: "폴더 이름" })).toHaveLength(2);
    await user.click(screen.getByRole("button", { name: "잔디밭 풍경 이름 고치기" }));

    const input = screen.getByDisplayValue("잔디밭 풍경");
    expect(input).toHaveFocus();
    await user.type(input, "2{Enter}");
    expect(screen.getByRole("button", { name: "잔디밭 풍경2 이름 고치기" })).toBeInTheDocument();
  });

  it("이름 없이 엔터를 치면 입력칸 그대로 둔다", async () => {
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getAllByRole("textbox", { name: "폴더 이름" })[0], "{Enter}");

    expect(screen.getAllByRole("textbox", { name: "폴더 이름" })).toHaveLength(3);
  });

  it("모음을 눌러 사진을 빼면 '1장을 뺐어요' 가 보이고 모음 장수가 줄어든다", async () => {
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByRole("button", { name: "이름 없는 모음 사진 5장 확인" }));
    expect(screen.getByRole("heading", { name: "5장" })).toBeInTheDocument();
    expect(screen.getByText("이 모음에 안 맞는 사진을 눌러 빼 주세요.")).toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: "사진 5" }));
    expect(screen.getByText("1장을 뺐어요")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "4장" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "완료" }));
    expect(
      screen.getByRole("button", { name: "이름 없는 모음 사진 4장 확인" }),
    ).toBeInTheDocument();
  });

  it("이름 붙인 모음의 사진을 모두 빼면 흐린 카드로 바뀌어 폴더에서 빠지고, 다시 넣으면 이름째 돌아온다", async () => {
    const user = userEvent.setup();
    renderScreen([9, 5, 2]);

    await user.type(screen.getAllByRole("textbox", { name: "폴더 이름" })[2], "바다{Enter}");
    await user.click(screen.getByRole("button", { name: "바다 모음 사진 2장 확인" }));
    await user.click(screen.getByRole("checkbox", { name: "사진 1" }));
    await user.click(screen.getByRole("checkbox", { name: "사진 2" }));
    expect(screen.getByText("모두 뺐어요. 폴더로 만들지 않아요")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "완료" }));

    const emptied = screen.getByRole("button", { name: /사진을 모두 뺐어요/ });
    expect(emptied).toHaveTextContent("눌러서 다시 넣기");
    expect(screen.queryByRole("button", { name: "바다 이름 고치기" })).not.toBeInTheDocument();
    expect(screen.getByText("이름을 붙이면 폴더가 생겨요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "폴더 만들기" })).toBeDisabled();

    await user.click(emptied);
    expect(screen.getByRole("heading", { name: "바다 0장" })).toBeInTheDocument();
    await user.click(screen.getByRole("checkbox", { name: "사진 1" }));
    await user.click(screen.getByRole("button", { name: "완료" }));

    expect(screen.getByRole("button", { name: "바다 이름 고치기" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "1개 폴더 만들기" })).toBeEnabled();
  });

  describe("이름 겹침", () => {
    const nameInputs = (): HTMLElement[] => screen.getAllByRole("textbox", { name: "폴더 이름" });

    it("다른 모음과 같은 이름을 붙이면 합칠지 묻고, 합치면 모음 수가 줄고 합친 장수를 알린다", async () => {
      const user = userEvent.setup();
      renderScreen();

      await user.type(nameInputs()[0], "잔디밭 풍경{Enter}");
      await user.type(nameInputs()[0], "잔디밭 풍경{Enter}");

      expect(screen.getByRole("heading", { name: "한 폴더로 합칠까요?" })).toBeInTheDocument();
      expect(screen.getByText(/‘잔디밭 풍경’ 모음이 이미 있어요./)).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: "네, 합칠게요" }));

      expect(
        screen.queryByRole("heading", { name: "한 폴더로 합칠까요?" }),
      ).not.toBeInTheDocument();
      expect(screen.getByRole("heading", { name: "사진을 2개로 묶었어요." })).toBeInTheDocument();
      expect(screen.getByText("잔디밭 풍경 사진을 합쳤어요 (14장)")).toBeInTheDocument();
      expect(screen.getByText("이름을 안 붙인 1개는 폴더 없이 전체에 남아요")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "1개 폴더 만들기" })).toBeEnabled();
    });

    it("합치지 않으면 방금 붙인 이름을 떼고 모음은 그대로 둔다", async () => {
      const user = userEvent.setup();
      renderScreen();

      await user.type(nameInputs()[0], "잔디밭 풍경{Enter}");
      await user.type(nameInputs()[0], "잔디밭 풍경{Enter}");
      await user.click(screen.getByRole("button", { name: "아니요" }));

      expect(screen.getByRole("heading", { name: "사진을 3개로 묶었어요." })).toBeInTheDocument();
      expect(screen.getAllByRole("button", { name: "잔디밭 풍경 이름 고치기" })).toHaveLength(1);
      expect(nameInputs()).toHaveLength(2);
      expect(nameInputs()[0]).toHaveValue("");
    });

    it("방에 같은 이름 폴더가 있으면 입력칸 아래에 바꾸라고 알리고, 바꾸기 전에는 폴더를 못 만든다", async () => {
      const user = userEvent.setup();
      renderScreen([9, 5, 2], ["잔디밭 풍경"]);

      await user.type(nameInputs()[0], "잔디밭 풍경{Enter}");

      expect(screen.getByRole("alert")).toHaveTextContent(
        "‘잔디밭 풍경’ 폴더가 이미 있어요. 다른 이름을 붙여 주세요.",
      );
      expect(nameInputs()[0]).toHaveAttribute("aria-invalid", "true");
      expect(
        screen.getByText("‘잔디밭 풍경’ 폴더는 이름을 바꿔야 만들 수 있어요"),
      ).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "폴더 만들기" })).toBeDisabled();

      await user.type(nameInputs()[0], "2{Enter}");

      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: "1개 폴더 만들기" })).toBeEnabled();
    });
  });
});
