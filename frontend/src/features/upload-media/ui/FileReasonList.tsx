import { ListBox, Reason, Row, RowName } from "./FileReasonList.styles";

export interface FileReasonItem {
  fileName: string;
  /** 파일명 오른쪽에 붙는 짧은 꼬리표. 문장이 아니다. */
  reason: string;
}

interface FileReasonListProps {
  items: FileReasonItem[];
}

/**
 * 어떤 파일이 왜 걸렸는지 한 줄씩 보여주는 목록 (시안 07d · 07g 공통).
 *
 * **장수만 말하지 않고 파일명을 보여주는 이유는** 사용자가 다시 고를 때 같은 파일을
 * 또 집지 않게 하기 위해서다. "2장 실패" 만으로는 어느 것이었는지 알 수 없다.
 *
 * 실패 모달과 거절 모달이 같은 모양을 쓴다 — 사용자가 보는 것은 둘 다
 * "안 된 파일 목록" 이라, 생김새가 다르면 같은 것을 두 번 배워야 한다.
 */
export const FileReasonList = ({ items }: FileReasonListProps) => {
  return (
    <ListBox>
      {items.map(({ fileName, reason }) => (
        <Row key={fileName}>
          <RowName title={fileName}>{fileName}</RowName>
          <Reason>{reason}</Reason>
        </Row>
      ))}
    </ListBox>
  );
};
