import type { MediaItem } from "@/entities/media";
import type { OrganizeGroup } from "../api/types";
import {
  createPersonReview,
  footerOf,
  includedPhotos,
  mergePeople,
  ownerOfName,
  toggleName,
  togglePhoto,
} from "./personReview";

const photo = (mediaId: number) => ({ mediaId }) as MediaItem;

const group = (groupId: string | null, ids: number[]): OrganizeGroup => ({
  groupId,
  photoCount: ids.length,
  photos: ids.map(photo),
});

const reviewOf = () =>
  createPersonReview([group("p1", [1, 2]), group("p2", [3]), group("p3", [4, 5])]);

describe("personReview", () => {
  it("groupId 가 있는 그룹만 카드로 만든다", () => {
    const review = createPersonReview([group("p1", [1]), group(null, [2])]);

    expect(review.people.map((person) => person.id)).toEqual(["p1"]);
  });

  it("같은 이름을 다시 고르면 선택이 풀린다", () => {
    const picked = toggleName(reviewOf(), "p1", "하루");
    expect(picked.people[0].name).toBe("하루");

    expect(toggleName(picked, "p1", "하루").people[0].name).toBeNull();
  });

  it("다른 카드가 이미 고른 이름이면 그 카드를 알려 준다", () => {
    const review = toggleName(reviewOf(), "p1", "하루");

    expect(ownerOfName(review, "p2", "하루")?.id).toBe("p1");
    expect(ownerOfName(review, "p1", "하루")).toBeNull();
  });

  it("합치면 원래 그룹과 사진을 모두 이름 가진 카드에 모은다", () => {
    const review = toggleName(reviewOf(), "p1", "하루");
    const merged = mergePeople(review, "p1", "p3");

    expect(merged.people.map((person) => person.id)).toEqual(["p1", "p2"]);
    expect(merged.people[0]).toMatchObject({ name: "하루", groupIds: ["p1", "p3"] });
    expect(merged.people[0].photos.map((item) => item.mediaId)).toEqual([1, 2, 4, 5]);
  });

  it("사진을 빼고, 다시 누르면 되돌린다", () => {
    const removed = togglePhoto(reviewOf(), "p1", 1);
    expect(includedPhotos(removed.people[0]).map((item) => item.mediaId)).toEqual([2]);

    const restored = togglePhoto(removed, "p1", 1);
    expect(includedPhotos(restored.people[0]).map((item) => item.mediaId)).toEqual([1, 2]);
  });

  it("방에 같은 이름 폴더가 있으면 이름을 바꾸라고 하고 만들지 못하게 한다", () => {
    const review = toggleName(reviewOf(), "p1", "하루");

    expect(footerOf(review, ["하루"])).toEqual({
      hint: "‘하루’ 폴더는 이름을 바꿔야 만들 수 있어요",
      submit: "폴더 만들기",
      canSubmit: false,
    });
  });

  it("고른 사람 수에 따라 하단 안내와 버튼이 바뀐다", () => {
    expect(footerOf(reviewOf())).toEqual({
      hint: "이름을 고르면 폴더가 생겨요",
      submit: "폴더 만들기",
      canSubmit: false,
    });

    const one = toggleName(reviewOf(), "p1", "하루");
    expect(footerOf(one)).toEqual({
      hint: "이름을 안 고른 2명은 폴더 없이 전체에 남아요",
      submit: "1명의 폴더 만들기",
      canSubmit: true,
    });

    const all = toggleName(toggleName(one, "p2", "윤돌"), "p3", "민찬");
    expect(footerOf(all)).toEqual({ hint: null, submit: "3명의 폴더 만들기", canSubmit: true });
  });
});
