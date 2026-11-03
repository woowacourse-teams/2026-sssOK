"""Exercise CLI imports in fresh Python processes without loading ML models."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


RUNNER = Path(__file__).resolve().parents[1] / 'pipeline' / 'run_local.py'


class RunLocalTests(unittest.TestCase):
    def run_cli(self, mode, kind, *, import_error=False, partial=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            gallery = root / 'servers' / 'gallery'
            pipeline = gallery / 'pipeline'
            pipeline.mkdir(parents=True)
            shutil.copyfile(RUNNER, pipeline / 'run_local.py')
            for module, label in (('photo_analysis', 'PERSON'), ('similar_analysis', 'SIMILAR')):
                source = (
                    "raise ImportError('model dependency unavailable')\n" if import_error else
                    "def run(input_dir, output, assets_root, threads):\n"
                    f"    print('{label}', input_dir, output, assets_root, threads)\n"
                    f"    return {{'errors': {['decode'] if partial else []!r}}}\n"
                )
                (pipeline / f'{module}.py').write_text(source)
            if mode == 'script':
                command, cwd = [str(pipeline / 'run_local.py')], root
            elif mode == 'short_module':
                command, cwd = ['-m', 'pipeline.run_local'], gallery
            else:
                command, cwd = ['-m', 'servers.gallery.pipeline.run_local'], root
            env = {key: value for key, value in os.environ.items() if key != 'PYTHONPATH'}
            return subprocess.run(
                [sys.executable, *command, '--kind', kind, '--input', 'photos',
                 '--output', 'result', '--assets-root', 'models', '--threads', '2'],
                cwd=cwd, env=env, capture_output=True, text=True, timeout=10,
            )

    def test_all_entry_points_select_the_requested_pipeline(self):
        for mode in ('script', 'short_module', 'full_module'):
            for kind in ('PERSON', 'SIMILAR'):
                with self.subTest(mode=mode, kind=kind):
                    result = self.run_cli(mode, kind)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertEqual(result.stdout.strip(), f'{kind} photos result models 2')

    def test_import_failure_uses_cli_error_message(self):
        for mode in ('script', 'short_module', 'full_module'):
            with self.subTest(mode=mode):
                result = self.run_cli(mode, 'SIMILAR', import_error=True)
                self.assertEqual(result.returncode, 1)
                self.assertIn('실행 실패: model dependency unavailable', result.stderr)
                self.assertNotIn('Traceback', result.stderr)

    def test_partial_result_preserves_exit_code(self):
        result = self.run_cli('full_module', 'SIMILAR', partial=True)
        self.assertEqual(result.returncode, 2, result.stderr)


if __name__ == '__main__':
    unittest.main()
