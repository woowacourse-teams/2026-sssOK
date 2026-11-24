import { useMutation, type UseMutationResult } from "@tanstack/react-query";
import type { GalleryItem } from "@/entities/media";
import { downloadMedia, savesToPhotoLibrary } from "@/features/download-media";
import { useToast } from "@/shared/ui/toast";

export const useViewerDownload = ({
  roomId,
  item,
  token,
}: {
  roomId: number;
  item: GalleryItem;
  token: string | undefined;
}): UseMutationResult<void, Error, void> => {
  const { showToast } = useToast();
  const downloadTarget =
    item.type === "local"
      ? {
          mediaId: item.mediaId,
          fileName: item.file.name,
          size: item.file.size,
          mimeType: item.file.type,
        }
      : {
          mediaId: item.mediaId,
          fileName: item.media.fileName,
          size: item.media.size,
          mimeType: item.media.mimeType,
        };
  const download = useMutation({
    mutationFn: async () => {
      if (!token) throw new Error("로그인 정보를 확인하지 못했어요. 다시 입장해 주세요.");
      const toPhotoLibrary = savesToPhotoLibrary();
      const outcome = await downloadMedia({
        roomId,
        targets: [downloadTarget],
        mode: toPhotoLibrary ? "share" : "individual",
        token,
      });

      if (outcome.type === "failed") throw new Error(outcome.reason);
      if (outcome.type === "empty") throw new Error("사진을 다운로드하지 못했어요.");

      if (outcome.type === "saved") {
        showToast({
          message: toPhotoLibrary ? "사진첩에 저장했어요." : "사진을 저장했어요.",
        });
      }
    },
  });

  return download;
};
