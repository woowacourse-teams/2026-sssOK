import type { IssuedUpload, RejectedFile } from "../api/types";

export interface UploadTarget {
  issued: IssuedUpload;
  file: File;
}

/**
 * 발급 결과와 원본 `File` 을 짝짓는다.
 *
 * 서버는 요청 순서를 지켜 `issued` 와 `rejected` 를 채워 보내지만 **자리 번호는 주지 않는다.**
 * 그래서 이름으로 찾지 않고, 원본 순서를 훑으며 양쪽 대기열에서 하나씩 꺼내 맞춘다 —
 * 파일명이 겹쳐도 어긋나지 않는다.
 *
 * 다만 **같은 이름 파일이 여럿이고 그중 일부만 거절되면** 어느 자리가 거절됐는지 알 방법이 없다.
 * 서버가 거절 항목에 요청 인덱스를 실어주면 사라지는 문제다 (#76).
 */
export const pairWithFiles = (
  files: File[],
  issued: IssuedUpload[],
  rejected: RejectedFile[],
): UploadTarget[] => {
  const targets: UploadTarget[] = [];
  let issuedIndex = 0;
  let rejectedIndex = 0;

  for (const file of files) {
    if (rejected[rejectedIndex]?.fileName === file.name) {
      rejectedIndex += 1;
      continue;
    }

    const issuedOne = issued[issuedIndex];

    // 발급도 거절도 아닌 파일은 있을 수 없다. 그래도 응답이 어긋나면 없는 자리를 만들지 않는다.
    if (issuedOne === undefined) {
      break;
    }

    issuedIndex += 1;
    targets.push({ issued: issuedOne, file });
  }

  return targets;
};
