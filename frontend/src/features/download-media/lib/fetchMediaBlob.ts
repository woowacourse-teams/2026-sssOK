/**
 * 단건 다운로드(B-6)로 원본 바이트를 받아 Blob 하나로 만든다.
 * **한 번 불릴 때 요청을 하나만 보낸다.**
 *
 * 재시도하지 않고, 실패를 예외가 아니라 **값으로** 돌려준다 — `putToStorage` 와 같은 규칙이다.
 * 사진 30장 중 하나가 깨졌다고 나머지 29장까지 멈추면 안 되고, 그 판단은 부르는 쪽 몫이다.
 *
 * `shared/api/apiClient` 를 쓸 수 없다. `{ data }` 를 파싱하는데, 이 요청은 302 를 따라
 * 스토리지로 넘어가고 응답 본문이 이미지 바이트다. 그래서 접두사·토큰만 직접 붙인다.
 *
 * **전제: R2 버킷에 GET CORS 가 열려 있어야 한다.** 서명이 아니라 브라우저 정책이라
 * 열려 있지 않으면 응답이 통째로 막히고, 여기서는 `status: 0` 으로만 보인다
 * (docs/backend/R2_PRESIGNED_UPLOAD.md 의 "확인하지 않은 것" 참고).
 */

type ProgressListener = (loaded: number, total: number) => void;

export interface FetchMediaBlobParams {
  url: string;
  /** 참여자만 받을 수 있다. 붙이지 않으면 401 이다. */
  token?: string;
  /** 목록이 알려준 크기. 응답에 Content-Length 가 없을 때 퍼센트의 분모로 쓴다. */
  size: number;
  signal?: AbortSignal;
  onProgress?: ProgressListener;
}

/** 중단은 실패와 따로 둔다 — 취소를 실패로 세면 "3장 실패" 같은 거짓 보고가 나간다. */
export type FetchMediaBlobResult =
  { type: "success"; blob: Blob } | { type: "failure"; status: number } | { type: "aborted" };

const isAbortError = (error: unknown) => error instanceof Error && error.name === "AbortError";

export const fetchMediaBlob = async ({
  url,
  token,
  size,
  signal,
  onProgress,
}: FetchMediaBlobParams): Promise<FetchMediaBlobResult> => {
  if (signal?.aborted) {
    return { type: "aborted" };
  }

  try {
    // 302 를 그대로 따라간다. `redirect: "manual"` 로 받으면 `opaqueredirect` 라
    // Location 을 읽을 수조차 없다 — 따라가서 바이트를 받는 것 말고는 방법이 없다.
    //
    // credentials 는 붙이지 않는다. 토큰은 헤더로 실으므로 쿠키가 필요 없고,
    // 붙이면 리다이렉트가 닿는 스토리지 쪽에서 `*` 인 CORS 응답이 거절된다.
    const response = await fetch(url, {
      signal,
      credentials: "omit",
      headers: token === undefined ? undefined : { Authorization: `Bearer ${token}` },
    });

    if (!response.ok) {
      return { type: "failure", status: response.status };
    }

    const header = Number(response.headers.get("content-length"));
    const total = Number.isFinite(header) && header > 0 ? header : size;

    // 스트림을 못 읽는 환경에서는 퍼센트를 포기하고 통째로 받는다. 받아지긴 해야 한다.
    if (response.body === null) {
      const blob = await response.blob();

      onProgress?.(blob.size, blob.size);

      return { type: "success", blob };
    }

    const reader = response.body.getReader();
    const chunks: BlobPart[] = [];
    let loaded = 0;

    for (;;) {
      const { done, value } = await reader.read();

      if (done) {
        break;
      }

      chunks.push(value);
      loaded += value.length;
      // 분모를 넘겨 세지 않는다. Content-Length 가 없어 size 로 대신할 때 어긋날 수 있다.
      onProgress?.(Math.min(loaded, total), total);
    }

    return {
      type: "success",
      blob: new Blob(chunks, { type: response.headers.get("content-type") ?? "" }),
    };
  } catch (error) {
    if (isAbortError(error) || signal?.aborted) {
      return { type: "aborted" };
    }

    // CORS 차단·회선 끊김은 상태 코드 자체가 없다. 0 으로 내려 "받지 못했다"로만 남긴다.
    return { type: "failure", status: 0 };
  }
};
