import type { MediaItem } from "@/entities/media";
import { createPersonReview, togglePhoto } from "./personReview";
import { renameCard, similarApplyGroupsOf, similarFooterOf, similarOwnerOf } from "./similarReview";

const photo = (mediaId: number) => ({ mediaId, thumbnailUrl: `/t/${mediaId}.webp` }) as MediaItem;

const reviewOf = (sizes: number[]) => {
  let next = 1;
  return createPersonReview(
    sizes.map((size, index) => ({
      groupId: `s${index + 1}`,
      photoCount: size,
      photos: Array.from({ length: size }, () => photo(next++)),
    })),
  );
};

describe("similarReview", () => {
  it("공백뿐인 이름은 지우고, 입력 중인 글자는 그대로 둔다", () => {
    const review = reviewOf([2]);

    expect(renameCard(review, "s1", "  ").people[0].name).toBeNull();
    expect(renameCard(review, "s1", "잔디 ").people[0].name).toBe("잔디 ");
  });

  it("아무 모음에도 이름이 없으면 버튼이 비활성이다", () => {
    expect(similarFooterOf(reviewOf([2, 3]))).toEqual({
      hint: "이름을 붙이면 폴더가 생겨요",
      submit: "폴더 만들기",
      canSubmit: false,
    });
  });

  it("일부만 이름을 붙이면 남는 모음 수를 알리고 만들 폴더 수를 적는다", () => {
    const review = renameCard(reviewOf([2, 3, 1]), "s1", "잔디밭 풍경");

    expect(similarFooterOf(review)).toEqual({
      hint: "이름을 안 붙인 2개는 폴더 없이 전체에 남아요",
      submit: "1개 폴더 만들기",
      canSubmit: true,
    });
  });

  it("같은 이름을 쓴 모음은 한 폴더로 합치고 뺀 사진은 싣지 않는다", () => {
    let review = reviewOf([2, 2, 1]);
    review = renameCard(review, "s1", "바다");
    review = renameCard(review, "s2", " 바다 ");
    review = togglePhoto(review, "s2", 3);

    expect(similarApplyGroupsOf(review.people)).toEqual([
      { groupId: "s1", folderName: "바다", mediaIds: [1, 2, 4] },
    ]);
    expect(similarFooterOf(review).submit).toBe("1개 폴더 만들기");
  });

  it("방에 같은 이름 폴더가 있으면 이름을 바꾸라고 알리고 막는다", () => {
    const review = renameCard(reviewOf([2]), "s1", "여행");

    expect(similarFooterOf(review, ["여행"])).toMatchObject({
      hint: "‘여행’ 폴더는 이름을 바꿔야 만들 수 있어요",
      canSubmit: false,
    });
  });

  it("같은 이름을 먼저 쓴 다른 모음을 찾는다", () => {
    let review = reviewOf([1, 1, 1]);
    review = renameCard(review, "s1", "바다");
    review = renameCard(review, "s3", "바다 ");

    expect(similarOwnerOf(review, "s3")?.id).toBe("s1");
    expect(similarOwnerOf(review, "s2")).toBeNull();
    expect(similarOwnerOf(renameCard(review, "s3", "산"), "s3")).toBeNull();
  });

  describe("사진을 모두 뺀 모음", () => {
    it("이름을 붙인 모음을 모두 빼면 폴더 수와 남는 모음 수에서 모두 빠진다", () => {
      let review = reviewOf([2, 1, 1]);
      review = renameCard(review, "s1", "바다");
      review = renameCard(review, "s2", "산");
      review = togglePhoto(review, "s2", 3);

      expect(similarFooterOf(review)).toEqual({
        hint: "이름을 안 붙인 1개는 폴더 없이 전체에 남아요",
        submit: "1개 폴더 만들기",
        canSubmit: true,
      });
    });

    it("모두 뺀 모음의 이름은 합칠 대상과 폴더 이름 충돌로 보지 않는다", () => {
      let review = reviewOf([1, 1]);
      review = renameCard(review, "s1", "바다");
      review = togglePhoto(review, "s1", 1);
      review = renameCard(review, "s2", "바다");

      expect(similarOwnerOf(review, "s2")).toBeNull();
      expect(similarOwnerOf(review, "s1")).toBeNull();
      expect(similarFooterOf(renameCard(reviewOf([1]), "s1", "여행"), ["여행"]).canSubmit).toBe(
        false,
      );
      expect(
        similarFooterOf(togglePhoto(renameCard(reviewOf([1, 1]), "s1", "여행"), "s1", 1), ["여행"]),
      ).toMatchObject({ hint: "이름을 붙이면 폴더가 생겨요", canSubmit: false });
    });
  });
});
