"""Discord 웹훅 발송. 요청 전 실패와 요청 후 결과 불확실을 구분하고, 웹훅 주소는 오류 문구에 넣지 않는다."""
from __future__ import annotations

from dataclasses import dataclass
import http.client
import json
from pathlib import Path
import re
import socket
import ssl
import urllib.parse

# 알림(#서버-알림)과 다른 #지표-매일-요약 채널 웹훅
WEBHOOK_KEY = 'OPS_REPORT_DISCORD_WEBHOOK_URL'
WEBHOOK_PATTERN = re.compile(r'https://discord\.com/api/webhooks/[0-9]+/[A-Za-z0-9_-]+')


@dataclass(frozen=True)
class Result:
    outcome: str  # sent | failed | retry(429) | unknown
    http_status: int | None = None
    message_id: str | None = None
    channel_id: str | None = None
    error: str | None = None
    retry_after: float = 0.0


def read_webhook(env_file: Path) -> str:
    """.env의 다른 비밀값은 읽지 않고 웹훅 한 줄만 찾는다."""
    value = None
    for line in env_file.read_text().splitlines():
        key, sep, raw = line.strip().partition('=')
        if sep and key.strip().removeprefix('export ').strip() == WEBHOOK_KEY:
            value = raw.strip().strip('"').strip("'")
    if not value or not WEBHOOK_PATTERN.fullmatch(value):
        raise RuntimeError(f'{env_file}의 {WEBHOOK_KEY} 값이 없거나 Discord 웹훅 형식이 아닙니다')
    return value


class Webhook:
    def __init__(self, url: str, timeout: float = 15):
        parsed = urllib.parse.urlsplit(url)
        self.scheme, self.host, self.port = parsed.scheme, parsed.hostname, parsed.port
        self.path = parsed.path + '?wait=true'
        self.timeout = timeout
        self._secret = parsed.path

    def _connection(self):
        if self.scheme == 'https':
            return http.client.HTTPSConnection(self.host, self.port, timeout=self.timeout,
                                               context=ssl.create_default_context())
        return http.client.HTTPConnection(self.host, self.port, timeout=self.timeout)

    def _clean(self, text: str) -> str:
        return text.replace(self._secret, '/api/webhooks/***')[:300]

    def send(self, payload: dict) -> Result:
        body = json.dumps(payload, ensure_ascii=False).encode()
        connection = self._connection()
        try:
            try:
                connection.connect()
            except (OSError, ssl.SSLError) as error:
                # 요청이 나가기 전이므로 재시도해도 중복되지 않는다.
                return Result('failed', error=self._clean(f'연결 실패: {type(error).__name__}: {error}'))
            try:
                connection.request('POST', self.path, body, {'Content-Type': 'application/json',
                                                            'User-Agent': 'sssok-ops-report'})
                response = connection.getresponse()
                raw = response.read()
            except (OSError, http.client.HTTPException, socket.timeout) as error:
                return Result('unknown', error=self._clean(f'응답 확인 실패: {type(error).__name__}: {error}'))
        finally:
            connection.close()
        status = response.status
        if 200 <= status < 300:
            try:
                message = json.loads(raw)
                ids = [message.get(key) for key in ('id', 'channel_id')]
                return Result('sent', status, *(str(value) if value else None for value in ids))
            except ValueError:
                return Result('sent', status, error='메시지 ID 응답을 해석하지 못함')
        if status == 429:
            try:
                retry_after = float(json.loads(raw).get('retry_after', 5))
            except (ValueError, AttributeError):
                retry_after = 5.0
            return Result('retry', status, error='발송 속도 제한', retry_after=min(retry_after, 60))
        if 400 <= status < 500:
            return Result('failed', status, error=self._clean(f'HTTP {status}: {raw[:200].decode(errors="replace")}'))
        # 5xx는 메시지 생성 여부를 확정할 수 없어 자동 재전송하지 않는다.
        return Result('unknown', status, error=f'HTTP {status}')
