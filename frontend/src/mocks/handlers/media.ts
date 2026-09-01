import { http, HttpResponse } from "msw";

import type { MediaDetail } from "@/entities/media";
import { API_BASE_URL } from "@/shared/config";
import { markMediaDeleted, type GalleryMedia } from "../db";
import { hasFolder, hasJoinedRoom, mediaOfRoom, MOCK_HOST_ID, roomStatusOfId } from "./room";

const error = (status: number, code: string, message: string) =>
  HttpResponse.json({ code, message }, { status });

const authorize = (request: Request, roomId: number) => {
  const authorization = request.headers.get("Authorization");
  const match = authorization?.match(/^Bearer mock-token-(\d+)$/);
  if (!authorization || !match) return error(401, "UNAUTHORIZED", "인증이 필요합니다.");
  const status = roomStatusOfId(roomId);
  if (status === null) return error(404, "ROOM_NOT_FOUND", "존재하지 않는 방입니다.");
  if (status !== "ACTIVE")
    return error(410, "ROOM_ALREADY_DELETED", "이미 삭제되었거나 만료된 방입니다.");
  if (!hasJoinedRoom(authorization, roomId))
    return error(403, "NOT_ROOM_MEMBER", "입장한 방에서만 이용할 수 있습니다.");
  return Number(match[1]);
};

// backend FilePermissionPolicy: 업로더 본인 또는 방장만 삭제할 수 있다.
const canDelete = (media: GalleryMedia, memberId: number) =>
  media.uploaderId === memberId || memberId === MOCK_HOST_ID;
const notFound = () => error(404, "MEDIA_NOT_FOUND", "미디어를 찾을 수 없습니다.");
const forbidden = () =>
  error(403, "MEDIA_FORBIDDEN", "다른 사람이 올린 파일이라 삭제할 수 없습니다");

export const mediaHandlers = [
  http.put(`${API_BASE_URL}/rooms/:roomId/media/folders`, async ({ request, params }) => {
    const roomId = Number(params.roomId);
    const auth = authorize(request, roomId);
    if (typeof auth !== "number") return auth;
    const body: unknown = await request.json().catch(() => null);
    if (
      !body ||
      typeof body !== "object" ||
      !("mediaIds" in body) ||
      !Array.isArray(body.mediaIds) ||
      body.mediaIds.length === 0 ||
      (!("folderIds" in body) && !("folderId" in body))
    ) {
      return error(400, "INVALID_PARAM", "미디어와 폴더를 선택해 주세요.");
    }
    const requestedFolderIds =
      "folderIds" in body && Array.isArray(body.folderIds)
        ? body.folderIds
        : "folderId" in body && typeof body.folderId === "number"
          ? [body.folderId]
          : [];
    if (
      requestedFolderIds.length === 0 ||
      !requestedFolderIds.every((id: unknown) => typeof id === "number")
    ) {
      return error(400, "INVALID_PARAM", "미디어와 폴더를 선택해 주세요.");
    }
    const folderIds = [...new Set<number>(requestedFolderIds)];
    if (folderIds.some((folderId) => !hasFolder(roomId, folderId))) {
      return error(404, "FOLDER_NOT_FOUND", "폴더를 찾을 수 없습니다.");
    }

    const mediaById = new Map(mediaOfRoom(roomId).map((media) => [media.mediaId, media]));
    let updatedCount = 0;
    let alreadyInCount = 0;
    const notFoundMediaIds: number[] = [];
    for (const mediaId of [...new Set<number>(body.mediaIds)]) {
      const media = mediaById.get(mediaId);
      if (!media) {
        notFoundMediaIds.push(mediaId);
      } else {
        for (const folderId of folderIds) {
          if (media.folderIds.includes(folderId)) {
            alreadyInCount += 1;
          } else {
            media.folderIds.push(folderId);
            updatedCount += 1;
          }
        }
      }
    }

    const folders = folderIds.map((folderId) => ({
      id: folderId,
      name: "폴더",
      photoCount: mediaOfRoom(roomId).filter((media) => media.folderIds.includes(folderId)).length,
    }));
    const data = { updatedCount, alreadyInCount, notFoundMediaIds };

    return HttpResponse.json({
      data: "folderId" in body ? { ...data, folder: folders[0] } : { ...data, folders },
    });
  }),

  http.delete(`${API_BASE_URL}/rooms/:roomId/media/folders`, async ({ request, params }) => {
    const roomId = Number(params.roomId);
    const auth = authorize(request, roomId);
    if (typeof auth !== "number") return auth;
    const body: unknown = await request.json().catch(() => null);
    if (
      !body ||
      typeof body !== "object" ||
      !("mediaIds" in body) ||
      !Array.isArray(body.mediaIds) ||
      body.mediaIds.length === 0 ||
      !("folderIds" in body) ||
      !Array.isArray(body.folderIds) ||
      body.folderIds.length === 0
    ) {
      return error(400, "INVALID_PARAM", "미디어와 폴더를 선택해 주세요.");
    }

    const folderIds = [...new Set<number>(body.folderIds)];
    if (folderIds.some((folderId) => !hasFolder(roomId, folderId))) {
      return error(404, "FOLDER_NOT_FOUND", "폴더를 찾을 수 없습니다.");
    }

    const mediaById = new Map(mediaOfRoom(roomId).map((media) => [media.mediaId, media]));
    let updatedCount = 0;
    const movedToRootMediaIds: number[] = [];
    const notFoundMediaIds: number[] = [];
    for (const mediaId of [...new Set<number>(body.mediaIds)]) {
      const media = mediaById.get(mediaId);
      if (!media) {
        notFoundMediaIds.push(mediaId);
        continue;
      }
      const before = media.folderIds.length;
      media.folderIds = media.folderIds.filter((folderId) => !folderIds.includes(folderId));
      updatedCount += before - media.folderIds.length;
      if (before > 0 && media.folderIds.length === 0) movedToRootMediaIds.push(mediaId);
    }

    return HttpResponse.json({
      data: {
        updatedCount,
        movedToRootMediaIds,
        notFoundMediaIds,
        folders: folderIds.map((folderId) => ({
          id: folderId,
          name: "폴더",
          photoCount: mediaOfRoom(roomId).filter((media) => media.folderIds.includes(folderId))
            .length,
        })),
      },
    });
  }),

  http.get(`${API_BASE_URL}/rooms/:roomId/media/:mediaId`, ({ request, params }) => {
    const roomId = Number(params.roomId);
    const auth = authorize(request, roomId);
    if (typeof auth !== "number") return auth;
    const media = mediaOfRoom(roomId).find((item) => item.mediaId === Number(params.mediaId));
    if (!media) return notFound();

    const data: MediaDetail = {
      mediaId: media.mediaId,
      type: media.type,
      fileName: media.fileName,
      mimeType: media.mimeType,
      size: media.size,
      originalUrl: media.originalUrl,
      width: media.width,
      height: media.height,
      duration: media.duration,
      folderIds: media.folderIds,
      uploaderId: media.uploaderId,
      uploaderName: media.uploaderName,
      status: media.status,
      uploadedAt: media.uploadedAt,
      takenAt: media.mediaId === 5012 ? "2026-08-17T14:02:11+09:00" : null,
      location:
        media.mediaId === 5012
          ? { latitude: 35.1587, longitude: 129.1604, name: "부산 해운대구" }
          : null,
      canDelete: canDelete(media, auth),
    };
    return HttpResponse.json({ data });
  }),

  http.delete(`${API_BASE_URL}/rooms/:roomId/media/:mediaId`, ({ request, params }) => {
    const roomId = Number(params.roomId);
    const auth = authorize(request, roomId);
    if (typeof auth !== "number") return auth;
    const media = mediaOfRoom(roomId).find((item) => item.mediaId === Number(params.mediaId));
    if (!media) return notFound();
    if (!canDelete(media, auth)) return forbidden();
    markMediaDeleted(roomId, media.mediaId);
    return new HttpResponse(null, { status: 204 });
  }),

  http.delete(`${API_BASE_URL}/rooms/:roomId/media`, async ({ request, params }) => {
    const roomId = Number(params.roomId);
    const auth = authorize(request, roomId);
    if (typeof auth !== "number") return auth;
    const body: unknown = await request.json().catch(() => null);
    if (
      !body ||
      typeof body !== "object" ||
      !("mediaIds" in body) ||
      !Array.isArray(body.mediaIds) ||
      body.mediaIds.length === 0 ||
      !body.mediaIds.every(
        (id: unknown) => typeof id === "number" && Number.isSafeInteger(id) && id > 0,
      )
    ) {
      return error(400, "INVALID_MEDIA_IDS", "삭제할 미디어 ID 목록을 확인해 주세요.");
    }
    const mediaIds = [...new Set<number>(body.mediaIds)];
    const deleted: number[] = [];
    const skipped: { mediaId: number; code: string; message: string }[] = [];
    const mediaById = new Map(mediaOfRoom(roomId).map((media) => [media.mediaId, media]));
    for (const mediaId of mediaIds) {
      const media = mediaById.get(mediaId);
      if (!media) {
        skipped.push({ mediaId, code: "MEDIA_NOT_FOUND", message: "미디어를 찾을 수 없습니다." });
      } else if (!canDelete(media, auth)) {
        skipped.push({
          mediaId,
          code: "MEDIA_FORBIDDEN",
          message: "다른 사람이 올린 파일이라 삭제할 수 없습니다",
        });
      } else {
        markMediaDeleted(roomId, mediaId);
        deleted.push(mediaId);
      }
    }
    return HttpResponse.json({ data: { deleted, skipped, deletedCount: deleted.length } });
  }),
];
