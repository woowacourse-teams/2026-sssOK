-- 촬영 정보. 워커가 원본 EXIF 에서 읽어 채운다.
-- 카메라가 기록하지 않았거나 편집 과정에서 지워졌으면 비어 있다.
ALTER TABLE stored_file
    ADD COLUMN taken_at TIMESTAMPTZ,
    -- 좌표는 소수점 이하 자릿수가 정확도를 좌우해 부동소수로 두지 않는다.
    -- 위도 ±90, 경도 ±180 이라 정수부는 3자리면 충분하다.
    ADD COLUMN latitude  NUMERIC(9, 6),
    ADD COLUMN longitude NUMERIC(9, 6);
