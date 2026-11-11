import tempfile
import unittest
from pathlib import Path
import cv2
import numpy as np
from pipeline.photo_duplicates import independent_photos


class PhotoDuplicateTests(unittest.TestCase):
    def classify(self, images):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            photos = []
            for i, image in enumerate(images):
                cv2.imwrite(str(root / f'{i}.png'), image)
                photos.append({'image_id': str(i), 'prepared_path': f'{i}.png'})
            return list(independent_photos(photos, root).values())

    def test_identical_and_resized_frames_count_once(self):
        image = np.full((192, 144, 3), 100, np.uint8)
        self.assertEqual(self.classify([image, image.copy(), cv2.resize(image, (288, 384))]), ['0'] * 3)

    def test_local_subject_change_survives_same_backdrop(self):
        a = np.full((192, 192, 3), 100, np.uint8)
        b = a.copy(); b[80:112, 80:112] = 200
        self.assertEqual(self.classify([a, b]), ['0', '1'])

    def test_near_duplicates_do_not_chain(self):
        self.assertEqual(self.classify([np.full((96, 96, 3), n, np.uint8) for n in [100, 101, 102]]), ['0', '0', '2'])

    def test_aspect_ratio_change_is_independent(self):
        self.assertEqual(self.classify([np.zeros((96, 96, 3), np.uint8), np.zeros((96, 48, 3), np.uint8)]), ['0', '1'])

    def test_decode_failure_is_explicit(self):
        with self.assertRaises(ValueError):
            independent_photos([{'image_id': 'a', 'prepared_path': 'missing.jpg'}], Path('/nonexistent'))
