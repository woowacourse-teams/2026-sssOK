-- 상세 모달에 쓰는 파생본(preview)의 스토리지 키.
-- 썸네일과 같은 이유로 완성된 URL 이 아니라 키를 저장한다.
--
-- 목록 타일용 thumbnail 과 따로 두는 이유: 타일은 400px 면 충분한데 모달에서 그걸 띄우면 흐리다.
-- 반대로 1600px 프리뷰는 상세를 열었을 때만 내주어, 실제로 열어보는 사진에 대해서만 요청이 일어나게 한다.
--
-- 이 컬럼이 생기기 전에 올라온 사진은 NULL 로 남는다. 조회 쪽이 preview 가 없으면 original 로
-- 내려앉으므로(MediaUrlResolver) 채워 넣는 배치는 두지 않았다.
ALTER TABLE stored_file
    ADD COLUMN preview_key VARCHAR(255);
