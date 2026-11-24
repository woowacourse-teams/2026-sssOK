import { Platform } from "react-native";
import { Directory, File, Paths } from "expo-file-system";

export const SHARE_APP_GROUP = "group.com.ssssok.app";

const IOS_FOLDER = "sharedData";
const ANDROID_FOLDER = "share-inbox";

export interface SharedItem {
  localId: string;
  uri: string;
  fileName: string;
  mimeType: string;
  size: number;
}

const MIME_BY_EXTENSION: Record<string, string> = {
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  png: "image/png",
  gif: "image/gif",
  webp: "image/webp",
  heic: "image/heic",
  heif: "image/heif",
  mov: "video/quicktime",
  mp4: "video/mp4",
  m4v: "video/x-m4v",
  "3gp": "video/3gpp",
  webm: "video/webm",
};

const extensionOf = (fileName: string): string => {
  const dot = fileName.lastIndexOf(".");

  return dot < 0 ? "" : fileName.slice(dot + 1).toLowerCase();
};

export const mimeTypeOf = (fileName: string): string =>
  MIME_BY_EXTENSION[extensionOf(fileName)] ?? "application/octet-stream";

export const isVideo = (fileName: string): boolean => mimeTypeOf(fileName).startsWith("video/");

export const fileNameOf = (uri: string): string => {
  const last = uri.split("/").pop() ?? "";

  try {
    return decodeURIComponent(last);
  } catch {
    return last;
  }
};

const inboxDirectory = (): Directory | null => {
  try {
    if (Platform.OS === "ios") {
      const container = Paths.appleSharedContainers[SHARE_APP_GROUP];

      return container ? new Directory(container, IOS_FOLDER) : null;
    }

    return new Directory(Paths.document, ANDROID_FOLDER);
  } catch {
    return null;
  }
};

const RECEIVED_AT = ".received-at";

export const SHARE_TTL_MS = 24 * 60 * 60 * 1000;

const filesIn = (directory: Directory | null): File[] => {
  if (!directory?.exists) return [];

  return directory
    .list()
    .filter((entry): entry is File => entry instanceof File)
    .filter((file) => !fileNameOf(file.uri).startsWith("."));
};

const deleteQuietly = (entry: File): void => {
  try {
    if (entry.exists) entry.delete();
  } catch {}
};

const markReceived = async (directory: Directory): Promise<void> => {
  try {
    const marker = new File(directory, RECEIVED_AT);

    if (!marker.exists) marker.create();
    await marker.write(String(Date.now()));
  } catch {}
};

const isExpired = (directory: Directory, now: number): boolean => {
  try {
    const marker = new File(directory, RECEIVED_AT);
    const receivedAt = marker.exists ? Number(marker.textSync()) : NaN;

    return !Number.isFinite(receivedAt) || now - receivedAt > SHARE_TTL_MS;
  } catch {
    return true;
  }
};

export function listInbox(now: number = Date.now()): SharedItem[] {
  const directory = inboxDirectory();
  const files = filesIn(directory);

  if (directory !== null && files.length > 0 && isExpired(directory, now)) {
    clearInbox();
    return [];
  }

  return files
    .map((file) => {
      const fileName = fileNameOf(file.uri);

      return {
        localId: fileName,
        uri: file.uri,
        fileName,
        mimeType: mimeTypeOf(fileName),
        size: file.size ?? 0,
      };
    })
    .filter((item) => item.size > 0)
    .sort((a, b) => a.fileName.localeCompare(b.fileName));
}

export function findInInbox(localId: string): SharedItem | null {
  return listInbox().find((item) => item.localId === localId) ?? null;
}

export function clearInbox(): void {
  const directory = inboxDirectory();

  filesIn(directory).forEach(deleteQuietly);
  if (directory !== null) deleteQuietly(new File(directory, RECEIVED_AT));
}

export function keepOnlyInInbox(keepUris: string[]): void {
  const keep = new Set(keepUris.map(fileNameOf));
  const sample = keepUris[0];

  if (sample === undefined) return;

  const directory = new File(sample).parentDirectory;

  filesIn(directory)
    .filter((file) => !keep.has(fileNameOf(file.uri)))
    .forEach(deleteQuietly);
  void markReceived(directory);
}

export function removeFromInbox(uris: string[]): void {
  uris.forEach((uri) => deleteQuietly(new File(uri)));
}

export interface IncomingFile {
  uri: string;
  fileName: string;
  mimeType: string;
}

const withExtension = (fileName: string, mimeType: string): string => {
  if (extensionOf(fileName) in MIME_BY_EXTENSION) return fileName;

  const extension = Object.keys(MIME_BY_EXTENSION).find(
    (key) => MIME_BY_EXTENSION[key] === mimeType,
  );

  return extension ? `${fileName}.${extension}` : fileName;
};

const uniqueName = (fileName: string, taken: Set<string>): string => {
  if (!taken.has(fileName)) return fileName;

  const dot = fileName.lastIndexOf(".");
  const base = dot < 0 ? fileName : fileName.slice(0, dot);
  const ext = dot < 0 ? "" : fileName.slice(dot);
  let index = 2;

  while (taken.has(`${base}-${index}${ext}`)) index += 1;

  return `${base}-${index}${ext}`;
};

const safeNameOf = (fileName: string): string => fileName.replace(/[/\\]/g, "_") || "file";

export async function replaceInbox(files: IncomingFile[]): Promise<void> {
  const directory = inboxDirectory();

  if (directory === null) return;

  clearInbox();
  if (!directory.exists) directory.create({ intermediates: true });

  const taken = new Set<string>();

  for (const incoming of files) {
    const name = uniqueName(withExtension(safeNameOf(incoming.fileName), incoming.mimeType), taken);

    taken.add(name);

    try {
      await new File(incoming.uri).copy(new File(directory, name));
    } catch {}
  }

  await markReceived(directory);
}
