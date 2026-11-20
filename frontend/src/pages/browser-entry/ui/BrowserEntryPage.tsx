import { useEffect, useRef } from "react";
import { FiLink } from "react-icons/fi";
import { FaApple, FaGooglePlay } from "react-icons/fa";
import { Navigate, useLocation, useNavigate, useParams } from "react-router-dom";

import mascot from "@/shared/assets/mascot-invite.png";
import { ROUTES } from "@/shared/config";
import { isAppWebView } from "@/shared/lib/isAppWebView";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Stack } from "@/shared/ui/stack";
import { buildInviteAppUrl, isMobileBrowser, openInviteApp } from "../lib/browserEntry";
import {
  Actions,
  Content,
  Description,
  Image,
  InstallLabel,
  Page,
  StoreLinks,
  StoreLink,
  Title,
} from "./BrowserEntryPage.styles";

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
  const mobile = isMobileBrowser(navigator.userAgent, navigator.maxTouchPoints);
  const tryApp = mobile && !isAppWebView() && /^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$/.test(code);
  const appUrl = buildInviteAppUrl(invite, navigator.userAgent);
  useEffect(() => {
    if (!tryApp || attempted.current === appUrl) return;
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
        <InstallLabel>
          <Badge variant="soft">추천</Badge>앱으로 더 편하게 이용해 보세요
        </InstallLabel>
        <StoreLinks>
          <StoreLink href="https://play.google.com/store/apps/details?id=com.ssssok.app&pcampaignid=web_share">
            <FaGooglePlay aria-hidden="true" />
            Google Play
          </StoreLink>
          <StoreLink href="https://apps.apple.com/kr/app/%EC%8F%99-%ED%95%A8%EA%BB%98-%EB%AA%A8%EC%9C%BC%EB%8A%94-%EC%82%AC%EC%A7%84/id6828711923">
            <FaApple aria-hidden="true" />
            App Store
          </StoreLink>
        </StoreLinks>
        <Button variant="primary" size="lg" onClick={() => navigate(entry, { replace: true })}>
          <FiLink aria-hidden="true" />
          웹으로 참여하기
        </Button>
      </Actions>
    </Page>
  );
};
