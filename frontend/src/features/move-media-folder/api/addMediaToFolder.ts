import { apiClient } from "@/shared/api";

interface AddMediaToFolderResponse {
  updatedCount: number;
  alreadyInCount: number;
  notFoundMediaIds: number[];
  folders: {
    id: number;
    name: string;
    photoCount: number;
  }[];
}

interface LegacyAddMediaToFolderResponse {
  updatedCount: number;
  alreadyInCount: number;
  notFoundMediaIds: number[];
  folder: AddMediaToFolderResponse["folders"][number];
}

export const addMediaToFolder = async ({
  roomId,
  mediaIds,
  folderIds,
  token,
}: {
  roomId: number;
  mediaIds: number[];
  folderIds: number[];
  token: string;
}) => {
  // 현재 배포 서버는 folderId 단건 명세다. 다중 선택은 폴더별 요청으로 처리한다.
  const results = await Promise.all(
    folderIds.map((folderId) =>
      apiClient<LegacyAddMediaToFolderResponse>(`/rooms/${roomId}/media/folders`, {
        method: "PUT",
        token,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ mediaIds, folderId }),
      }),
    ),
  );

  return {
    updatedCount: results.reduce((sum, result) => sum + result.updatedCount, 0),
    alreadyInCount: results.reduce((sum, result) => sum + result.alreadyInCount, 0),
    notFoundMediaIds: [...new Set(results.flatMap((result) => result.notFoundMediaIds))],
    folders: results.map((result) => result.folder),
  } satisfies AddMediaToFolderResponse;
};
