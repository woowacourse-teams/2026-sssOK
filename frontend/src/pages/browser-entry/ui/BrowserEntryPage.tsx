import { useEffect, useRef } from "react";
import { Navigate, useLocation, useNavigate, useParams } from "react-router-dom";

import mascot from "@/shared/assets/mascot-invite.png";
import { ROUTES } from "@/shared/config";
import { isAppWebView } from "@/shared/lib/isAppWebView";
import { Button } from "@/shared/ui/button";
import { Stack } from "@/shared/ui/stack";
import { buildInviteAppUrl, isMobileBrowser, openInviteApp } from "../lib/browserEntry";
import { Actions, Content, Description, Image, Page, Title } from "./BrowserEntryPage.styles";

export const BrowserEntryPage = () => {
  const { code = "" } = useParams<{ code: string }>();
  const { search, hash } = useLocation();
  const navigate = useNavigate();
  const attempted = useRef("");
  const entry = ROUTES.roomEntry(encodeURIComponent(code)) + search + hash;
  const invite = new URL(
    ROUTES.roomInvite(encodeURIComponent(code)) + search + hash,
    window.location.origin,
  );
  const tryApp = !isAppWebView() && /^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$/.test(code);
  const appUrl = buildInviteAppUrl(invite, navigator.userAgent);
  useEffect(() => {
    if (
      !tryApp ||
      !isMobileBrowser(navigator.userAgent, navigator.maxTouchPoints) ||
      attempted.current === appUrl
    )
      return;
    attempted.current = appUrl;
    try {
      const attemptKey = "sssok:last-invite-app-attempt";
      if (sessionStorage.getItem(attemptKey) === appUrl) return;
      sessionStorage.setItem(attemptKey, appUrl);
    } catch {
      return;
    }
    openInviteApp(appUrl);
  }, [appUrl, tryApp]);

  if (!tryApp) return <Navigate to={entry} replace />;

  return (
    <Page>
      <Content>
        <Image src={mascot} alt="초대 링크를 안내하는 쏙 캐릭터" />
        <Stack gap={8} align="center">
          <Title>쏙 방에 초대됐어요</Title>
          <Description>{"앱이나 웹에서 참여하고\n함께한 사진을 모아보세요"}</Description>
        </Stack>
      </Content>
      <Actions>
        <Button variant="primary" size="lg" onClick={() => openInviteApp(appUrl)}>
          쏙 앱에서 열기
        </Button>
        <Button variant="default" size="lg" onClick={() => navigate(entry, { replace: true })}>
          웹에서 참여하기
        </Button>
      </Actions>
    </Page>
  );
};
