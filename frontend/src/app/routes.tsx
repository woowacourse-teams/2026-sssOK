import { Navigate, type RouteObject } from "react-router-dom";

import { CreateRoomPage } from "@/pages/create-room";
import { GalleryPage } from "@/pages/gallery";
import { HomePage } from "@/pages/home";
import { MediaDetailPage } from "@/pages/media-detail";
import { RoomEntryPage } from "@/pages/room-entry";
import { RoomSettingsPage } from "@/pages/room-settings";
import { ROUTE_PATTERNS, ROUTES } from "@/shared/config";
import { RoomMediaLayout } from "./RoomMediaLayout";

export const routes: RouteObject[] = [
  { path: ROUTE_PATTERNS.home, element: <HomePage /> },
  { path: ROUTE_PATTERNS.createRoom, element: <CreateRoomPage /> },
  {
    path: ROUTE_PATTERNS.roomEntry,
    element: <RoomMediaLayout />,
    children: [
      { index: true, element: <RoomEntryPage /> },
      { path: ROUTE_PATTERNS.gallery, element: <GalleryPage /> },
      { path: ROUTE_PATTERNS.mediaDetail, element: <MediaDetailPage /> },
      { path: ROUTE_PATTERNS.roomSettings, element: <RoomSettingsPage /> },
    ],
  },
  // 알 수 없는 주소는 홈으로 안내한다 (screens/001-home.md)
  { path: "*", element: <Navigate to={ROUTES.home} replace /> },
];
