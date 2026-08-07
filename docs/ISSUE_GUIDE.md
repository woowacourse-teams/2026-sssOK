# 이슈 전략

## 이슈 종류 (라벨)

- `feature` : 신규 기능
- `bug` : 버그 수정
- `refactor` : 리팩터링
- `chore` : 설정/문서/빌드
- `frontend` / `backend` : 모노레포이므로 영역 구분 라벨 필수 (둘 다 걸치면 `fullstack` 라벨 추가)

## 이슈 템플릿

```markdown
## 작업 내용
-

## 작업 범위
- [ ] Frontend
- [ ] Backend

## 완료 조건 (Acceptance Criteria)
-

## 참고 사항
-
```

## 규칙

- 작업 시작 전 이슈부터 생성 (선작업 후이슈 금지 → 브랜치/PR과 추적 연결이 끊김)
- 담당자(Assignee) 지정 필수
- 제목은 `[FE]` / `[BE]` / `[FULL]` 접두사로 영역 구분
  - 예: `[BE] 결제 등록 API 구현`, `[FE] 결제 내역 조회 화면`
