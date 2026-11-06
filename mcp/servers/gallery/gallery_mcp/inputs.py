import hashlib
import json
import os
import stat
from contextlib import contextmanager, ExitStack
from pathlib import Path
from urllib.parse import urlsplit
from uuid import UUID

import httpx
from pydantic import BaseModel, ConfigDict, Field

from .config import Settings


class Photo(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    mediaId: int = Field(gt=0)
    source: str = Field(min_length=1, max_length=8192)
    extension: str = Field(pattern=r'^(jpg|jpeg|png|webp|heic|heif|tif|tiff|bmp)$')

    @property
    def filename(self):
        return f'media_{self.mediaId:012d}.{self.extension}'


def source_kind(photo: Photo, settings: Settings):
    url = urlsplit(photo.source)
    if url.scheme or url.netloc:
        if (url.scheme != 'https' or url.hostname not in settings.download_hosts
                or url.port not in (None, 443) or url.username or url.password or url.fragment):
            raise ValueError('Photo URL must use HTTPS on an allowed storage host')
        return 'https'
    if settings.input_root is None:
        raise ValueError('Local photo input is disabled')
    with _open_local(settings.input_root, photo.source):
        pass
    return 'local'


def validate(room_id, request_id, photos, settings):
    if room_id < 1 or str(UUID(request_id)) != request_id:
        raise ValueError('Positive room ID and canonical request UUID are required')
    if not photos or len(photos) > settings.max_photos or len({p.mediaId for p in photos}) != len(photos):
        raise ValueError('Photo snapshot must be nonempty, bounded and contain distinct media IDs')
    for photo in photos:
        source_kind(photo, settings)


def fingerprint(photos):
    # Signed URL refreshes do not change the immutable photo snapshot identity.
    normalized = []
    for photo in sorted(photos, key=lambda p: p.mediaId):
        url = urlsplit(photo.source)
        source = url._replace(query='').geturl() if url.scheme else photo.source
        normalized.append([photo.mediaId, source, photo.extension])
    return hashlib.sha256(json.dumps(normalized).encode()).hexdigest()


def stage_photos(photos, target, settings, on_photo=None):
    """Copy every photo into the job snapshot. on_photo(done) runs after each completed photo."""
    target.mkdir()
    total = 0
    done = 0
    with httpx.Client(timeout=30, follow_redirects=False, trust_env=False) as client:
        for photo in photos:
            kind = source_kind(photo, settings)
            path = target / photo.filename
            size = 0
            if kind == 'local':
                # Copy into the job snapshot, so later source changes cannot alter analysis.
                with _open_local(settings.input_root, photo.source) as source, path.open('xb') as output:
                    while chunk := source.read(64 * 1024):
                        size += len(chunk); total += len(chunk)
                        _require_size(size, total, settings)
                        output.write(chunk)
            else:
                with client.stream('GET', photo.source) as response:
                    response.raise_for_status()
                    if response.is_redirect:
                        raise ValueError('Storage redirects are not allowed')
                    with path.open('xb') as output:
                        for chunk in response.iter_bytes(64 * 1024):
                            size += len(chunk); total += len(chunk)
                            _require_size(size, total, settings)
                            output.write(chunk)
            if not size:
                raise ValueError('Empty photo input')
            done += 1
            if on_photo:
                on_photo(done)


@contextmanager
def _open_local(root, source):
    relative = Path(source)
    if relative.is_absolute() or not relative.parts or '..' in relative.parts:
        raise ValueError('Photo must be a relative path inside the input directory')
    with ExitStack() as stack:
        try:
            # Pin each directory by descriptor; never follow a replaced symlink.
            directory_flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
            directory = os.open(root, directory_flags)
            stack.callback(os.close, directory)
            for part in relative.parts[:-1]:
                directory = os.open(part, directory_flags, dir_fd=directory)
                stack.callback(os.close, directory)
            descriptor = os.open(relative.parts[-1], os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK,
                                 dir_fd=directory)
            stream = stack.enter_context(os.fdopen(descriptor, 'rb'))
            if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
                raise ValueError('Local photo must be a regular file')
        except OSError:
            raise ValueError('Local photo is unavailable or contains a symbolic link') from None
        yield stream


def _require_size(size, total, settings):
    if size > settings.max_photo_bytes or total > settings.max_job_bytes:
        raise ValueError('Photo byte limit exceeded')
