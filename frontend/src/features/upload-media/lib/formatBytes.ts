/**
 * 파일 크기를 사람이 읽을 문자열로 바꾼다 — `14MB`, `2.3GB`.
 *
 * 왜 자릿수를 나눠 쓰냐면, 못 올린 이유가 **한도를 얼마나 넘었는지**라서다.
 * `14MB` 는 10MB 한도를 넘은 게 한눈에 보이지만 `14680064` 는 아무 말도 하지 않는다.
 * GB 는 소수 첫째 자리까지 보여준다 — `2GB` 와 `2.3GB` 는 사용자에게 다른 크기다.
 */

const KB = 1024;
const MB = KB * 1024;
const GB = MB * 1024;

export const formatBytes = (bytes: number) => {
  if (bytes >= GB) {
    // 2.0GB 처럼 의미 없는 0 은 떼고 2GB 로 보여준다.
    return `${trimZero(bytes / GB)}GB`;
  }

  if (bytes >= MB) {
    return `${Math.round(bytes / MB)}MB`;
  }

  // 1MB 미만은 반올림하면 전부 0MB 가 된다. 한도에 걸릴 크기가 아니라 자주 오지도 않는다.
  return `${Math.max(Math.round(bytes / KB), 1)}KB`;
};

const trimZero = (value: number) => Number(value.toFixed(1)).toString();
