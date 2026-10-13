#!/usr/bin/env python3
"""실제 서버나 컨테이너를 변경하지 않고 배포 차단 조건을 검사한다."""
import importlib.util
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('preflight', Path(__file__).with_name('check-private-ip.py'))
preflight = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(preflight)
ROOT = Path(__file__).resolve().parents[2]


def config(ip):
    return {'services': {'nginx': {'ports': [
        {'target': 80, 'published': '80'},
        {'target': 19100, 'published': '19100', 'host_ip': ip},
    ]}}}


def interfaces(ip):
    return [{'addr_info': [{'family': 'inet', 'local': ip}]}]


class PrivateIPTest(unittest.TestCase):
    def test_assigned_private_addresses(self):
        for ip in ('10.0.0.69', '10.0.20.127', '10.2.3.4', '172.16.1.2', '192.168.1.2'):
            with self.subTest(ip=ip):
                self.assertEqual(preflight.validate_binding(config(ip), interfaces(ip)), ip)

    def test_missing_address_blocks_deployment(self):
        with self.assertRaisesRegex(ValueError, '이 서버에 없습니다'):
            preflight.validate_binding(config('10.0.20.127'), interfaces('10.0.20.128'))

    def test_unsafe_addresses_rejected_even_when_assigned(self):
        for ip in ('0.0.0.0', '127.0.0.1', '169.254.1.1', '8.8.8.8', '::', '', 'bad\nvalue'):
            with self.subTest(ip=ip), self.assertRaises(ValueError):
                preflight.validate_binding(config(ip), interfaces(ip))

    def test_missing_duplicate_or_wrong_port_rejected(self):
        for ports in ([], [config('10.0.0.69')['services']['nginx']['ports'][1]] * 2,
                      [{'target': 19100, 'published': '19101', 'host_ip': '10.0.0.69'}]):
            with self.subTest(ports=ports), self.assertRaises(ValueError):
                preflight.validate_binding({'services': {'nginx': {'ports': ports}}}, [])

    def test_reads_candidate_with_actual_env_without_mutations(self):
        for environment in ('dev', 'prod'):
            with self.subTest(environment=environment), patch.object(
                preflight, 'read_json', side_effect=[config('10.2.3.4'), interfaces('10.2.3.4')]
            ) as read:
                self.assertEqual(preflight.check(environment, '/tmp/deployment'), '10.2.3.4')
                command = read.call_args_list[0].args[0]
                self.assertEqual(command[command.index('--env-file') + 1], str(Path('/tmp/deployment/.env').resolve()))
                self.assertEqual(command[command.index('-f') + 1], str(ROOT / f'backend/docker-compose.{environment}.yml'))
                self.assertEqual(command[-3:], ['config', '--format', 'json'])
                self.assertEqual(read.call_args_list[1].args[0], ['ip', '-j', '-4', 'addr', 'show'])

    def test_command_failure_does_not_expose_secrets(self):
        with patch.object(preflight.subprocess, 'run', return_value=subprocess.CompletedProcess(
            ['docker'], 1, stdout='secret-value', stderr='secret-value'
        )), self.assertRaises(ValueError) as failure:
            preflight.read_json(['docker'])
        self.assertNotIn('secret-value', str(failure.exception))

    def test_workflows_check_before_file_copy_and_pass_validated_ip(self):
        for environment in ('dev', 'prod'):
            with self.subTest(environment=environment):
                workflow = (ROOT / f'.github/workflows/deploy-{environment}.yml').read_text()
                self.assertLess(workflow.index('배치 전 지표 포트 사설 IP 확인'), workflow.index('name: compose 파일 배치'))
                self.assertIn(f'check-private-ip.py {environment} "$DEPLOY_PATH"', workflow)
                self.assertIn('echo "MONITORING_PRIVATE_IP=$PRIVATE_IP" >> "$GITHUB_ENV"', workflow)


if __name__ == '__main__':
    unittest.main()
