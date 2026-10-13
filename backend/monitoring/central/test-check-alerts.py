#!/usr/bin/env python3
"""실서버 조회 없이 누락·평가 오류·수집 실패와 실제 firing을 구분한다."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('check_alerts', Path(__file__).with_name('check-alerts.py'))
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class AlertCheckTest(unittest.TestCase):
    def setUp(self):
        self.rules = {'groups': [{'rules': [dict(name=name, type='alerting', health='ok',
            state='inactive', lastEvaluation='2026-10-14T00:00:00Z', alerts=[]) for name in sorted(check.EXPECTED)]}]}
        self.targets = {'result': [dict(metric=dict(environment=e, server=s, job=j), value=[0, '1'])
                                  for e, s, j in sorted(check.TARGETS)]}

    def test_firing_is_reported_without_treating_it_as_deployment_failure(self):
        rule = self.rules['groups'][0]['rules'][0]
        rule.update(state='firing', alerts=[{'labels': {'environment': 'dev', 'server': 'dev', 'host': 'central:13100'}}])
        errors, lines = check.inspect(self.rules, self.targets)
        self.assertEqual(errors, [])
        self.assertTrue(any('firing' in line for line in lines))
        self.assertTrue(any('host=central:13100' in line for line in lines))

    def test_missing_duplicate_old_or_failed_rule(self):
        for case in ('missing', 'duplicate', 'old', 'error', 'unevaluated'):
            with self.subTest(case=case):
                data = copy.deepcopy(self.rules)
                rules = data['groups'][0]['rules']
                if case == 'missing': rules.pop()
                if case == 'duplicate': rules.append(copy.deepcopy(rules[0]))
                if case == 'old': rules[0]['name'] = 'CentralDiskSpaceLow'
                if case == 'error': rules[0]['lastError'] = 'query failed'
                if case == 'unevaluated': rules[0]['lastEvaluation'] = '0001-01-01T00:00:00Z'
                self.assertTrue(check.inspect(data, self.targets)[0])

    def test_down_missing_duplicate_or_wrong_server(self):
        for case in ('down', 'missing', 'duplicate', 'wrong-server'):
            with self.subTest(case=case):
                data = copy.deepcopy(self.targets)
                samples = data['result']
                if case == 'down': samples[0]['value'][1] = '0'
                if case == 'missing': samples.pop()
                if case == 'duplicate': samples.append(copy.deepcopy(samples[0]))
                if case == 'wrong-server': samples[0]['metric']['server'] = 'unknown'
                self.assertTrue(check.inspect(self.rules, data)[0])


if __name__ == '__main__':
    unittest.main()
