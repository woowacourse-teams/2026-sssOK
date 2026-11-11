import styled from "@emotion/styled";
import { Link, useRouteError } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { colors, spacing, typography } from "@/shared/styles/tokens";
import { Button } from "@/shared/ui/button";

/** 배포로 파일 이름(contenthash)이 바뀌어 이전 화면 코드를 받지 못한 경우다. */
const isChunkLoadError = (error: unknown) =>
  error instanceof Error &&
  (error.name === "ChunkLoadError" || /Loading (CSS )?chunk .+ failed/.test(error.message));

export const RouteErrorPage = () => {
  const error = useRouteError();
  const isChunkError = isChunkLoadError(error);

  return (
    <Page>
      <Title>{isChunkError ? "화면을 불러오지 못했어요" : "문제가 생겼어요"}</Title>
      <Description>
        {isChunkError
          ? "새 버전이 나왔거나 연결이 불안정해요.\n새로고침하면 다시 열려요."
          : "잠시 후 다시 시도해 주세요."}
      </Description>
      <Actions>
        <Button size="lg" onClick={() => window.location.reload()}>
          새로고침
        </Button>
        <HomeLink to={ROUTES.home}>홈으로 가기</HomeLink>
      </Actions>
    </Page>
  );
};

const Page = styled.main`
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: ${spacing[12]};
  padding: ${spacing[16]};
  text-align: center;
`;

const Title = styled.h1`
  color: ${colors.textStrong};
  ${typography.heading2}
`;

const Description = styled.p`
  color: ${colors.textSecondary};
  white-space: pre-line;
  ${typography.body}
`;

const Actions = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${spacing[12]};
  margin-top: ${spacing[16]};
`;

const HomeLink = styled(Link)`
  color: ${colors.textSecondary};
  text-decoration: underline;
  ${typography.label1}
`;
