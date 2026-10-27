"""보고 지표와 조회식. 모든 조회식은 environment="prod"로 고정한다.

기간 끝 시각의 instant query로 `[기간]`을 계산하므로 경계에 마지막 수집 간격만큼 오차가 있다.
"""
from __future__ import annotations

from dataclasses import dataclass

from . import ENVIRONMENT

# 대시보드·알림과 같은 제외 조건(상태 검사·Actuator·장기 SSE 연결)
HTTP_EXCLUDE = '/actuator.*|/health|/api/v1/rooms/[^/]+/events'
APP = f'job="sssok-backend",environment="{ENVIRONMENT}"'
HTTP = f'{APP},uri!~"{HTTP_EXCLUDE}"'
NODE = f'job="node",environment="{ENVIRONMENT}"'
ALLOY = f'job="alloy",environment="{ENVIRONMENT}"'
SCRAPE_INTERVAL = 15
TARGET_JOBS = ('node', 'sssok-backend', 'alloy')


@dataclass(frozen=True)
class Spec:
    name: str
    unit: str
    expr: str
    method: str
    by: tuple = ()
    # zero: 처음 발생할 때 생기는 카운터(5xx·실패 결과). 수집 대상이 관측된 기간에
    # 시계열이 없으면 발생 없음(0)으로 본다.
    absent: str = 'no_data'
    source: str = 'sssok-backend'
    aggregate: str = 'sum'
    distribution: bool = False


def _sum(metric: str, selector: str, by: tuple = ()) -> str:
    group = f' by ({", ".join(by)})' if by else ''
    return f'sum{group} (increase({metric}{{{selector}}}[{{range}}]))'


API = ('method', 'uri')
SPECS = (
    Spec('requests_total', 'count', _sum('http_server_requests_seconds_count', HTTP),
         'increase 합계 · 상태 검사·Actuator·SSE 제외'),
    Spec('requests_by_api', 'count', _sum('http_server_requests_seconds_count', HTTP, API),
         'API(method, uri)별 increase 합계', by=API),
    Spec('errors_5xx_total', 'count', _sum('http_server_requests_seconds_count', HTTP + ',status=~"5.."'),
         '5xx 응답 increase 합계', absent='zero'),
    Spec('errors_5xx_by_api', 'count', _sum('http_server_requests_seconds_count', HTTP + ',status=~"5.."', API),
         'API별 5xx 응답 increase 합계', by=API, absent='zero'),
    Spec('latency_buckets', 'count', _sum('http_server_requests_seconds_bucket', HTTP, ('le',)),
         '히스토그램 버킷별 increase', by=('le',), distribution=True),
    Spec('latency_buckets_by_api', 'count', _sum('http_server_requests_seconds_bucket', HTTP, API + ('le',)),
         'API별 히스토그램 버킷 increase', by=API + ('le',), distribution=True),
    Spec('upload_files_rejected', 'count', _sum('sssok_upload_files_total', APP + ',result="rejected"', ('stage',)),
         '업로드 파일 거절 increase · 단계별', by=('stage',), absent='zero'),
    Spec('upload_files_accepted', 'count', _sum('sssok_upload_files_total', APP + ',result="accepted"', ('stage',)),
         '업로드 파일 수락 increase · 단계별', by=('stage',), absent='zero'),
    Spec('media_submissions_rejected', 'count', _sum('sssok_media_submissions_total', APP + ',result="rejected"'),
         '썸네일 작업 제출 거절 increase', absent='zero'),
    Spec('media_permanent_failures', 'count',
         _sum('sssok_media_processing_seconds_count', APP + ',result="permanent_failure"'),
         '미디어 처리 영구 실패 increase', absent='zero'),
    Spec('thumbnail_failed', 'count', _sum('sssok_thumbnail_failed_total', APP), '썸네일 실패 increase', absent='zero'),
    Spec('thumbnail_retried', 'count', _sum('sssok_thumbnail_retried_total', APP), '썸네일 재시도 increase', absent='zero'),
    Spec('download_failures', 'count', _sum('sssok_download_processing_seconds_count', APP + ',result="failure"'),
         '다운로드 처리 실패 increase', absent='zero'),
    Spec('download_successes', 'count', _sum('sssok_download_processing_seconds_count', APP + ',result="success"'),
         '다운로드 처리 성공 increase', absent='zero'),
    Spec('cpu_peak_ratio', 'ratio',
         f'max_over_time((1 - avg(rate(node_cpu_seconds_total{{{NODE},mode="idle"}}[5m])))[{{range}}:1m])',
         '전체 코어 5분 평균 사용률의 최댓값(1분 간격 평가)', source='node', aggregate='max'),
    Spec('memory_available_min_ratio', 'ratio',
         f'min_over_time((sum(node_memory_MemAvailable_bytes{{{NODE}}}) / sum(node_memory_MemTotal_bytes{{{NODE}}}))[{{range}}:1m])',
         'MemAvailable / MemTotal 최솟값(1분 간격 평가)', source='node', aggregate='min'),
    Spec('disk_available_min_ratio', 'ratio',
         f'min_over_time((sum(node_filesystem_avail_bytes{{{NODE},mountpoint="/"}}) / sum(node_filesystem_size_bytes{{{NODE},mountpoint="/"}}))[{{range}}:1m])',
         '루트 디스크 여유 비율 최솟값(1분 간격 평가)', source='node', aggregate='min'),
    Spec('disk_available_min_bytes', 'bytes',
         f'min_over_time(sum(node_filesystem_avail_bytes{{{NODE},mountpoint="/"}})[{{range}}:1m])',
         '루트 디스크 여유 용량 최솟값(1분 간격 평가)', source='node', aggregate='min'),
    Spec('scrape_samples', 'count',
         f'sum by (job) (count_over_time(up{{environment="{ENVIRONMENT}",job=~"{"|".join(TARGET_JOBS)}"}}[{{range}}]))',
         f'수집 시도 기록 수(기대값: 기간 / {SCRAPE_INTERVAL}초)', by=('job',), source=''),
    Spec('scrape_failed_samples', 'count',
         f'sum by (job) (count_over_time((up{{environment="{ENVIRONMENT}",job=~"{"|".join(TARGET_JOBS)}"}} == 0)[{{range}}:{SCRAPE_INTERVAL}s]))',
         f'up=0으로 기록된 수집 실패 수({SCRAPE_INTERVAL}초 간격 평가)', by=('job',), absent='zero', source=''),
    Spec('log_entries_dropped', 'count', _sum('loki_write_dropped_entries_total', ALLOY, ('host', 'reason')),
         'Alloy 로그 폐기 increase · 목적지·사유별', by=('host', 'reason'), absent='zero', source='alloy'),
    Spec('log_send_failures', 'count',
         _sum('loki_write_request_duration_seconds_count', ALLOY + ',status_code!~"2.."', ('host',)),
         'Alloy 로그 전송 실패 요청 increase · 목적지별', by=('host',), absent='zero', source='alloy'),
)

# 하루 전체 p95에 가려지는 짧은 지연 구간
LATENCY_WINDOW = (
    f'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{{{HTTP}}}[5m])))'
    f' and sum(increase(http_server_requests_seconds_count{{{HTTP}}}[5m])) >= 20'
)
LATENCY_WINDOW_STEP = 300
ALERTS = f'ALERTS{{alertstate="firing",environment="{ENVIRONMENT}"}}'
ALERTS_STEP = 60


def render(spec: Spec, seconds: int) -> str:
    return spec.expr.replace('{range}', f'{seconds}s')


def all_queries(seconds: int = 86400) -> list[str]:
    return [render(spec, seconds) for spec in SPECS] + [LATENCY_WINDOW, ALERTS]
