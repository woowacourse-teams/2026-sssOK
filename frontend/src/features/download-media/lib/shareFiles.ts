/**
 * 공유 시트를 띄워 사진첩에 바로 저장하게 한다 (003-selection-download "휴대폰 사진첩에 바로 저장").
 *
 * 폰에서 `<a download>` 로 받은 사진은 사진 앱이 아니라 **파일 앱**에 떨어진다.
 * 사람들이 원하는 건 카메라롤이라, 아이폰·안드로이드에서는 공유 시트를 거쳐
 * "이미지 저장"을 누르게 하는 쪽이 실제로 원하는 자리에 놓인다.
 */

/**
 * 이 기기가 파일 공유를 지원하는지 본다. 지원하지 않으면 버튼 자체를 감춘다 —
 * 눌렀는데 아무 일도 안 일어나는 버튼보다 없는 편이 낫다.
 *
 * `navigator.share` 가 있어도 **파일**은 못 받는 기기가 있어서(맥 크롬 등),
 * 실제 파일로 `canShare` 를 물어봐야 한다. 빈 배열로 물으면 항상 거짓이라 표본을 하나 넣는다.
 */
export const canShareFiles = () => {
  if (typeof navigator === "undefined" || typeof navigator.canShare !== "function") {
    return false;
  }

  try {
    const probe = new File([new Uint8Array(1)], "probe.jpg", { type: "image/jpeg" });

    return navigator.canShare({ files: [probe] });
  } catch {
    // File 생성자가 없는 구형 기기. 어차피 공유도 안 된다.
    return false;
  }
};

export type ShareFilesResult = { type: "shared" } | { type: "dismissed" } | { type: "unsupported" };

const isAbortError = (error: unknown) => error instanceof Error && error.name === "AbortError";

/**
 * **사용자 제스처 안에서 불러야 한다.** 받아오는 걸 먼저 기다린 다음 부르면,
 * 사파리는 "클릭에서 너무 멀어졌다"며 시트를 열지 않는다. 그래서 부르는 쪽
 * (`downloadMedia`)이 바이트를 다 모은 **직후에** 곧바로 이 함수에 닿게 되어 있다.
 *
 * 시트를 열고 사용자가 그냥 닫은 경우는 실패가 아니다. 재시도를 권하면 안 된다.
 */
export const shareFiles = async (files: File[]): Promise<ShareFilesResult> => {
  if (!canShareFiles() || files.length === 0) {
    return { type: "unsupported" };
  }

  // 기기마다 한 번에 받는 장수 한계가 다르다. 못 받겠다고 하면 저장 방식을 바꿔야 한다.
  if (!navigator.canShare({ files })) {
    return { type: "unsupported" };
  }

  try {
    await navigator.share({ files });

    return { type: "shared" };
  } catch (error) {
    if (isAbortError(error)) {
      return { type: "dismissed" };
    }

    throw error;
  }
};
