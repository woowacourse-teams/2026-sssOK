/**
 * zip 으로 받을 때 붙는 파일명. **서버가 정하는 이름을 미리 그려 보여주기 위한 것이다.**
 *
 * 실제 이름은 압축 잡 응답(`fileName`)이 알려준다. 그런데 시트는 잡을 만들기 **전에** 뜨고,
 * 시안이 그 자리에서 파일명을 보여준다 — 그래서 같은 규칙을 프론트에도 한 벌 둔다.
 *
 * 규칙이 두 곳에 있는 셈이라, **받을 때는 반드시 응답의 `fileName` 을 쓴다.**
 * 여기 값은 미리보기 전용이고, 서버가 규칙을 바꾸면 시트 문구만 어긋난다.
 * (backend `DownloadFileNames.zipNameOf` 와 같은 형식)
 */
export const zipArchiveName = (roomCode: string) => `ShareDrop_${roomCode}.zip`;
