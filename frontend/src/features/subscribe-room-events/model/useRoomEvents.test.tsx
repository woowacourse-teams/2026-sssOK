import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";

import { photosQueryKey, type Media, type MediaList } from "@/entities/media";
import type { OrganizeJobUpdatedEvent, OrganizeRoomChangedEvent } from "./roomEventTypes";
import { useRoomEvents } from "./useRoomEvents";

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  private listeners = new Map<string, Set<(event: MessageEvent<string>) => void>>();

  constructor(public url: URL) {
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void) {
    if (!this.listeners.has(type)) this.listeners.set(type, new Set());
    this.listeners.get(type)!.add(listener);
  }

  removeEventListener(type: string, listener: (event: MessageEvent<string>) => void) {
    this.listeners.get(type)?.delete(listener);
  }

  close() {}

  emit(type: string, data?: unknown) {
    const event = new MessageEvent(type, { data: JSON.stringify(data) });
    this.listeners.get(type)?.forEach((listener) => listener(event));
  }
}

const renderRoomEvents = (callbacks: {
  onUploadRequested?: (event: import("./roomEventTypes").UploadRequestedEvent) => void;
  onOrganizeRoomChanged?: (event: OrganizeRoomChangedEvent) => void;
  onOrganizeJobUpdated?: (event: OrganizeJobUpdatedEvent) => void;
  onReconnected?: () => void;
  onMediaCreated?: (media: Media) => void;
  onFoldersChanged?: () => void;
  onFolderDeleted?: (folderId: number) => void;
}) => {
  const queryClient = new QueryClient();
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  const view = renderHook(
    () => useRoomEvents({ roomId: 1, userId: 10234, token: "token", ...callbacks }),
    { wrapper: Wrapper },
  );
  const source = FakeEventSource.instances[FakeEventSource.instances.length - 1];
  return { ...view, source, queryClient };
};

beforeEach(() => {
  FakeEventSource.instances = [];
  Object.defineProperty(globalThis, "EventSource", {
    value: FakeEventSource,
    configurable: true,
    writable: true,
  });
});

const processingMedia: Media = {
  mediaId: 10,
  type: "IMAGE",
  fileName: "photo.jpg",
  mimeType: "image/jpeg",
  size: 100,
  thumbnailUrl: null,
  displayUrl: "https://example.com/display.jpg",
  width: null,
  height: null,
  duration: null,
  folderIds: [],
  uploaderId: 2,
  uploaderName: "다른 사용자",
  status: "PROCESSING",
  uploadedAt: "2026-09-29T00:00:00Z",
};

describe("useRoomEvents 미디어 생성 이벤트", () => {
  it("media.created를 받으면 처리 중 미디어를 넘긴다", () => {
    const onMediaCreated = jest.fn();
    const { source } = renderRoomEvents({ onMediaCreated });

    act(() => source.emit("media.created", processingMedia));

    expect(onMediaCreated).toHaveBeenCalledWith(processingMedia);
  });
});

describe("useRoomEvents 폴더 이벤트", () => {
  it.each([
    [
      "folder.created",
      { roomId: 1, folderId: 7, name: "1일차", createdAt: "2026-09-28T00:00:00Z" },
    ],
    ["folder.renamed", { roomId: 1, folderId: 7, name: "2일차" }],
    ["folder.deleted", { roomId: 1, folderId: 7 }],
  ])("%s 를 받으면 폴더 목록이 바뀌었다고 알린다", (type, payload) => {
    const onFoldersChanged = jest.fn();
    const { source } = renderRoomEvents({ onFoldersChanged });

    act(() => source.emit(type, payload));

    expect(onFoldersChanged).toHaveBeenCalledTimes(1);
  });

  it("folder.deleted 를 받으면 지워진 폴더 id 를 넘긴다", () => {
    const onFolderDeleted = jest.fn();
    const { source } = renderRoomEvents({ onFolderDeleted });

    act(() => source.emit("folder.deleted", { roomId: 1, folderId: 7 }));

    expect(onFolderDeleted).toHaveBeenCalledWith(7);
  });

  it("구독을 끊으면 폴더 이벤트를 더 받지 않는다", () => {
    const onFoldersChanged = jest.fn();
    const { source, unmount } = renderRoomEvents({ onFoldersChanged });

    unmount();
    source.emit("folder.created", { roomId: 1, folderId: 7, name: "1일차" });

    expect(onFoldersChanged).not.toHaveBeenCalled();
  });
});

describe("useRoomEvents 좋아요 이벤트", () => {
  it("media.likes.updated를 받으면 목록 캐시의 좋아요 수만 갱신한다", () => {
    const { source, queryClient } = renderRoomEvents({});
    queryClient.setQueryData<MediaList>(photosQueryKey(1, 10234), {
      items: [
        {
          ...processingMedia,
          status: "READY",
          thumbnailUrl: "thumb",
          displayUrl: "display",
          width: 1,
          height: 1,
          likeCount: 1,
          likedByMe: true,
        },
      ],
    });

    act(() => source.emit("media.likes.updated", { roomId: 1, mediaId: 10, likeCount: 4 }));

    expect(queryClient.getQueryData<MediaList>(photosQueryKey(1, 10234))?.items[0]).toMatchObject({
      likeCount: 4,
      likedByMe: true,
    });
  });
});

describe("useRoomEvents 사진 정리 이벤트", () => {
  it("방 점유와 내 작업 이벤트를 받은 그대로 넘긴다", () => {
    const onOrganizeRoomChanged = jest.fn();
    const onOrganizeJobUpdated = jest.fn();
    const { source } = renderRoomEvents({ onOrganizeRoomChanged, onOrganizeJobUpdated });

    source.emit("organize.room.changed", { roomId: 1, roomVersion: 8, busy: true });
    source.emit("organize.job.updated", { jobId: "job", jobVersion: 3, status: "RUNNING" });

    expect(onOrganizeRoomChanged).toHaveBeenCalledWith({ roomId: 1, roomVersion: 8, busy: true });
    expect(onOrganizeJobUpdated).toHaveBeenCalledWith({
      jobId: "job",
      jobVersion: 3,
      status: "RUNNING",
    });
  });

  it("끊겼다 다시 붙었을 때만 재연결을 알린다", () => {
    const onReconnected = jest.fn();
    const { source } = renderRoomEvents({ onReconnected });

    source.emit("open");
    expect(onReconnected).not.toHaveBeenCalled();

    source.emit("error");
    source.emit("open");
    expect(onReconnected).toHaveBeenCalledTimes(1);
  });
});

it("사진 요청은 다른 참여자의 현재 방 이벤트만 전달하고 구독 해제 후에는 전달하지 않는다", () => {
  const onUploadRequested = jest.fn();
  const { source, unmount } = renderRoomEvents({ onUploadRequested });
  const event = {
    roomId: 1,
    roomCode: "ABCDEFG2",
    requesterId: 2,
    title: "사진 요청",
    body: "사진을 올려주세요",
  };
  act(() => {
    source.emit("upload.requested", { ...event, requesterId: 10234 });
    source.emit("upload.requested", { ...event, roomId: 9 });
    source.emit("upload.requested", event);
  });
  expect(onUploadRequested).toHaveBeenCalledTimes(1);
  expect(onUploadRequested).toHaveBeenCalledWith(event);
  unmount();
  act(() => source.emit("upload.requested", event));
  expect(onUploadRequested).toHaveBeenCalledTimes(1);
});
