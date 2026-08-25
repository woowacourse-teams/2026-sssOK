import { Navigate, type RouteObject } from "react-router-dom";

import { GalleryPage } from "@/pages/gallery";
import { HomePage } from "@/pages/home";
import { RoomEntryPage } from "@/pages/room-entry";
import { ROUTE_PATTERNS, ROUTES } from "@/shared/config";

export const routes: RouteObject[] = [
  { path: ROUTE_PATTERNS.home, element: <HomePage /> },
  { path: ROUTE_PATTERNS.roomEntry, element: <RoomEntryPage /> },
  { path: ROUTE_PATTERNS.gallery, element: <GalleryPage /> },
  // 알 수 없는 주소는 홈으로 안내한다 (screens/001-home.md)
  { path: "*", element: <Navigate to={ROUTES.home} replace /> },
];
