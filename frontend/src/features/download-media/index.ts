/**
 * 슬라이스 밖으로 여는 것만 둔다.
 * 나머지(zip 조립, 공유 시트, 진행률 계산)는 안에서만 쓴다 —
 * 여기 적는 순간 밖에서 써도 된다는 뜻이 되므로, 실제로 쓰는 곳이 생길 때 연다.
 */
export { SelectionDownloadBar } from "./ui/SelectionDownloadBar";
export type { DownloadMode, DownloadOutcome, DownloadTarget, FailedDownload } from "./model/types";
