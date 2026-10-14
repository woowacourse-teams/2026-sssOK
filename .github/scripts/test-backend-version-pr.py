#!/usr/bin/env python3
"""격리 Git 저장소에서 신규·재사용·릴리스 버전 정책을 검증한다."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parent


class VersionPolicyTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.git('init', '-q')
        self.git('config', 'user.email', 'test@example.invalid')
        self.git('config', 'user.name', 'Version policy test')
        for name in ('backend-version.sh', 'check-backend-version-pr.sh'):
            path = self.root / '.github/scripts' / name
            path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(SCRIPTS / name, path)
        self.write('backend/gradle.properties', 'version=1.0.0\n')
        self.write('backend/app.txt', 'before\n')
        self.intent('468', 'patch')
        self.commit()
        self.base = self.git('rev-parse', 'HEAD')
        self.git('checkout', '-qb', 'fix/be-#468-monitoring-followup')

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, text=True,
                                       stderr=subprocess.STDOUT).strip()

    def write(self, path, content):
        file = self.root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content)

    def intent(self, issue, bump):
        self.write(f'.github/backend-version-intents/{issue}.txt', bump + '\n')

    def commit(self):
        self.git('add', '.')
        self.git('commit', '-qm', 'fixture')

    def check(self, success=True, head_ref=None):
        env = dict(os.environ)
        env.pop('PR_HEAD_REF', None)
        if head_ref is not None:
            env['PR_HEAD_REF'] = head_ref
        result = subprocess.run(['bash', '.github/scripts/check-backend-version-pr.sh', self.base],
                                cwd=self.root, env=env, text=True, capture_output=True)
        self.assertEqual(result.returncode == 0, success, result.stdout + result.stderr)

    def backend_change(self):
        self.write('backend/app.txt', 'after\n')

    def test_same_issue_reuses_existing_intent(self):
        self.backend_change()
        self.commit()
        self.check()

    def test_detached_ci_uses_explicit_pr_branch(self):
        self.backend_change()
        self.commit()
        self.git('checkout', '--detach', '-q')
        self.check(False)
        self.check(head_ref='fix/be-#468-monitoring-followup')
        self.check(False, head_ref='fix/be-#999-unrelated-issue')
        self.check(False, head_ref='$(touch injected)')
        self.assertFalse((self.root / 'injected').exists())

    def test_other_issue_cannot_reuse_existing_intent(self):
        self.git('checkout', '-qb', 'fix/be-#999-another-issue')
        self.backend_change()
        self.commit()
        self.check(False)

    def test_new_intent_must_match_branch(self):
        self.git('checkout', '-qb', 'fix/be-#999-another-issue')
        self.backend_change()
        self.intent('999', 'patch')
        self.commit()
        self.check()
        self.check(False, head_ref='fix/be-#468-monitoring-followup')

    def test_existing_intent_cannot_change(self):
        self.backend_change()
        self.intent('468', 'minor')
        self.commit()
        self.check(False)

    def test_existing_intent_cannot_be_deleted(self):
        self.backend_change()
        (self.root / '.github/backend-version-intents/468.txt').unlink()
        self.commit()
        self.check(False)

    def test_multiple_new_intents_rejected(self):
        self.backend_change()
        self.intent('999', 'patch')
        self.intent('1000', 'patch')
        self.commit()
        self.check(False)

    def test_invalid_existing_bump_rejected(self):
        self.intent('468', 'invalid')
        self.commit()
        self.base = self.git('rev-parse', 'HEAD')
        self.backend_change()
        self.commit()
        self.check(False)

    def test_consumed_intent_must_be_added_again(self):
        (self.root / '.github/backend-version-intents/468.txt').unlink()
        self.commit()
        self.base = self.git('rev-parse', 'HEAD')
        self.backend_change()
        self.commit()
        self.check(False)
        self.intent('468', 'patch')
        self.commit()
        self.check()

    def test_no_backend_change_needs_no_intent(self):
        self.write('docs/note.md', 'note')
        self.commit()
        self.check()
        self.intent('999', 'patch')
        self.commit()
        self.check(False)

    def test_release_consumes_intents_without_task_branch(self):
        self.git('checkout', '-qb', 'release/prepare')
        self.write('backend/gradle.properties', 'version=1.0.1\n')
        (self.root / '.github/backend-version-intents/468.txt').unlink()
        self.commit()
        self.check()
        self.backend_change()
        self.commit()
        self.check(False)


if __name__ == '__main__':
    unittest.main()
