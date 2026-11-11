import { Navigate, Outlet, type RouteObject } from "react-router-dom";

// 초대 링크로 들어온 게스트가 처음 보는 화면만 첫 번들에 둔다. 나머지 페이지는 열 때 받아 온다.
// 삭제된 방 안내는 방 입장 화면이 직접 띄우므로 함께 둔다.
import { BrowserEntryPage } from "@/pages/browser-entry";
import { RoomClosedPage } from "@/pages/room-closed";
import { RoomEntryPage } from "@/pages/room-entry";
import { ROUTE_PATTERNS, ROUTES } from "@/shared/config";
import { RedirectIfMember } from "./RedirectIfMember";
import { RequireMember } from "./RequireMember";
import { AdminRoute } from "./AdminRoute";
import { lazyPage } from "./lazyPage";
import { RoomMediaLayout } from "./RoomMediaLayout";
import { RouteErrorPage } from "./RouteErrorPage";
import { useUnauthorizedRecovery } from "./useUnauthorizedRecovery";

const PrivacyPolicyPage = lazyPage(
  () => import("@/pages/legal"),
  (m) => m.PrivacyPolicyPage,
);
const TermsOfServicePage = lazyPage(
  () => import("@/pages/legal"),
  (m) => m.TermsOfServicePage,
);
const LoginCallbackPage = lazyPage(
  () => import("@/pages/login-callback"),
  (m) => m.LoginCallbackPage,
);
const LoginPage = lazyPage(
  () => import("@/pages/login"),
  (m) => m.LoginPage,
);
const HomePage = lazyPage(
  () => import("@/pages/home"),
  (m) => m.HomePage,
);
const RoomListPage = lazyPage(
  () => import("@/pages/room-list"),
  (m) => m.RoomListPage,
);
const AccountSettingsPage = lazyPage(
  () => import("@/pages/account-settings"),
  (m) => m.AccountSettingsPage,
);
const CreateRoomPage = lazyPage(
  () => import("@/pages/create-room"),
  (m) => m.CreateRoomPage,
);
// 이름을 입력한 게스트는 곧바로 갤러리로 가므로, 첫 화면을 띄운 뒤 쉬는 틈에 미리 받아 둔다.
const GalleryPage = lazyPage(
  () => import(/* webpackPrefetch: true */ "@/pages/gallery"),
  (m) => m.GalleryPage,
);
const MediaDetailPage = lazyPage(
  () => import("@/pages/media-detail"),
  (m) => m.MediaDetailPage,
);
const RoomSettingsPage = lazyPage(
  () => import("@/pages/room-settings"),
  (m) => m.RoomSettingsPage,
);
const AdminLoginPage = lazyPage(
  () => import("@/pages/admin-login"),
  (m) => m.AdminLoginPage,
);
const AdminFeedbacksPage = lazyPage(
  () => import("@/pages/admin-feedbacks"),
  (m) => m.AdminFeedbacksPage,
);
const AdminAccountsPage = lazyPage(
  () => import("@/pages/admin-accounts"),
  (m) => m.AdminAccountsPage,
);

const pageRoutes: RouteObject[] = [
  { path: ROUTE_PATTERNS.privacy, element: PrivacyPolicyPage },
  { path: ROUTE_PATTERNS.terms, element: TermsOfServicePage },
  { path: ROUTE_PATTERNS.loginCallback, element: LoginCallbackPage },
  {
    element: <RedirectIfMember />,
    children: [{ path: ROUTE_PATTERNS.login, element: LoginPage }],
  },
  { path: ROUTE_PATTERNS.home, element: HomePage },
  {
    element: <RequireMember />,
    children: [
      { path: ROUTE_PATTERNS.rooms, element: RoomListPage },
      { path: ROUTE_PATTERNS.accountSettings, element: AccountSettingsPage },
    ],
  },
  { path: ROUTE_PATTERNS.createRoom, element: CreateRoomPage },
  { path: ROUTE_PATTERNS.closedRoom, element: <RoomClosedPage /> },
  { path: ROUTE_PATTERNS.roomInvite, element: <BrowserEntryPage /> },
  {
    path: ROUTE_PATTERNS.roomEntry,
    element: <RoomMediaLayout />,
    children: [
      { index: true, element: <RoomEntryPage /> },
      { path: ROUTE_PATTERNS.gallery, element: GalleryPage },
      { path: ROUTE_PATTERNS.mediaDetail, element: MediaDetailPage },
      { path: ROUTE_PATTERNS.roomSettings, element: RoomSettingsPage },
    ],
  },
  { path: ROUTE_PATTERNS.adminLogin, element: AdminLoginPage },
  {
    // 로그인 화면 자신은 이 문 밖에 있어야 한다 — 안에 두면 되돌림이 끝없이 돈다.
    element: <AdminRoute />,
    children: [
      { path: ROUTE_PATTERNS.admin, element: <Navigate to={ROUTES.adminFeedbacks} replace /> },
      { path: ROUTE_PATTERNS.adminFeedbacks, element: AdminFeedbacksPage },
      { path: ROUTE_PATTERNS.adminAccounts, element: AdminAccountsPage },
    ],
  },
  // 알 수 없는 주소는 홈으로 안내한다 (screens/001-home.md)
  { path: "*", element: <Navigate to={ROUTES.home} replace /> },
];

const AuthRecoveryLayout = () => {
  useUnauthorizedRecovery();
  return <Outlet />;
};

// 화면 코드를 받지 못하거나 렌더링 중 오류가 나면 기본 오류 화면 대신 안내 화면을 띄운다.
export const routes: RouteObject[] = [
  ...pageRoutes
    .filter(
      (route) =>
        route.path === ROUTE_PATTERNS.adminLogin ||
        route.children?.some((child) => child.path === ROUTE_PATTERNS.admin),
    )
    .map((route) => ({ ...route, errorElement: <RouteErrorPage /> })),
  {
    element: <AuthRecoveryLayout />,
    errorElement: <RouteErrorPage />,
    children: pageRoutes.filter(
      (route) =>
        route.path !== ROUTE_PATTERNS.adminLogin &&
        !route.children?.some((child) => child.path === ROUTE_PATTERNS.admin),
    ),
  },
];
