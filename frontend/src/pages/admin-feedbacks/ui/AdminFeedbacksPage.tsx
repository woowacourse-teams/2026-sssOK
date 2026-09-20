import { Badge } from "@/shared/ui/badge";
import { Empty, Page, Title } from "./AdminFeedbacksPage.styles";

/**
 * 자리표시 화면. 로그인 성공 뒤 갈 곳이 있어야 인증 흐름이 끝까지 이어진다.
 *
 * 의견 목록 자체는 별도 작업이다 — 여기를 그 화면으로 갈아끼우면 된다.
 */
export const AdminFeedbacksPage = () => (
  <Page>
    <Badge size="md" variant="soft">
      관리자
    </Badge>
    <Title>의견</Title>
    <Empty>의견 화면은 준비 중이에요.</Empty>
  </Page>
);
