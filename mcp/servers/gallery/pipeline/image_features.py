"""Local image preparation and pretrained inference. No experiment result dependencies."""
from __future__ import annotations

import hashlib
import shutil
from pathlib import Path

import cv2
import numpy as np
import torch
import torchvision
from PIL import Image, ImageOps
from torchvision import transforms
from torchvision.models.detection import ssdlite320_mobilenet_v3_large

from .vendor.osnet import osnet_x1_0

SUPPORTED = {'.jpg', '.jpeg', '.png', '.webp', '.heic', '.heif', '.tif', '.tiff', '.bmp'}
# ARM64 measurements: 8 reduces peak memory versus 16; 4 adds no overall saving.
BODY_FEATURE_BATCH_SIZE = 8


def configure_runtime(threads):
    cv2.setNumThreads(threads)
    torch.set_num_threads(threads)
    return {'opencv': cv2.__version__, 'torch': torch.__version__,
            'torchvision': torchvision.__version__}


def resolve_assets(root):
    root = Path(root).resolve()
    assets = {'yunet': root / 'yunet.onnx', 'sface': root / 'sface.onnx',
              'ssdlite': root / 'ssdlite.pth', 'osnet': root / 'osnet.pth'}
    missing = [name for name,path in assets.items() if not path.is_file()]
    if missing:
        raise FileNotFoundError('Missing model files: ' + ', '.join(missing))
    return assets


def prepare_photos(input_dir, output_dir, progress):
    import pillow_heif
    pillow_heif.register_heif_opener()
    output_dir.mkdir()
    sources = sorted((p for p in Path(input_dir).iterdir() if p.is_file()
                      and not p.name.startswith('.') and p.suffix.lower() in SUPPORTED), key=lambda p: p.name)
    if not sources:
        raise ValueError('No supported image files in the input directory')
    photos, errors = [], []
    for index, source in enumerate(sources, 1):
        image_id = f'image_{index:06d}'
        target = output_dir / f'{image_id}.jpg'
        try:
            with Image.open(source) as original:
                if original.width * original.height > 24_000_000:
                    raise ValueError('Photo exceeds the 24-megapixel analysis limit')
                original.load()
                orientation = original.getexif().get(274, 1)
                image = ImageOps.exif_transpose(original).convert('RGB')
                # Preserve existing JPEG pixels to reproduce the selected development baseline.
                passthrough = original.format == 'JPEG' and orientation == 1 and max(image.size) <= 1600
                if passthrough:
                    shutil.copyfile(source, target)
                else:
                    image.thumbnail((1600, 1600), Image.Resampling.LANCZOS)
                    image.save(target, quality=95, subsampling=0)
                width, height = image.size
            photos.append({'image_id': image_id, 'source_filename': source.name,
                           'source_path': str(source.resolve()), 'prepared_path': f'images/{image_id}.jpg',
                           'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                           'width': width, 'height': height, 'face_ids': [], 'body_ids': []})
        except Exception as exc:
            target.unlink(missing_ok=True)
            errors.append({'image_id': image_id, 'source_filename': source.name,
                           'stage': 'preparing', 'reason': 'image_decode_failed', 'detail': str(exc)})
        progress('preparing', processed=index, total=len(sources), errors=len(errors))
    return photos, errors


def face_quality(row, aligned, image_size, largest_face_area):
    width, height = image_size
    x, y, bw, bh = map(float, row[:4])
    eye1, eye2, nose, mouth1, mouth2 = row[4:14].reshape(5, 2)
    eyes = float(np.linalg.norm(eye2 - eye1))
    offset = float(abs(np.dot(nose - (eye1 + eye2) / 2, (eye2 - eye1) / max(eyes, 1e-9))) / max(eyes, 1e-9))
    gray = cv2.cvtColor(aligned, cv2.COLOR_BGR2GRAY)
    quality = {'score': float(row[-1]), 'min_size': min(bw, bh), 'area_ratio': bw * bh / (width * height),
               'relative_size': float(np.sqrt(bw * bh / largest_face_area)),
               'sharpness': float(cv2.Laplacian(gray, cv2.CV_64F).var()), 'contrast': float(gray.std()),
               'eye_width': eyes / bw, 'mouth_width': float(np.linalg.norm(mouth2 - mouth1)) / bw,
               'nose_offset': offset}
    base = [key for key, limit in [('min_size', 64), ('area_ratio', .004), ('relative_size', .50),
                                    ('contrast', 18), ('sharpness', 60)] if quality[key] < limit]
    quality['seed_failures'] = base + [key for key, limit in [('score', .90), ('eye_width', .25),
                                                            ('mouth_width', .12)] if quality[key] < limit]
    if offset > .60:
        quality['seed_failures'].append('nose_offset')
    quality['match_failures'] = [key for key in base if key in ['contrast', 'sharpness']]
    quality['match_failures'] += [key for key, limit in [('min_size', 32), ('score', .85),
                                                       ('eye_width', .18)] if quality[key] < limit]
    if offset > .80:
        quality['match_failures'].append('nose_offset')
    small = cv2.resize(gray, (32, 32)).astype(np.float32)
    low = cv2.dct(small)[:8, :8].flatten()[1:]
    return quality, low > np.median(low)


def extract_faces(photos, output, assets, progress):
    detector = cv2.FaceDetectorYN.create(str(assets['yunet']), '', (320, 320), .75, .3, 5000)
    recognizer = cv2.FaceRecognizerSF.create(str(assets['sface']), '')
    faces, features, hashes, errors = [], [], [], []
    (output / 'faces').mkdir()
    for index, photo in enumerate(photos, 1):
        try:
            image = cv2.imread(str(output / photo['prepared_path']))
            if image is None:
                raise ValueError('Prepared image cannot be decoded')
            h, w = image.shape[:2]
            detector.setInputSize((w, h))
            _, detected = detector.detect(image)
            rows = [row for row in sorted(detected if detected is not None else [], key=lambda row: float(row[0]))
                    if min(row[2:4]) >= 32]
            largest = max((float(row[2] * row[3]) for row in rows), default=1)
            prepared = []
            for row in rows:
                aligned = recognizer.alignCrop(image, row)
                feature = recognizer.feature(aligned).flatten().copy()
                feature /= max(float(np.linalg.norm(feature)), 1e-12)
                quality, image_hash = face_quality(row, aligned, (w, h), largest)
                prepared.append((row, aligned, feature, quality, image_hash))
            for row, aligned, feature, quality, image_hash in prepared:
                fid = len(faces)
                cv2.imwrite(str(output / 'faces' / f'{fid:06d}.jpg'), aligned)
                faces.append({'face_id': fid, 'image_id': photo['image_id'],
                              'bbox': [float(v) for v in row[:4]], 'quality': quality,
                              'crop': f'faces/{fid:06d}.jpg'})
                features.append(feature)
                hashes.append(image_hash)
                photo['face_ids'].append(fid)
        except Exception as exc:
            errors.append({'image_id': photo['image_id'], 'stage': 'detecting_faces',
                           'reason': 'face_analysis_failed', 'detail': str(exc)})
        progress('detecting_faces', processed=index, total=len(photos), faces=len(faces))
    return faces, np.asarray(features, dtype=np.float32).reshape((-1, 128)), hashes, errors


def extract_bodies(photos, output, assets, progress):
    detector = ssdlite320_mobilenet_v3_large(weights=None, weights_backbone=None).eval()
    detector.load_state_dict(torch.load(assets['ssdlite'], map_location='cpu', weights_only=True))
    bodies, errors = [], []
    with torch.inference_mode():
        for index, photo in enumerate(photos, 1):
            try:
                with Image.open(output / photo['prepared_path']) as opened:
                    image = opened.convert('RGB')
                w, h = image.size
                result = detector([transforms.ToTensor()(image)])[0]
                raw = []
                for box, score, label in zip(result['boxes'], result['scores'], result['labels']):
                    if int(label) != 1 or float(score) < .60:
                        continue
                    x1, y1, x2, y2 = map(float, box)
                    x1, y1, x2, y2 = max(0, x1), max(0, y1), min(w, x2), min(h, y2)
                    area = (x2 - x1) * (y2 - y1)
                    if min(x2 - x1, y2 - y1) < 40 or area / (w * h) < .005:
                        continue
                    raw.append({'image_id': photo['image_id'], 'bbox': [x1, y1, x2, y2],
                                'score': float(score), 'area_ratio': area / (w * h)})
                largest = max((b['area_ratio'] for b in raw), default=1)
                for body in sorted(raw, key=lambda b: b['bbox'][0]):
                    body['body_id'] = len(bodies)
                    body['relative_area'] = body['area_ratio'] / largest
                    bodies.append(body)
                    photo['body_ids'].append(body['body_id'])
            except Exception as exc:
                errors.append({'image_id': photo['image_id'], 'stage': 'detecting_bodies',
                               'reason': 'body_detection_failed', 'detail': str(exc)})
            progress('detecting_bodies', processed=index, total=len(photos), bodies=len(bodies))
    del detector
    return bodies, errors


def extract_body_features(bodies, photos, output, assets, progress):
    if not bodies:
        return np.empty((0, 512), dtype=np.float32)
    model = osnet_x1_0(num_classes=1, pretrained=False).eval()
    state = torch.load(assets['osnet'], map_location='cpu', weights_only=True)
    state = state.get('state_dict', state)
    state = {key.removeprefix('module.'): value for key, value in state.items()}
    own = model.state_dict()
    compatible = {key: value for key, value in state.items() if key in own and value.shape == own[key].shape}
    missing = [key for key in own if key not in compatible and not key.startswith('classifier.')]
    if missing:
        raise ValueError('OSNet checkpoint incompatible: ' + ', '.join(missing))
    model.load_state_dict(compatible, strict=False)
    transform = transforms.Compose([transforms.Resize((256, 128)), transforms.ToTensor(),
                                    transforms.Normalize([.485, .456, .406], [.229, .224, .225])])
    paths = {p['image_id']: output / p['prepared_path'] for p in photos}
    (output / 'bodies').mkdir()
    features = []
    with torch.inference_mode():
        for start in range(0, len(bodies), BODY_FEATURE_BATCH_SIZE):
            batch = []
            for body in bodies[start:start + BODY_FEATURE_BATCH_SIZE]:
                with Image.open(paths[body['image_id']]) as opened:
                    crop = opened.convert('RGB').crop(tuple(map(int, body['bbox'])))
                batch.append(transform(crop))
                crop.thumbnail((260, 350))
                crop.save(output / 'bodies' / f'{body["body_id"]:06d}.jpg')
            values = torch.nn.functional.normalize(model(torch.stack(batch)), dim=1).numpy()
            features.append(values)
            progress('extracting_body_features', processed=min(start + BODY_FEATURE_BATCH_SIZE, len(bodies)), total=len(bodies))
    return np.concatenate(features)
