"""Prometheus HTTP API 조회(GET 전용)."""
from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_URL = 'http://127.0.0.1:9090'


class QueryError(Exception):
    pass


class Prometheus:
    def __init__(self, base_url: str = DEFAULT_URL, timeout: float = 30, token: str | None = None):
        self.base_url = base_url.rstrip('/')
        self.timeout = timeout
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        # 서버 밖에서 Grafana 데이터소스 프록시로 조회할 때 쓴다.
        if token:
            self.opener.addheaders.append(('Authorization', f'Bearer {token}'))

    def _get(self, path: str, params: dict) -> dict:
        url = f'{self.base_url}/api/v1/{path}?{urllib.parse.urlencode(params)}'
        try:
            with self.opener.open(url, timeout=self.timeout) as response:
                body = json.load(response)
        except urllib.error.HTTPError as error:
            try:
                body = json.load(error)
            except ValueError:
                raise QueryError(f'HTTP {error.code}') from None
        except (OSError, ValueError) as error:
            raise QueryError(f'{type(error).__name__}: {error}') from None
        if body.get('status') != 'success':
            raise QueryError(f"{body.get('errorType', 'error')}: {body.get('error', '응답 오류')}")
        return body['data']

    def instant(self, query: str, at: float) -> list[dict]:
        data = self._get('query', {'query': query, 'time': f'{at:.3f}'})
        if data.get('resultType') != 'vector':
            raise QueryError(f"vector 결과가 필요합니다: {data.get('resultType')}")
        return data['result']

    def range(self, query: str, start: float, end: float, step: int) -> list[dict]:
        data = self._get('query_range', {'query': query, 'start': f'{start:.3f}',
                                         'end': f'{end:.3f}', 'step': str(step)})
        return data['result']

    def ready(self) -> bool:
        try:
            with self.opener.open(self.base_url + '/-/ready', timeout=self.timeout) as response:
                return response.status == 200
        except OSError:
            return False
