import styled from "@emotion/styled";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { HiArrowLeft, HiChevronRight } from "react-icons/hi2";
import { useNavigate } from "react-router-dom";

import { MEMBER_SESSION_QUERY_KEY, useMemberSessionQuery } from "@/entities/member-session";
import { manageAccount } from "@/features/manage-account";
import { ROUTES } from "@/shared/config";
import { colors, typography } from "@/shared/styles/tokens";
import { Button } from "@/shared/ui/button";
import { IconButton } from "@/shared/ui/icon-button";
import { Modal } from "@/shared/ui/modal";
import { Row } from "@/shared/ui/row";
import { Stack } from "@/shared/ui/stack";

type Action = "logout" | "withdraw";

export const AccountSettingsPage = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: session } = useMemberSessionQuery();
  const [action, setAction] = useState<Action | null>(null);
  const mutation = useMutation({
    mutationFn: (selected: Action) => {
      if (!session) throw new Error("로그인 정보를 확인해 주세요.");
      return manageAccount(selected, session.accessToken);
    },
    onSuccess: async () => {
      await queryClient.cancelQueries();
      localStorage.clear();
      queryClient.clear();
      queryClient.setQueryData(MEMBER_SESSION_QUERY_KEY, null);
      navigate(ROUTES.login, { replace: true });
    },
    retry: false,
  });
  const close = () => {
    if (mutation.isPending) return;
    setAction(null);
    mutation.reset();
  };

  return (
    <Page>
      <Header>
        <IconButton aria-label="뒤로 가기" onClick={() => navigate(ROUTES.rooms)}>
          <HiArrowLeft />
        </IconButton>
        <Title>설정</Title>
      </Header>
      <Content>
        <Section aria-labelledby="legal-title">
          <SectionTitle id="legal-title">약관</SectionTitle>
          <ItemList>
            <Item type="button" disabled>
              이용약관
              <HiChevronRight aria-hidden="true" />
            </Item>
            <Item type="button" disabled>
              개인정보처리방침
              <HiChevronRight aria-hidden="true" />
            </Item>
          </ItemList>
        </Section>
        <Section aria-labelledby="account-title">
          <SectionTitle id="account-title">계정 관리</SectionTitle>
          <ItemList>
            <Item type="button" onClick={() => setAction("logout")}>
              로그아웃
              <HiChevronRight aria-hidden="true" />
            </Item>
            <Item type="button" $danger onClick={() => setAction("withdraw")}>
              회원탈퇴
              <HiChevronRight aria-hidden="true" />
            </Item>
          </ItemList>
        </Section>
      </Content>
      {action && (
        <Modal onClose={close} showClose={false}>
          <Stack gap={20}>
            <DialogTitle>
              {action === "withdraw" ? "정말 탈퇴할까요?" : "로그아웃할까요?"}
            </DialogTitle>
            <Description>
              {action === "withdraw"
                ? "탈퇴하면 계정을 다시 사용할 수 없고, 내가 만든 방은 접근이 차단돼요."
                : "다시 이용하려면 로그인해 주세요."}
            </Description>
            {mutation.isError && (
              <ErrorMessage role="alert">
                요청을 처리하지 못했어요. 다시 시도해 주세요.
              </ErrorMessage>
            )}
            <Row gap={12}>
              <Button variant="default" disabled={mutation.isPending} onClick={close}>
                취소
              </Button>
              <Button
                variant={action === "withdraw" ? "danger" : "primary"}
                disabled={mutation.isPending}
                onClick={() => mutation.mutate(action)}
              >
                {mutation.isPending ? "처리 중…" : action === "withdraw" ? "탈퇴하기" : "로그아웃"}
              </Button>
            </Row>
          </Stack>
        </Modal>
      )}
    </Page>
  );
};

const Page = styled.main({
  display: "flex",
  flexDirection: "column",
  gap: 32,
  minHeight: "100%",
  backgroundColor: colors.backgroundDefault,
});
const Header = styled.header({
  position: "relative",
  display: "flex",
  alignItems: "center",
  minHeight: 64,
  flexShrink: 0,
  padding: "0 16px",
});
const Title = styled.h1({
  position: "absolute",
  left: "50%",
  transform: "translateX(-50%)",
  color: colors.textStrong,
  ...typography.heading3,
});
const Content = styled.div({
  padding: "0 24px 32px",
  display: "flex",
  flexDirection: "column",
  gap: 48,
  width: "100%",
  boxSizing: "border-box",
  alignSelf: "center",
});
const Section = styled.section({
  display: "flex",
  flexDirection: "column",
  gap: 12,
  "@media (max-width: 767px)": { gap: 8 },
});
const ItemList = styled.div({
  display: "flex",
  flexDirection: "column",
  gap: 8,
});
const SectionTitle = styled.h2({
  color: colors.textSecondary,
  ...typography.label3,
  "@media (max-width: 767px)": {
    ...typography.body,
  },
});
const Item = styled.button<{ $danger?: boolean }>(({ $danger }) => ({
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
  gap: 24,
  width: "100%",
  minHeight: 56,
  padding: 0,
  border: 0,
  borderBottom: `1px solid ${colors.borderDefault}`,
  background: "transparent",
  color: $danger ? colors.danger : colors.textStrong,
  textAlign: "left",
  cursor: "pointer",
  ...typography.label3,
  "&:disabled": { cursor: "default" },
  "& svg": { color: colors.textSecondary, width: 20, height: 20 },
}));
const DialogTitle = styled.h2({ textAlign: "center", ...typography.heading3 });
const Description = styled.p({
  color: colors.textSecondary,
  textAlign: "center",
  ...typography.body,
});
const ErrorMessage = styled.p({
  color: colors.danger,
  textAlign: "center",
  ...typography.caption1,
});
