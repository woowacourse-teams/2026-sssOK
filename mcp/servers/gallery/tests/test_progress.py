"""Progress recording: public shape, single-file handoff, first-step default and download counting."""
import json
from pathlib import Path
import tempfile
import unittest
from uuid import uuid4

from gallery_mcp.config import Settings
from gallery_mcp.inputs import Photo, stage_photos
from gallery_mcp.person_grouping_service import PersonGroupingService
from gallery_mcp.progress import ProgressFile, progress_path, read_progress, snapshot


class SnapshotTest(unittest.TestCase):
    def test_measured_stage_carries_numbers_and_its_unit(self):
        self.assertEqual(snapshot('detecting_faces', 24, 60),
                         {'stage': 'detecting_faces', 'processed': 24, 'total': 60, 'unit': 'PHOTO'})

    def test_body_features_are_counted_in_bodies_not_photos(self):
        self.assertEqual(snapshot('extracting_body_features', 2, 8)['unit'], 'BODY')

    def test_stage_without_measurement_has_no_numbers_or_unit(self):
        self.assertEqual(snapshot('grouping_faces'),
                         {'stage': 'grouping_faces', 'processed': None, 'total': None, 'unit': None})

    def test_stage_that_cannot_be_measured_never_gets_invented_numbers(self):
        self.assertEqual(snapshot('loading_models', 3, 10)['processed'], None)

    def test_invalid_numbers_are_dropped(self):
        for processed, total in [(5, 3), (-1, 3), (True, 3), (1, None), (None, 3), (1.5, 3)]:
            with self.subTest(processed=processed, total=total):
                value = snapshot('detecting_faces', processed, total)
                self.assertEqual((value['processed'], value['total'], value['unit']), (None, None, None))


class ProgressFileTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'progress.json'

    def test_latest_value_is_readable_and_nothing_partial_is_left_behind(self):
        recorder = ProgressFile(self.path)
        recorder.write('downloading_photos', 1, 3)
        recorder.write('downloading_photos', 2, 3)
        self.assertEqual(read_progress(self.path)['processed'], 2)
        self.assertEqual(list(Path(self.temp.name).glob('*.tmp')), [])

    def test_identical_value_is_not_written_again(self):
        recorder = ProgressFile(self.path)
        recorder.write('detecting_faces', 1, 3)
        before = self.path.stat().st_mtime_ns
        self.path.write_text('sentinel')
        recorder.write('detecting_faces', 1, 3)
        self.assertEqual(self.path.read_text(), 'sentinel')
        self.assertNotEqual(before, 0)

    def test_missing_or_corrupt_file_reads_as_nothing_recorded(self):
        self.assertIsNone(read_progress(self.path))
        self.path.write_text('{not json')
        self.assertIsNone(read_progress(self.path))
        self.path.write_text(json.dumps({'processed': 1}))
        self.assertIsNone(read_progress(self.path))


class ViewTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'input').mkdir()
        for name in ('a.jpg', 'b.jpg'):
            (self.root / 'input' / name).write_bytes(b'test')
        self.settings = Settings(data=self.root / 'data', assets=self.root / 'models', token='t' * 32,
                                 input_root=self.root / 'input')
        self.service = PersonGroupingService(self.settings)
        self.execution = str(uuid4())
        self.service.start(1, self.execution, [Photo(mediaId=1, source='a.jpg', extension='jpg'),
                                               Photo(mediaId=2, source='b.jpg', extension='jpg')])

    def view(self):
        return self.service.view(self.service.store.get(1, self.execution))

    def test_queued_execution_has_no_progress(self):
        self.assertEqual(self.view()['progress'], {})

    def test_running_without_recorded_progress_reports_the_first_step(self):
        self.service.store.claim()
        self.assertEqual(self.view()['progress'],
                         {'stage': 'downloading_photos', 'processed': 0, 'total': 2, 'unit': 'PHOTO'})

    def test_running_reports_the_latest_recorded_progress_in_the_public_shape(self):
        self.service.store.claim()
        path = progress_path(self.settings.data, self.execution)
        path.parent.mkdir(parents=True)
        ProgressFile(path).write('detecting_faces', 1, 2)
        self.assertEqual(self.view()['progress'],
                         {'stage': 'detecting_faces', 'processed': 1, 'total': 2, 'unit': 'PHOTO'})

    def test_finished_execution_reports_no_progress(self):
        self.service.store.claim()
        self.service.store.finish(self.execution, 'FAILED', error='ANALYSIS_FAILED')
        self.assertEqual(self.view()['progress'], {})


class StagePhotosProgressTest(unittest.TestCase):
    def test_callback_receives_the_number_of_completed_photos(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'input').mkdir()
            photos = []
            for index in range(3):
                (root / 'input' / f'{index}.jpg').write_bytes(b'test')
                photos.append(Photo(mediaId=index + 1, source=f'{index}.jpg', extension='jpg'))
            settings = Settings(data=root / 'data', assets=root / 'models', token='t' * 32,
                                input_root=root / 'input')
            done = []
            stage_photos(photos, root / 'copy', settings, done.append)
            self.assertEqual(done, [1, 2, 3])


class SettingsTest(unittest.TestCase):
    def test_defaults_are_two_seconds_for_progress_and_fifteen_for_liveness(self):
        settings = Settings(data=Path('/d'), assets=Path('/a'), token='t' * 32)
        self.assertEqual((settings.progress_interval_seconds, settings.heartbeat_interval_seconds), (2, 15))

    def test_intervals_must_be_positive_and_progress_not_slower_than_heartbeat(self):
        for progress, heartbeat in [(0, 15), (-1, 15), (20, 15)]:
            with self.subTest(progress=progress, heartbeat=heartbeat):
                with self.assertRaises(ValueError):
                    Settings(data=Path('/d'), assets=Path('/a'), token='t' * 32,
                             progress_interval_seconds=progress, heartbeat_interval_seconds=heartbeat)
