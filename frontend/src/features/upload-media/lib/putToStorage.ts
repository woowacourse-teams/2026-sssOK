/**
 * 스토리지(R2)로 파일 하나를 PUT 한다. **한 번 불릴 때 요청을 하나만 보낸다.**
 *
 * 재시도도, 재발급도, "이 실패가 재시도할 만한가" 하는 판단도 하지 않는다.
 * 이 안에는 반복문이 없다 — 다시 올려야 하면 부르는 쪽(`uploadOne`)이 새 URL 을 받아
 * 이 함수를 **다시 부른다**. 여기는 결과를 값으로 돌려주기만 한다.
 *
 * `shared/api/apiClient` 를 쓸 수 없다. 접두사·토큰을 붙이고 `{ data }` 를 파싱하는데,
 * 이 요청은 외부 도메인이고 응답 본문이 비어 있다.
 */

/** `total` 은 전송할 전체 바이트다. 파일명·mediaId 는 여기서 모른다 — 부르는 쪽이 붙인다. */
type ProgressListener = (loaded: number, total: number) => void;

export interface PutToStorageParams {
  url: string;
  /** 발급 응답의 `headers` 를 **그대로** 넘긴다. `file.type` 으로 만들면 서명과 어긋난다. */
  headers: Record<string, string>;
  file: File;
  signal?: AbortSignal;
  onProgress?: ProgressListener;
}

/**
 * 세 갈래로만 나뉜다. `status` 가 `0` 이면 응답을 아예 못 받은 것이다(네트워크 끊김).
 * 중단은 실패와 따로 둔다 — 재시도하면 안 되는 유일한 경우라서다.
 */
export type PutToStorageResult =
  { type: "success" } | { type: "failure"; status: number } | { type: "aborted" };

const isSuccessStatus = (status: number) => status >= 200 && status < 300;

export const putToStorage = ({ url, headers, file, signal, onProgress }: PutToStorageParams) =>
  new Promise<PutToStorageResult>((resolve) => {
    if (signal?.aborted) {
      // 앞선 파일이 올라가는 동안 이미 취소를 눌렀다. 요청을 시작할 이유가 없다.
      resolve({ type: "aborted" });
      return;
    }

    const xhr = new XMLHttpRequest();
    const abortRequest = () => xhr.abort();

    /** 한 요청은 한 번만 끝난다. 어느 경로로 끝나든 signal 에 걸어둔 청취자를 떼어낸다. */
    const settle = (result: PutToStorageResult) => {
      signal?.removeEventListener("abort", abortRequest);
      resolve(result);
    };

    xhr.open("PUT", url);

    // setRequestHeader 는 open 뒤에만 먹는다. Authorization 은 붙이지 않는다 —
    // 서명이 URL 에 이미 들어 있어서, 토큰을 얹으면 R2 가 403 으로 거절한다.
    for (const [name, value] of Object.entries(headers)) {
      xhr.setRequestHeader(name, value);
    }

    if (onProgress) {
      xhr.upload.addEventListener("progress", (event) => {
        // 전체 크기를 모를 때가 있다. 그때 event.total 은 0 이라 File.size 로 대신한다.
        onProgress(event.loaded, event.lengthComputable ? event.total : file.size);
      });
    }

    xhr.addEventListener("load", () => {
      // 오류 본문은 XML 이라 우리 ApiError 형식이 아니다. 파싱하지 않고 상태 코드만 본다.
      settle(
        isSuccessStatus(xhr.status) ? { type: "success" } : { type: "failure", status: xhr.status },
      );
    });

    // 연결이 끊기면 상태 코드 자체가 없다. 0 으로 내려보내 재시도 대상으로 남긴다.
    xhr.addEventListener("error", () => settle({ type: "failure", status: 0 }));
    xhr.addEventListener("abort", () => settle({ type: "aborted" }));

    signal?.addEventListener("abort", abortRequest, { once: true });

    // File 을 그대로 보낸다. readAsArrayBuffer 등으로 읽으면 아이폰에서 메모리로 죽는다.
    // xhr.timeout 은 두지 않는다 — 느린 회선의 큰 영상이 멀쩡히 올라가는 중에 잘린다.
    xhr.send(file);
  });
