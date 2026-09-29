# 백엔드 버전 의도

백엔드 변경 PR은 아래 명령으로 이슈별 버전 의도 파일을 하나 추가한다.

```bash
bash .github/scripts/declare-backend-version.sh <이슈번호> <major|minor|patch>
```

각 파일은 서로 다른 이슈 번호를 사용하므로 동시에 열린 PR끼리 충돌하지 않는다. 릴리스 준비 시
`prepare-backend-version.sh`가 누적된 의도 중 가장 높은 단계를 백엔드 버전에 한 번 적용하고 파일을
모두 삭제한다.
