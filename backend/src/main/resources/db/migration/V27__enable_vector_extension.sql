-- #460 자연어 이미지 검색. 모델 선정과 무관한 벡터 확장부터 준비한다.
-- RDS에서 애플리케이션 계정에 설치 권한이 없으면 관리자가 각 DB에 먼저 설치한다.
CREATE EXTENSION IF NOT EXISTS vector;
