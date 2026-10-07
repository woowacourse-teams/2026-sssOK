-- 외부 호출 결과가 불명확한 작업은 자동 재호출하지 않는다.
ALTER TABLE media_search_document
    ADD COLUMN external_call_in_flight BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE media_search_document
    ADD CONSTRAINT chk_media_search_external_call_status
        CHECK (NOT external_call_in_flight OR status = 'PROCESSING');
