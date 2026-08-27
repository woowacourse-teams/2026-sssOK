/**
 * `ms` 만큼 기다린다. 기다리는 중에 중단되면 곧바로 깨어난다.
 *
 * `true` 면 끝까지 기다린 것이고, `false` 면 중간에 중단된 것이다.
 * 그냥 `setTimeout` 으로 기다리면, 취소를 눌러도 백오프가 끝난 뒤 태연히 다음 요청을 보낸다.
 */
export const waitUnlessAborted = (ms: number, signal?: AbortSignal) =>
  new Promise<boolean>((resolve) => {
    if (signal?.aborted) {
      resolve(false);
      return;
    }

    const stopWaiting = () => {
      clearTimeout(timer);
      resolve(false);
    };

    const timer = setTimeout(() => {
      signal?.removeEventListener("abort", stopWaiting);
      resolve(true);
    }, ms);

    signal?.addEventListener("abort", stopWaiting, { once: true });
  });
