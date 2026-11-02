import hashlib
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch

from scripts.prepare_models import prepare_models


class PrepareModelsTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.source = self.root / 'source'
        self.source.mkdir()
        (self.source / 'weights').write_bytes(b'verified')
        self.output = self.root / 'models'
        self.output.mkdir()
        self.target = self.output / 'model.pth'
        self.target.write_bytes(b'previous')
        self.manifest = {'model.pth': {'experimentPath': 'weights',
            'sha256': hashlib.sha256(b'verified').hexdigest()}}

    def test_valid_copy_replaces_model_and_is_readable_by_container(self):
        prepare_models(self.source, self.output, self.manifest)
        self.assertEqual(self.target.read_bytes(), b'verified')
        self.assertEqual(self.target.stat().st_mode & 0o777, 0o644)
        self.assertEqual(list(self.output.iterdir()), [self.target])

    def test_source_replaced_at_copy_time_preserves_existing_model(self):
        original_copy = shutil.copyfile
        def changed_copy(source, target):
            source.write_bytes(b'changed')
            return original_copy(source, target)
        with patch('scripts.prepare_models.shutil.copyfile', side_effect=changed_copy):
            with self.assertRaisesRegex(SystemExit, 'Model checksum mismatch'):
                prepare_models(self.source, self.output, self.manifest)
        self.assertEqual(self.target.read_bytes(), b'previous')
        self.assertEqual(list(self.output.iterdir()), [self.target])

    def test_failed_copy_cleans_partial_file_and_preserves_existing_model(self):
        def failed_copy(source, target):
            target.write_bytes(b'partial')
            raise OSError('Copy failed')
        with patch('scripts.prepare_models.shutil.copyfile', side_effect=failed_copy):
            with self.assertRaises(OSError):
                prepare_models(self.source, self.output, self.manifest)
        self.assertEqual(self.target.read_bytes(), b'previous')
        self.assertEqual(list(self.output.iterdir()), [self.target])

    def test_invalid_first_copy_does_not_publish_model(self):
        self.target.unlink()
        (self.source / 'weights').write_bytes(b'invalid')
        with self.assertRaises(SystemExit):
            prepare_models(self.source, self.output, self.manifest)
        self.assertEqual(list(self.output.iterdir()), [])
