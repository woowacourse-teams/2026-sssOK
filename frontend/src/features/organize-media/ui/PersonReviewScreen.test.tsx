import { screen, within } from "@testing-library/react";
import { render } from "../../../../test/renderWithToast";
import userEvent from "@testing-library/user-event";

import type { MediaItem } from "@/entities/media";
import type { OrganizeResult } from "../api/types";
import { PersonReviewScreen } from "./PersonReviewScreen";

const photo = (mediaId: number) =>
  ({ mediaId, fileName: `IMG_${mediaId}.jpg`, thumbnailUrl: `/t/${mediaId}.webp` }) as MediaItem;

const resultOf = (sizes: number[]): OrganizeResult => {
  let next = 1;
  return {
    jobId: "job",
    kind: "PERSON",
    resultVersion: 1,
    photoCount: sizes.reduce((sum, size) => sum + size, 0),
    removedCount: 0,
    groups: sizes.map((size, index) => ({
      groupId: `p${index + 1}`,
      photoCount: size,
      photos: Array.from({ length: size }, () => photo(next++)),
    })),
    unassigned: { groupId: null, photoCount: 0, photos: [] },
    errors: [],
  };
};

const renderScreen = ({
  sizes = [22, 3, 2],
  folderNames = [] as string[],
  isSubmitting = false,
  submitError = null as string | null,
} = {}) => {
  const onSubmit = jest.fn();
  const onDismissError = jest.fn();
  render(
    <PersonReviewScreen
      source={{ state: "ready", result: resultOf(sizes) }}
      myName="소이"
      memberNames={["하루", "윤돌"]}
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

const cards = () => screen.getAllByRole("listitem");

describe("PersonReviewScreen", () => {
  it("찾은 사람 수와 안내를 보여 주고, 사진 줄 마지막 칸에 남은 장수를 올린다", () => {
    renderScreen();

    expect(screen.getByRole("heading", { name: "사람별로 나누기" })).toBeInTheDocument();
    expect(screen.getByText("3명을 찾았어요.")).toBeInTheDocument();
    expect(screen.getByText("같은 이름을 고르면 한 폴더로 합쳐져요.")).toBeInTheDocument();
    expect(within(cards()[0]).getByText("+18")).toBeInTheDocument();
    expect(screen.getByText("이름을 고르면 폴더가 생겨요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "폴더 만들기" })).toBeDisabled();
  });

  it("내 이름은 '나' 칩으로 보이고, 고르면 내 이름이 붙는다", async () => {
    renderScreen();
    const first = cards()[0];

    await userEvent.click(within(first).getByRole("button", { name: "나" }));

    expect(within(first).getByText("소이", { selector: "p" })).toBeInTheDocument();
    expect(within(first).queryByRole("button", { name: "소이" })).not.toBeInTheDocument();
  });

  it("이름을 고르면 카드에 보이고, 다시 누르면 풀린다", async () => {
    renderScreen();
    const first = cards()[0];

    await userEvent.click(within(first).getByRole("button", { name: "하루" }));
    expect(within(first).getByText("하루", { selector: "p" })).toBeInTheDocument();
    expect(screen.getByText("이름을 안 고른 2명은 폴더 없이 전체에 남아요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "1명의 폴더 만들기" })).toBeEnabled();

    await userEvent.click(within(first).getByRole("button", { name: "하루" }));
    expect(within(first).getByText("누구인가요?")).toBeInTheDocument();
  });

  it("다른 카드가 고른 이름을 고르면 같은 사람인지 묻고, 합치면 알린다", async () => {
    const { onSubmit } = renderScreen();

    await userEvent.click(within(cards()[0]).getByRole("button", { name: "하루" }));
    await userEvent.click(within(cards()[1]).getByRole("button", { name: "하루" }));

    expect(screen.getByText("하루 님이 이미 있어요. 같은 사람인가요?")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "네, 합칠게요" }));

    expect(screen.getByText("2명을 찾았어요.")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("하루 님 사진을 합쳤어요 (25장)");
    await userEvent.click(screen.getByRole("button", { name: "1명의 폴더 만들기" }));
    expect(onSubmit).toHaveBeenCalledWith([
      {
        groupId: "p1",
        folderName: "하루",
        mediaIds: Array.from({ length: 25 }, (_, index) => index + 1),
      },
    ]);
  });

  it("직접 입력 칩에 쓰고 엔터를 누르면 입력칸을 빠져나와 그 이름을 고르고, 다른 카드에도 칩으로 보여 준다", async () => {
    renderScreen();

    const input = within(cards()[0]).getByRole("textbox", { name: "이름 직접 입력" });
    await userEvent.type(input, "민찬{Enter}");

    // 적용한 뒤에는 입력칸에서 빠져나와 키보드가 닫힌다.
    expect(input).not.toHaveFocus();
    expect(input).toHaveValue("");

    expect(within(cards()[0]).getByText("민찬", { selector: "p" })).toBeInTheDocument();
    expect(within(cards()[1]).getByRole("button", { name: "민찬" })).toBeInTheDocument();
  });

  it("방에 같은 이름 폴더가 있으면 이름을 바꾸게 하고, 바꾸면 만들 수 있다", async () => {
    renderScreen({ folderNames: ["하루"] });
    const first = cards()[0];

    await userEvent.click(within(first).getByRole("button", { name: "하루" }));

    expect(within(first).getByText("‘하루’ 폴더가 이미 있어요.")).toBeInTheDocument();
    expect(screen.getByText("‘하루’ 폴더는 이름을 바꿔야 만들 수 있어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "폴더 만들기" })).toBeDisabled();

    await userEvent.click(within(first).getByRole("button", { name: "이름 바꾸기" }));
    const input = within(first).getByRole("textbox", { name: "새 폴더 이름" });
    await userEvent.clear(input);
    await userEvent.type(input, "하루 나온 사진");
    await userEvent.click(within(first).getByRole("button", { name: "이 이름으로" }));

    expect(within(first).getByText("하루 나온 사진", { selector: "p" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "1명의 폴더 만들기" })).toBeEnabled();
  });

  it("사진 확인 화면에서 체크를 풀면 빼고 알리며, 다시 누르면 되돌린다", async () => {
    renderScreen();

    await userEvent.click(within(cards()[1]).getByRole("button", { name: /사진 3장 확인/ }));
    expect(screen.getByRole("heading", { name: "3장" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "사람 목록으로" }));

    await userEvent.click(within(cards()[0]).getByRole("button", { name: "하루" }));
    await userEvent.click(within(cards()[0]).getByRole("button", { name: /사진 22장 확인/ }));
    expect(screen.getByRole("heading", { name: "하루 22장" })).toBeInTheDocument();

    const first = screen.getByRole("checkbox", { name: "사진 1" });
    await userEvent.click(first);
    expect(first).not.toBeChecked();
    expect(screen.getByRole("status")).toHaveTextContent("1장을 뺐어요");
    expect(screen.getByRole("heading", { name: "하루 21장" })).toBeInTheDocument();

    await userEvent.click(first);
    expect(first).toBeChecked();
    expect(screen.getByRole("heading", { name: "하루 22장" })).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("checkbox", { name: "사진 2" }));
    await userEvent.click(screen.getByRole("button", { name: "완료" }));
    expect(within(cards()[0]).getByText("+17")).toBeInTheDocument();
  });

  it("이름 고른 사람의 사진을 모두 빼면 흐린 카드로 바뀌어 폴더에서 빠지고, 그 이름을 다른 카드가 써도 합칠지 묻지 않는다", async () => {
    const { onSubmit } = renderScreen();

    await userEvent.click(within(cards()[1]).getByRole("button", { name: "하루" }));
    await userEvent.click(within(cards()[1]).getByRole("button", { name: /사진 3장 확인/ }));
    for (const index of [1, 2, 3]) {
      await userEvent.click(screen.getByRole("checkbox", { name: `사진 ${index}` }));
    }
    expect(screen.getByRole("status")).toHaveTextContent("모두 뺐어요. 폴더로 만들지 않아요");
    await userEvent.click(screen.getByRole("button", { name: "완료" }));

    expect(
      within(cards()[1]).getByRole("button", { name: /사진을 모두 뺐어요/ }),
    ).toHaveTextContent("눌러서 다시 넣기");
    expect(screen.getByText("이름을 고르면 폴더가 생겨요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "폴더 만들기" })).toBeDisabled();

    await userEvent.click(within(cards()[0]).getByRole("button", { name: "하루" }));
    expect(screen.queryByText("하루 님이 이미 있어요. 같은 사람인가요?")).not.toBeInTheDocument();
    expect(screen.getByText("이름을 안 고른 1명은 폴더 없이 전체에 남아요")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "1명의 폴더 만들기" }));
    expect(onSubmit).toHaveBeenCalledWith([
      {
        groupId: "p1",
        folderName: "하루",
        mediaIds: Array.from({ length: 22 }, (_, index) => index + 1),
      },
    ]);
  });

  it("'나' 를 고르면 내 닉네임으로 폴더를 만든다", async () => {
    const { onSubmit } = renderScreen({ sizes: [2] });

    await userEvent.click(within(cards()[0]).getByRole("button", { name: "나" }));
    await userEvent.click(screen.getByRole("button", { name: "1명의 폴더 만들기" }));

    expect(onSubmit).toHaveBeenCalledWith([
      { groupId: "p1", folderName: "소이", mediaIds: [1, 2] },
    ]);
  });

  it("폴더를 만드는 동안 버튼을 막는다", () => {
    renderScreen({ isSubmitting: true });

    expect(screen.getByRole("button", { name: "폴더를 만드는 중" })).toBeDisabled();
  });

  it("폴더를 만들지 못했으면 검토 화면에 남아 까닭을 알린다", async () => {
    const { onDismissError } = renderScreen({
      submitError: "폴더를 만들지 못했어요. 다시 시도해 주세요.",
    });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "폴더를 만들지 못했어요. 다시 시도해 주세요.",
    );
    expect(screen.getByText("3명을 찾았어요.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "알림 닫기" }));
    expect(onDismissError).toHaveBeenCalled();
  });

  it("결과를 못 읽었으면 다시 불러오게 한다", async () => {
    const onRetry = jest.fn();
    render(
      <PersonReviewScreen
        source={{ state: "failed" }}
        myName="소이"
        memberNames={[]}
        folderNames={[]}
        onClose={jest.fn()}
        onRetry={onRetry}
        onSubmit={jest.fn()}
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "다시 불러오기" }));
    expect(onRetry).toHaveBeenCalled();
  });
});
