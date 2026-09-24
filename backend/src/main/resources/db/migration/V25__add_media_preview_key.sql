-- 상세 모달에 쓰는 파생본(preview)의 스토리지 키.
-- 썸네일과 같은 이유로 완성된 URL 이 아니라 키를 저장한다.
--
-- 목록 타일용 thumbnail 과 따로 두는 이유: 타일은 400px 면 충분한데 모달에서 그걸 띄우면 흐리고,
-- 그렇다고 모달용 1600px 를 목록에 30장 깔면 한 페이지가 5MB 를 넘는다.
--
-- 이 컬럼이 생기기 전에 올라온 사진은 NULL 로 남는다. 조회 쪽이 preview 가 없으면 thumbnail 로
-- 내려앉으므로(MediaUrlResolver) 채워 넣는 배치는 두지 않았다.
ALTER TABLE stored_file
    ADD COLUMN preview_key VARCHAR(255);
