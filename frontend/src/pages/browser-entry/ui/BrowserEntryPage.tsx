import { useEffect, useRef } from "react";
import { Navigate, useLocation, useNavigate, useParams } from "react-router-dom";

import mascot from "@/shared/assets/mascot-wave.png";
import { ROUTES } from "@/shared/config";
import { isAppWebView } from "@/shared/lib/isAppWebView";
import { Button } from "@/shared/ui/button";
import {
  buildChromeIntentUrl,
  openChrome,
  shouldOpenInChrome,
  buildInviteAppUrl,
  isMobileBrowser,
  openInviteApp,
} from "../lib/browserEntry";
import { ActionButton, Actions, Content, Image, Page, Title } from "./BrowserEntryPage.styles";

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
  const tryApp =
    !isAppWebView() &&
    isMobileBrowser(navigator.userAgent, navigator.maxTouchPoints) &&
    /^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$/.test(code);
  const appUrl = buildInviteAppUrl(invite, navigator.userAgent);
  const attemptKey = "sssok:last-invite-app-attempt";

  useEffect(() => {
    if (!tryApp || attempted.current === appUrl) return;
    attempted.current = appUrl;
    try {
      if (sessionStorage.getItem(attemptKey) === appUrl) return;
      sessionStorage.setItem(attemptKey, appUrl);
    } catch {
      // 저장소가 차단된 환경에서는 자동 재진입 대신 버튼으로 앱을 연다.
      return;
    }
    openInviteApp(appUrl);
  }, [appUrl, tryApp]);

  if (!tryApp) return <Navigate to={entry} replace />;

  return (
    <Page>
      <Content>
        <Image src={mascot} alt="" />
        <Title>쏙 방에 초대됐어요</Title>
      </Content>
      <Actions>
        <ActionButton onClick={() => openInviteApp(appUrl)}>쏙 앱에서 열기</ActionButton>
        <Button variant="default" size="lg" onClick={() => navigate(entry, { replace: true })}>
          웹에서 참여하기
        </Button>
        {shouldOpenInChrome(navigator.userAgent) && (
          <Button
            variant="default"
            onClick={() => openChrome(buildChromeIntentUrl(new URL(entry, invite.origin), invite))}
          >
            Chrome에서 계속하기
          </Button>
        )}
      </Actions>
    </Page>
  );
};
