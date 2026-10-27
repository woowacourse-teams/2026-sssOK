#!/usr/bin/env python3
"""정기 보고 회귀 시험. 실제 Prometheus·Discord 없이 경계·집계·누락·재실행·발송 실패를 검사한다."""
from datetime import date, datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
import io
import json
from pathlib import Path
import socket
import sqlite3
import sys
import tempfile
import threading
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

from ops_report import cli, message, queries
from ops_report.aggregate import Data, Obs, combine, compare, derive, quantile, status
from ops_report.collect import collect
from ops_report.discord import Result, Webhook, read_webhook
from ops_report.periods import KST, daily, targets, weekly
from ops_report.prometheus import QueryError
from ops_report.service import Reporter, deliver, message_key
from ops_report.store import Store

SPEC = {queries.render(spec, 86400): spec for spec in queries.SPECS}
BOUNDS = ('0.1', '0.5', '1.0', '+Inf')
WEBHOOK = 'https://discord.com/api/webhooks/123/secret-token_value'


def sample(value, **labels):
    return {'metric': labels, 'value': [0, str(value)]}


def day_values(requests=1000.0, errors=10.0, buckets=(900, 980, 995, 1000)):
    """하루치 원시 조회 결과. 각 값은 PromQL 결과 형태다."""
    return {
        'requests_total': [sample(requests)],
        'requests_by_api': [sample(requests * 0.6, method='GET', uri='/api/v1/rooms'),
                            sample(requests * 0.4, method='POST', uri='/api/v1/rooms/{roomId}/media')],
        'errors_5xx_total': [sample(errors)] if errors else [],
        'errors_5xx_by_api': [sample(errors, method='POST', uri='/api/v1/rooms/{roomId}/media')] if errors else [],
        'latency_buckets': [sample(v, le=le) for le, v in zip(BOUNDS, buckets)],
        'latency_buckets_by_api': [sample(v * 0.4, method='POST', uri='/api/v1/rooms/{roomId}/media', le=le)
                                   for le, v in zip(BOUNDS, buckets)],
        'upload_files_rejected': [sample(2, stage='issue')],
        'upload_files_accepted': [sample(50, stage='issue')],
        'thumbnail_failed': [sample(0)], 'thumbnail_retried': [sample(3)],
        'download_failures': [], 'download_successes': [sample(4)],
        'cpu_peak_ratio': [sample(0.42)], 'memory_available_min_ratio': [sample(0.31)],
        'disk_available_min_ratio': [sample(0.6)], 'disk_available_min_bytes': [sample(12 * 1024 ** 3)],
        'scrape_samples': [sample(5760, job=job) for job in queries.TARGET_JOBS],
    }


class FakePrometheus:
    """기간 끝 시각(KST 날짜)별 결과를 돌려준다. 없는 지표는 빈 결과, errors에 있으면 조회 실패."""

    def __init__(self, days=None, errors=(), down=False):
        self.days = days or {}
        self.errors = set(errors)
        self.down = down
        self.instant_calls = []

    def instant(self, query, at):
        self.instant_calls.append(query)
        assert 'environment="prod"' in query and 'dev' not in query, query
        spec = SPEC[query]
        if self.down or spec.name in self.errors:
            raise QueryError('connection refused')
        day = (datetime.fromtimestamp(at, KST) - timedelta(days=1)).date()
        return self.days.get(day, {}).get(spec.name, [])

    def range(self, query, start, end, step):
        assert 'environment="prod"' in query, query
        if self.down:
            raise QueryError('connection refused')
        day = datetime.fromtimestamp(start, KST).date()
        if query == queries.ALERTS and day in self.days:
            base = daily(day).start.timestamp() + 3600
            return [{'metric': {'alertname': 'BackendHighLatency', 'severity': 'warning', 'environment': 'prod',
                                'server': 'prod', 'instance': '10.0.20.127:19100'},
                     'values': [[base + i * 60, '1'] for i in range(12)]}]
        if query == queries.LATENCY_WINDOW and day in self.days:
            return [{'metric': {}, 'values': [[daily(day).start.timestamp() + 600, '0.4'],
                                              [daily(day).start.timestamp() + 5400, '1.3']]}]
        return []


class FakeWebhook:
    def __init__(self, *results):
        self.results = list(results)
        self.sent = []

    def send(self, payload):
        self.sent.append(payload)
        return self.results.pop(0) if len(self.results) > 1 else self.results[0]


class PeriodTest(unittest.TestCase):
    def test_kst_day_boundary_converts_to_utc_for_queries(self):
        period = daily(date(2026, 10, 26))
        self.assertEqual(period.start.astimezone(timezone.utc), datetime(2026, 10, 25, 15, tzinfo=timezone.utc))
        self.assertEqual(period.end.astimezone(timezone.utc), datetime(2026, 10, 26, 15, tzinfo=timezone.utc))
        self.assertEqual(period.seconds, 86400)
        self.assertEqual(period.previous().start.date(), date(2026, 10, 25))

    def test_monday_run_adds_last_week(self):
        # 2026-10-26은 월요일. 월요일 09:00 실행은 일요일(10-25) 일간 + 지난주(10-19~10-25) 주간이다.
        periods = targets(date(2026, 10, 25))
        self.assertEqual([p.kind for p in periods], ['daily', 'weekly'])
        self.assertEqual(periods[1].start, datetime(2026, 10, 19, tzinfo=KST))
        self.assertEqual(periods[1].end, datetime(2026, 10, 26, tzinfo=KST))
        self.assertEqual(periods[1].previous().start, datetime(2026, 10, 12, tzinfo=KST))
        self.assertEqual([p.kind for p in targets(date(2026, 10, 26))], ['daily'])
        with self.assertRaises(ValueError):
            weekly(date(2026, 10, 20))

    def test_cli_default_target_is_yesterday_in_kst(self):
        # UTC로는 26일 00:30이지만 KST로는 26일 09:30 → 보고 대상 25일
        now = datetime(2026, 10, 26, 0, 30, tzinfo=timezone.utc)
        self.assertEqual(cli.today(now) - timedelta(days=1), date(2026, 10, 25))


class QueryTest(unittest.TestCase):
    def test_every_query_is_prod_only(self):
        for query in queries.all_queries():
            self.assertIn('environment="prod"', query)
            self.assertNotIn('dev', query)
            self.assertNotIn('{range}', query)

    def test_latency_excludes_health_and_sse(self):
        for spec in queries.SPECS:
            if 'http_server_requests' in spec.expr:
                self.assertIn('uri!~"/actuator.*|/health|/api/v1/rooms/[^/]+/events"', spec.expr)


class AggregateTest(unittest.TestCase):
    def test_quantile_matches_prometheus_interpolation(self):
        buckets = {'0.1': 50, '0.5': 90, '1.0': 100, '+Inf': 100}
        # rank 95: 0.5~1.0 버킷(90→100) 안에서 선형 보간
        self.assertAlmostEqual(quantile(0.95, buckets), 0.75)
        self.assertEqual(quantile(0.95, {'0.1': 1, '+Inf': 100}), 0.1)  # +Inf 버킷이면 최고 유한 경계
        self.assertIsNone(quantile(0.95, {'0.1': 0, '+Inf': 0}))

    def day(self, **values):
        data = collect(FakePrometheus({date(2026, 10, 1): day_values(**values)}), daily(date(2026, 10, 1)))
        return data

    def test_weekly_error_rate_and_p95_use_sums_not_daily_averages(self):
        busy = self.day(requests=9000, errors=0, buckets=(8900, 9000, 9000, 9000))
        quiet = self.day(requests=1000, errors=100, buckets=(0, 0, 500, 1000))
        week, incompatible = combine([busy, quiet])
        derive(week, 2 * 86400, incompatible)
        self.assertEqual(week.get('requests_total').value, 10000)
        # 일별 오류율 평균(0% + 10%)/2 = 5%가 아니라 100 / 10000 = 1%
        self.assertAlmostEqual(week.get('error_rate').value, 0.01)
        merged = {'0.1': 8900, '0.5': 9000, '1.0': 9500, '+Inf': 10000}
        self.assertAlmostEqual(week.get('latency_p95').value, quantile(0.95, merged))
        self.assertEqual(week.get('cpu_peak_ratio').value, 0.42)

    def test_bucket_bounds_change_makes_weekly_p95_incomparable(self):
        first = self.day()
        second = self.day()
        second.distributions[('latency_buckets', '')] = {'0.2': 900, '+Inf': 1000}
        week, incompatible = combine([first, second])
        derive(week, 2 * 86400, incompatible)
        self.assertEqual(week.get('latency_p95').status, 'incomparable')
        self.assertEqual(week.get('requests_total').value, 2000)

    def test_missing_day_marks_week_partial_and_skips_change(self):
        week, incompatible = combine([self.day(), None])
        derive(week, 2 * 86400, incompatible)
        self.assertEqual(week.get('requests_total').status, 'partial')
        self.assertEqual(status(week), 'partial')
        full, _ = combine([self.day(), self.day()])
        rows = {c.metric: c for c in compare(week, full)}
        self.assertEqual(rows['requests_total'].status, 'partial_data')

    def test_previous_zero_is_new_not_infinite(self):
        current, previous = Data(), Data()
        for data, value in ((current, 5.0), (previous, 0.0)):
            data.observations[('errors_5xx_total', '')] = Obs(value, 'count', '', 'ok')
            data.observations[('thumbnail_failed', '')] = Obs(0.0, 'count', '', 'ok')
        rows = {c.metric: c for c in compare(current, previous)}
        self.assertEqual(rows['errors_5xx_total'].status, 'new')
        self.assertIsNone(rows['errors_5xx_total'].ratio)
        self.assertEqual(rows['thumbnail_failed'].status, 'both_zero')
        self.assertEqual({c.metric: c for c in compare(current, None)}['errors_5xx_total'].status, 'no_baseline')


class CollectTest(unittest.TestCase):
    period = daily(date(2026, 10, 1))

    def test_absent_counter_is_zero_only_when_target_was_observed(self):
        data = collect(FakePrometheus({date(2026, 10, 1): day_values(errors=0)}), self.period)
        self.assertEqual(data.get('errors_5xx_total'), Obs(0.0, 'count', data.get('errors_5xx_total').method, 'ok',
                                                          '시계열 없음 = 발생 없음'))
        self.assertEqual(data.get('error_rate').value, 0)
        self.assertEqual(data.get('scrape_failed_samples', 'node').value, 0)
        values = day_values(errors=0)
        values['scrape_samples'] = []
        values['requests_total'] = []
        data = collect(FakePrometheus({date(2026, 10, 1): values}), self.period)
        self.assertEqual(data.get('errors_5xx_total').status, 'no_data')
        self.assertEqual(data.get('error_rate').status, 'no_data')
        self.assertIsNone(data.get('scrape_failed_samples', 'node'))

    def test_zero_requests_is_not_missing_data(self):
        data = collect(FakePrometheus({date(2026, 10, 1): day_values(requests=0, errors=0, buckets=(0, 0, 0, 0))}),
                       self.period)
        self.assertEqual(data.get('requests_total').value, 0)
        self.assertEqual(data.get('error_rate').status, 'no_requests')
        self.assertEqual(data.get('latency_p95').status, 'no_requests')

    def test_partial_week_keeps_peak_time(self):
        days = [collect(FakePrometheus({date(2026, 10, 1): day_values()}), self.period), None]
        week, incompatible = combine(days)
        self.assertEqual(week.get('latency_p95_5m_max').status, 'partial')
        self.assertIn('at=10-01 01:30', week.get('latency_p95_5m_max').detail)
        self.assertIn('(10-01 01:30)', '\n'.join(message._numbers(week, {}, '전주')))

    def test_partial_query_failure_keeps_other_metrics(self):
        data = collect(FakePrometheus({date(2026, 10, 1): day_values()}, errors={'cpu_peak_ratio'}), self.period)
        self.assertEqual(data.get('cpu_peak_ratio').status, 'error')
        self.assertEqual(data.get('requests_total').value, 1000)
        self.assertEqual(status(data), 'partial')
        self.assertEqual(data.get('latency_p95_5m_max').value, 1.3)
        self.assertEqual(data.intervals[0].observed_seconds, 720)

    def test_total_failure(self):
        self.assertEqual(status(collect(FakePrometheus(down=True), self.period)), 'failed')

    def test_nan_peak_is_no_data_not_stored_value(self):
        values = day_values()
        values['memory_available_min_ratio'] = [sample('NaN')]
        data = collect(FakePrometheus({date(2026, 10, 1): values}), self.period)
        self.assertEqual((data.get('memory_available_min_ratio').value,
                          data.get('memory_available_min_ratio').status), (None, 'no_data'))

    def test_low_coverage_is_reported(self):
        values = day_values()
        values['scrape_samples'] = [sample(2880, job=job) for job in queries.TARGET_JOBS]
        data = collect(FakePrometheus({date(2026, 10, 1): values}), self.period)
        self.assertAlmostEqual(data.get('scrape_coverage', 'node').value, 0.5)
        text = '\n'.join(message._data_state(data, {}))
        self.assertIn('정상으로 계산하지 않았습니다', text)


class ReporterTest(unittest.TestCase):
    def setUp(self):
        self.store = Store(':memory:')
        self.addCleanup(self.store.close)
        # 2026-10-26(월) 09:00 실행. 15일 보존 안의 모든 날에 데이터가 있다.
        self.now = datetime(2026, 10, 26, 9, tzinfo=KST)
        self.days = {date(2026, 10, 26) - timedelta(days=i): day_values(errors=i % 3) for i in range(1, 16)}
        self.prom = FakePrometheus(self.days)

    def reporter(self, prom=None):
        return Reporter(self.store, prom or self.prom, self.now, log=lambda text: None)

    def test_monday_builds_one_payload_with_daily_and_weekly(self):
        kind, ids, body, state = self.reporter().build(date(2026, 10, 25))
        self.assertEqual((kind, state, len(ids)), ('report', 'complete', 2))
        titles = [embed['title'] for embed in body['embeds']]
        self.assertEqual(titles, ['[운영] 일간 보고 · 2026-10-25 (일)', '[운영] 주간 보고 · 2026-10-19 ~ 2026-10-25'])
        weekly_text = body['embeds'][1]['description']
        self.assertIn('일별 요청', weekly_text)
        self.assertIn('2일 이상 반복', weekly_text)
        self.assertIn('var-environment=prod', weekly_text)
        for embed in body['embeds']:
            self.assertLessEqual(len(embed['description']), message.DESCRIPTION_LIMIT)
        # Discord 메시지 전체 embed 6000자 제한
        self.assertLess(sum(len(e['title']) + len(e['description']) for e in body['embeds']), 6000)
        self.assertRegex(weekly_text, r'5분 구간 최대 1\.30s \(10-\d\d 01:30\)')
        self.assertEqual(body['allowed_mentions'], {'parse': []})
        rows = self.store.db.execute("SELECT kind, status FROM reports WHERE kind = 'weekly'").fetchall()
        self.assertEqual(len(rows), 2)
        comparison = self.store.db.execute("SELECT c.status FROM comparisons c JOIN reports r ON r.id = c.report_id"
                                           " WHERE r.kind = 'weekly' AND metric = 'requests_total'").fetchone()
        self.assertEqual(comparison[0], 'ok')

    def test_rerun_reuses_complete_aggregate(self):
        self.reporter().build(date(2026, 10, 22))
        calls = len(self.prom.instant_calls)
        self.reporter().build(date(2026, 10, 22))
        self.assertEqual(len(self.prom.instant_calls), calls)
        self.reporter().build(date(2026, 10, 22), refresh=True)
        self.assertGreater(len(self.prom.instant_calls), calls)
        count = self.store.db.execute("SELECT count(*) FROM reports WHERE period_start LIKE '2026-10-22%'").fetchone()
        self.assertEqual(count[0], 1)

    def test_out_of_retention_days_are_not_estimated(self):
        reporter = self.reporter()
        self.assertIsNone(reporter.daily(date(2026, 10, 9)))
        self.assertIsNone(self.store.report_row(daily(date(2026, 10, 9))))
        self.assertIsNone(reporter.daily(date(2026, 10, 26)))

    def test_total_failure_builds_failure_notice(self):
        kind, _, body, state = self.reporter(FakePrometheus(down=True)).build(date(2026, 10, 25))
        self.assertEqual((kind, state), ('failure', 'failed'))
        self.assertIn('주간 보고도 만들지 않았습니다', body['embeds'][0]['description'])
        self.assertIn('보고서 생성 실패', body['embeds'][0]['title'])
        self.assertIn('connection refused', body['embeds'][0]['description'])

    def test_first_run_without_previous_day_shows_missing_baseline(self):
        prom = FakePrometheus({date(2026, 10, 26): day_values()})
        _, ids, body, _ = Reporter(self.store, prom, datetime(2026, 10, 27, 9, tzinfo=KST),
                                   log=lambda t: None).build(date(2026, 10, 26))
        self.assertIn('비교 데이터 부족', body['embeds'][0]['description'])


class DeliveryTest(unittest.TestCase):
    def setUp(self):
        self.store = Store(':memory:')
        self.addCleanup(self.store.close)
        self.day = date(2026, 10, 25)
        self.key = message_key(self.day, 'report')
        self.body = message.payload([{'title': 't', 'description': 'd'}])

    def send(self, webhook, force=False):
        return deliver(self.store, webhook, self.key, self.day, 'report', [1], None, self.body, force,
                       log=lambda text: None, sleep=lambda s: None)

    def test_sent_report_is_not_resent(self):
        webhook = FakeWebhook(Result('sent', 200, '111', '222'))
        self.assertEqual(self.send(webhook), 'sent')
        self.assertEqual(self.send(webhook), 'sent')
        self.assertEqual(len(webhook.sent), 1)
        row = self.store.delivery(self.key)
        self.assertEqual((row['status'], row['message_id'], row['attempts']), ('sent', '111', 1))

    def test_unknown_result_requires_force(self):
        webhook = FakeWebhook(Result('unknown', error='timeout'))
        self.assertEqual(self.send(webhook), 'unknown')
        self.assertEqual(self.send(FakeWebhook(Result('sent', 200, '1', '2'))), 'unknown')
        self.assertEqual(self.send(FakeWebhook(Result('sent', 200, '1', '2')), force=True), 'sent')

    def test_interrupted_send_is_treated_as_unknown(self):
        self.store.begin_delivery(self.key, self.day.isoformat(), 'report', [1], None, self.body)
        webhook = FakeWebhook(Result('sent', 200, '1', '2'))
        self.assertEqual(self.send(webhook), 'unknown')
        self.assertEqual(webhook.sent, [])

    def test_clear_failure_retries_limited_times(self):
        webhook = FakeWebhook(Result('failed', error='연결 실패'))
        self.assertEqual(self.send(webhook), 'failed')
        self.assertEqual(len(webhook.sent), 3)
        webhook = FakeWebhook(Result('retry', 429, retry_after=1), Result('sent', 200, '9', '2'))
        self.assertEqual(self.send(webhook), 'sent')
        attempts = self.store.db.execute('SELECT outcome FROM delivery_attempts ORDER BY id').fetchall()
        self.assertEqual([a[0] for a in attempts], ['failed'] * 3 + ['retry', 'sent'])

    def test_client_error_is_not_retried(self):
        webhook = FakeWebhook(Result('failed', 404, error='HTTP 404'))
        self.assertEqual(self.send(webhook), 'failed')
        self.assertEqual(len(webhook.sent), 1)


class Receiver(BaseHTTPRequestHandler):
    mode = 'ok'
    received = []

    def do_POST(self):
        body = self.rfile.read(int(self.headers['Content-Length']))
        Receiver.received.append((self.path, json.loads(body)))
        if Receiver.mode == 'drop':
            self.connection.shutdown(socket.SHUT_RDWR)
            return
        status, reply = {'ok': (200, {'id': '42', 'channel_id': '7'}), '429': (429, {'retry_after': 0.5}),
                         '500': (500, {})}[Receiver.mode]
        data = json.dumps(reply).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


class WebhookTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = HTTPServer(('127.0.0.1', 0), Receiver)
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
        cls.url = f'http://127.0.0.1:{cls.server.server_port}/api/webhooks/1/secret-token'

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()

    def test_outcomes_and_secret_never_in_error(self):
        for mode, outcome in (('ok', 'sent'), ('429', 'retry'), ('500', 'unknown'), ('drop', 'unknown')):
            with self.subTest(mode=mode):
                Receiver.mode = mode
                result = Webhook(self.url, timeout=3).send({'content': 'x'})
                self.assertEqual(result.outcome, outcome)
                self.assertNotIn('secret-token', result.error or '')
        self.assertEqual(Receiver.received[0][0], '/api/webhooks/1/secret-token?wait=true')
        Receiver.mode = 'ok'
        self.assertEqual(Webhook(self.url).send({}).message_id, '42')

    def test_connection_refused_is_clear_failure(self):
        with socket.socket() as probe:
            probe.bind(('127.0.0.1', 0))
            port = probe.getsockname()[1]
        result = Webhook(f'http://127.0.0.1:{port}/api/webhooks/1/secret-token', timeout=2).send({})
        self.assertEqual((result.outcome, result.http_status), ('failed', None))
        self.assertNotIn('secret-token', result.error)

    def test_read_only_webhook_line_from_env(self):
        with tempfile.TemporaryDirectory() as tmp:
            env = Path(tmp) / '.env'
            env.write_text(f'CENTRAL_DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/9/alert\n'
                           f'OPS_REPORT_DISCORD_WEBHOOK_URL="{WEBHOOK}"\n')
            self.assertEqual(read_webhook(env), WEBHOOK)
            env.write_text('OPS_REPORT_DISCORD_WEBHOOK_URL=https://example.com/x\n')
            with self.assertRaises(RuntimeError) as error:
                read_webhook(env)
            self.assertNotIn('example.com', str(error.exception))


class CommandTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.dir = Path(tmp.name)
        self.db = str(self.dir / 'state' / 'report.sqlite3')
        (self.dir / '.env').write_text(f'OPS_REPORT_DISCORD_WEBHOOK_URL={WEBHOOK}\n')
        days = {date(2026, 10, 27) - timedelta(days=i): day_values() for i in range(1, 16)}
        self.prom = FakePrometheus(days)
        self.webhook = FakeWebhook(Result('sent', 200, '555', '7'))

    def run_cli(self, *args, send=None):
        settings = self.dir / 'ops-report.json'
        if send is not None:
            settings.write_text(json.dumps({'send': send}))
        argv = ['--db', self.db, '--settings', str(settings), '--env-file', str(self.dir / '.env'),
                '--now', '2026-10-27T09:00:00+09:00', *args]
        output = io.StringIO()
        with patch.object(cli, 'Prometheus', return_value=self.prom), \
             patch.object(cli, 'Webhook', return_value=self.webhook), redirect_stdout(output):
            code = cli.main(argv)
        return code, output.getvalue()

    def deliveries(self):
        return sqlite3.connect(self.db).execute('SELECT message_key, status, message_id, payload FROM deliveries').fetchall()

    def test_timer_run_follows_settings_and_prevents_duplicates(self):
        code, output = self.run_cli('run', '--trigger', 'timer')
        self.assertEqual(code, 0)
        self.assertIn('발송하지 않았습니다(발송 설정 꺼짐)', output)
        self.assertEqual(self.deliveries(), [])
        self.assertEqual(self.run_cli('run', '--trigger', 'timer', send=True)[0], 0)
        self.assertEqual(self.run_cli('run', '--trigger', 'timer', send=True)[0], 0)
        self.assertEqual(len(self.webhook.sent), 1)
        key, state, message_id, payload = self.deliveries()[0]
        self.assertEqual((key, state, message_id), ('prod:2026-10-26:report', 'sent', '555'))
        dump = '\n'.join(sqlite3.connect(self.db).iterdump())
        self.assertNotIn('secret-token', dump)

    def test_preview_and_dated_run_do_not_send(self):
        code, output = self.run_cli('run', '--preview', send=True)
        self.assertEqual(code, 0)
        self.assertIn('[운영] 일간 보고 · 2026-10-26 (월)', output)
        code, output = self.run_cli('run', '--date', '2026-10-20', send=True)
        self.assertEqual(code, 0)
        self.assertIn('발송하지 않았습니다(--send 없음)', output)
        self.assertEqual(self.webhook.sent, [])
        self.run_cli('run', '--date', '2026-10-20', '--send')
        self.assertEqual(self.deliveries()[0][0], 'prod:2026-10-20:report')

    def test_backfill_recovers_without_sending(self):
        code, output = self.run_cli('backfill', '--days', '5', send=True)
        self.assertEqual(code, 0)
        self.assertIn('집계 복구 5일', output)
        self.assertEqual(self.webhook.sent, [])
        self.assertEqual(sqlite3.connect(self.db).execute('SELECT count(*) FROM reports').fetchone()[0], 5)

    def test_unknown_delivery_fails_run_for_journal(self):
        self.webhook = FakeWebhook(Result('unknown', error='timeout'))
        code, _ = self.run_cli('run', send=True)
        self.assertEqual(code, 1)
        run = sqlite3.connect(self.db).execute('SELECT status, error FROM runs ORDER BY id DESC').fetchone()
        self.assertEqual(run[0], 'failed')

    def test_concurrent_run_is_rejected(self):
        Path(self.db).parent.mkdir(parents=True)
        with cli.locked(self.db):
            with self.assertRaises(SystemExit):
                self.run_cli('run')

    def test_missing_webhook_fails_before_querying(self):
        (self.dir / '.env').write_text('CENTRAL_DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/9/alert\n')
        with self.assertRaises(RuntimeError):
            self.run_cli('run', send=True)
        self.assertEqual(self.prom.instant_calls, [])

    def test_invalid_settings_are_rejected(self):
        (self.dir / 'ops-report.json').write_text('{"send": "yes"}')
        with self.assertRaises(SystemExit):
            self.run_cli('run')


class UnitFileTest(unittest.TestCase):
    def test_backup_copies_consistent_database(self):
        with tempfile.TemporaryDirectory() as tmp:
            db = str(Path(tmp) / 'report.sqlite3')
            store = Store(db)
            store.start_run('manual', None)
            store.close()
            with redirect_stdout(io.StringIO()):
                self.assertEqual(cli.main(['--db', db, 'backup', '--to', str(Path(tmp) / 'copy.sqlite3')]), 0)
            copy = sqlite3.connect(str(Path(tmp) / 'copy.sqlite3'))
            self.assertEqual(copy.execute('SELECT count(*) FROM runs').fetchone()[0], 1)
            self.assertEqual((Path(tmp) / 'copy.sqlite3').stat().st_mode & 0o777, 0o600)
            with self.assertRaises(SystemExit):
                cli.main(['--db', db, 'backup', '--to', str(Path(tmp) / 'copy.sqlite3')])

    def test_timer_and_service(self):
        root = Path(__file__).resolve().parent / 'ops_report'
        timer = (root / 'sssok-ops-report.timer').read_text()
        service = (root / 'sssok-ops-report.service').read_text()
        self.assertIn('OnCalendar=*-*-* 09:00:00 Asia/Seoul', timer)
        self.assertIn('Persistent=true', timer)
        self.assertIn('StateDirectory=sssok-ops-report', service)
        self.assertIn('-m ops_report run --trigger timer', service)
        self.assertNotIn('EnvironmentFile', service)


if __name__ == '__main__':
    unittest.main()
