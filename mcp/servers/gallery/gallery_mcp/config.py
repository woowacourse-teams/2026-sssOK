from dataclasses import dataclass
import os
import json
from pathlib import Path
from urllib.parse import urlsplit

import httpx


@dataclass(frozen=True)
class Settings:
    data: Path
    assets: Path
    token: str
    input_root: Path | None = None
    download_hosts: tuple[str, ...] = ()
    allowed_hosts: tuple[str, ...] = ('127.0.0.1:*', 'localhost:*')
    max_photos: int = 200
    max_queue: int = 8
    max_photo_bytes: int = 30 * 1024 * 1024
    max_job_bytes: int = 300 * 1024 * 1024
    timeout: int = 600
    threads: int = 2
    backend_url: str | None = None
    backend_token: str = ""
    standalone: bool = True
    receipt_retention_seconds: int = 86400
    max_receipts: int = 1000
    routes: dict | None = None
    # Running delivery: changed progress is sent at most this often (latest value wins);
    # unchanged progress is only a liveness signal at the heartbeat interval.
    progress_interval_seconds: float = 2
    heartbeat_interval_seconds: float = 15

    def __post_init__(self):
        if self.routes is not None:
            if not isinstance(self.routes, dict) or set(self.routes) != {'dev', 'prod'}:
                raise ValueError('Configure exactly dev and prod routes')
            tokens = []
            for route in self.routes.values():
                if not isinstance(route, dict) or set(route) != {'token', 'backend_url', 'backend_token'} or not all(isinstance(v, str) for v in route.values()):
                    raise ValueError('Invalid environment route configuration')
                Settings(data=self.data, assets=self.assets, standalone=False, **route)
                tokens.extend((route['token'], route['backend_token']))
            if self.routes['dev']['backend_url'] == self.routes['prod']['backend_url']:
                raise ValueError('Environment callback endpoints must be distinct')
            if len(set(tokens)) != len(tokens):
                raise ValueError('Use distinct request and callback tokens for each environment')
        if not self.standalone and not self.backend_url and self.routes is None:
            raise ValueError('Configure Spring callback URL or explicitly enable standalone test mode')
        if self.backend_url:
            try:
                # urlsplit validates the port only when .port is accessed.
                url = urlsplit(self.backend_url)
                port = url.port
                transport_url = httpx.URL(self.backend_url)
                if (any(char.isspace() or ord(char) < 32 or ord(char) == 127 for char in self.backend_url)
                        or url.netloc.endswith(':') or port == 0
                        or not transport_url.is_absolute_url):
                    raise ValueError('Invalid callback URL')
            except (ValueError, httpx.InvalidURL):
                # Do not echo configured URLs or parser errors containing credentials.
                raise ValueError('Callback URL or port is invalid') from None
            if (url.scheme != 'https' and not (url.scheme == 'http' and url.hostname in ('localhost', '127.0.0.1'))):
                raise ValueError('Callback must use HTTPS (loopback HTTP is allowed for tests)')
            if not url.hostname or url.username or url.password or url.query or url.fragment:
                raise ValueError('Callback URL must be a fixed trusted endpoint without credentials or query')
            if len(self.backend_token) < 32:
                raise ValueError('A separate backend service token of at least 32 characters is required')
        if self.receipt_retention_seconds < 1 or self.max_receipts < 1:
            raise ValueError('Receipt bounds must be positive')
        if not 0 < self.progress_interval_seconds <= self.heartbeat_interval_seconds:
            raise ValueError('Progress interval must be positive and not exceed the heartbeat interval')
        if self.routes is None and len(self.token) < 32:
            raise ValueError('Service token must contain at least 32 characters')
        for value in (self.max_photos, self.max_queue, self.timeout, self.threads):
            if value < 1:
                raise ValueError('Resource limits must be positive')
        if not self.allowed_hosts:
            raise ValueError('At least one HTTP host must be allowed')

    def credentials(self):
        return {name: route['token'] for name, route in self.routes.items()} if self.routes is not None else {'dev': self.token}

    def callback(self, environment):
        if self.routes is not None:
            route = self.routes[environment]  # Never fall back to another environment.
            return route['backend_url'], route['backend_token']
        if environment != 'dev':
            raise ValueError('No callback configured for this environment')
        return self.backend_url, self.backend_token

    @classmethod
    def environment(cls):
        routes_file = os.environ.get('GALLERY_ENVIRONMENTS_FILE')
        routes = None
        if routes_file:
            try:
                routes = json.loads(Path(routes_file).read_text())
            except (OSError, ValueError):
                raise ValueError('Cannot read valid environment configuration') from None
            if not isinstance(routes, dict):
                raise ValueError('Environment configuration must be an object')
        token_file = os.environ.get('GALLERY_TOKEN_FILE') if routes is None else None
        token = (Path(token_file).read_text().strip() if token_file else os.environ.get('GALLERY_TOKEN', '')) if routes is None else ''
        csv = lambda name, default='': tuple(x.strip() for x in os.environ.get(name, default).split(',') if x.strip())
        input_path = os.environ.get('GALLERY_INPUT_ROOT')
        return cls(
            data=Path(os.environ.get('GALLERY_DATA', '/data')).resolve(),
            assets=Path(os.environ.get('GALLERY_ASSETS', '/models')).resolve(),
            routes=routes, token=token, input_root=Path(input_path).resolve() if input_path else None,
            download_hosts=csv('GALLERY_DOWNLOAD_HOSTS'),
            allowed_hosts=csv('GALLERY_ALLOWED_HOSTS', '127.0.0.1:*,localhost:*'),
            max_photos=int(os.environ.get('GALLERY_MAX_PHOTOS', '200')),
            timeout=int(os.environ.get('GALLERY_JOB_TIMEOUT', '600')),
            threads=int(os.environ.get('GALLERY_THREADS', '2')),
            backend_url=(os.environ.get('GALLERY_BACKEND_URL') or None) if routes is None else None,
            backend_token=(Path(os.environ['GALLERY_BACKEND_TOKEN_FILE']).read_text().strip()
                           if os.environ.get('GALLERY_BACKEND_TOKEN_FILE') else os.environ.get('GALLERY_BACKEND_TOKEN', '')) if routes is None else '',
            standalone=os.environ.get('GALLERY_STANDALONE', 'false').lower() == 'true',
            receipt_retention_seconds=int(os.environ.get('GALLERY_RECEIPT_RETENTION_SECONDS', '86400')),
            max_receipts=int(os.environ.get('GALLERY_MAX_RECEIPTS', '1000')),
            progress_interval_seconds=float(os.environ.get('GALLERY_PROGRESS_INTERVAL_SECONDS', '2')),
            heartbeat_interval_seconds=float(os.environ.get('GALLERY_HEARTBEAT_INTERVAL_SECONDS', '15')),
        )
