/**
 * Blob 을 파일로 내려받게 한다.
 *
 * 원본 URL 에 `<a download>` 를 바로 걸 수는 없다. `download` 속성은 **같은 출처일 때만**
 * 먹고, 스토리지(R2)는 다른 출처라 브라우저가 속성을 무시하고 그 파일로 이동해버린다.
 * 사진이면 화면에 그냥 뜨고, 영상이면 재생기가 열린다 — 받아지지 않는다.
 * 그래서 한 번 받아서(`fetchMediaBlob`) `blob:` 주소로 바꾼 뒤에야 이름을 붙여 저장할 수 있다.
 */

/**
 * 만든 주소를 언제 놓아줄지가 까다롭다. 곧바로 `revokeObjectURL` 을 부르면
 * 브라우저가 저장을 시작하기도 전에 주소가 사라져 다운로드가 조용히 실패한다.
 * 한 틱 뒤로 미뤄, 클릭이 실제로 처리된 다음에 놓아준다.
 */
const RELEASE_DELAY_MS = 1000;

export const saveBlob = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");

  anchor.href = url;
  anchor.download = fileName;
  // 화면에 붙이지 않아도 클릭은 먹지만, 파이어폭스는 붙어 있어야 반응한다.
  anchor.style.display = "none";

  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();

  setTimeout(() => URL.revokeObjectURL(url), RELEASE_DELAY_MS);
};
