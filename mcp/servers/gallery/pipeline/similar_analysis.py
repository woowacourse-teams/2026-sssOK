"""Offline DINOv2 + ORB, with a ResNet center-composition guard."""
import hashlib
import json
from pathlib import Path

import cv2
import numpy as np
import torch
from PIL import Image
from torchvision import transforms

from .image_features import configure_runtime, prepare_photos
from .photo_analysis import Job, write_json
from .similar_grouping import build_groups, SIMILARITY_FLOOR, CENTER_FLOOR, MIN_GROUP_SIZE

VERSION = 'similar-1.0.0'
POLICY = {'similarityFloor': SIMILARITY_FLOOR, 'centerFloor': CENTER_FLOOR,
          'minGroupSize': MIN_GROUP_SIZE, 'dinoWeight': .7, 'orbWeight': .3,
          'grouping': 'complete-link; disjoint; deterministic input order',
          'quality': 'development-data tuning; not general accuracy validation'}


def load_model(assets, name):
    manifest = json.loads((Path(__file__).parents[1] / 'similar-model-checksums.json').read_text())
    # Hash and load the same open file; no path replacement between validation/loading.
    with (Path(assets) / name).open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != manifest[name]['sha256']:
            raise ValueError('Similar model checksum mismatch')
        stream.seek(0)
        return torch.jit.load(stream, map_location='cpu').eval()


def feature_matrix(photos, output, model, transform, job, stage):
    features = []
    # 첫 사진의 추론이 오래 걸려도 화면이 이전 단계에 머물지 않게 시작을 먼저 알린다.
    job.update(stage, processed=0, total=len(photos))
    with torch.inference_mode():
        for index, photo in enumerate(photos, 1):
            with Image.open(output / photo['prepared_path']) as im:
                tensor = transform(im.convert('RGB')).unsqueeze(0)
            vector = torch.nn.functional.normalize(model(tensor), dim=1).numpy()[0]
            if not np.isfinite(vector).all():
                raise ValueError('Nonfinite model features')
            features.append(vector)
            job.update(stage, processed=index, total=len(photos))
    return np.asarray(features)


def orb_scores(photos, output, job):
    cv2.setRNGSeed(661)
    detector = cv2.ORB_create(nfeatures=500)
    descriptors = []
    # descriptor 준비가 비교 반복보다 먼저 오래 걸릴 수 있어 이 단계의 시작을 먼저 알린다.
    job.update('matching_similar_photos', processed=0, total=len(photos))
    for photo in photos:
        image = cv2.imread(str(output / photo['prepared_path']), cv2.IMREAD_GRAYSCALE)
        if image is None:
            raise ValueError('Prepared image unavailable')
        # Fixed canvas matches the reviewed development policy; general aspect ratios need evaluation.
        descriptors.append(detector.detectAndCompute(cv2.resize(image, (480, 640)), None))
    matrix = np.eye(len(photos))
    matcher = cv2.BFMatcher(cv2.NORM_HAMMING)
    for i, (keys1, desc1) in enumerate(descriptors):
        for j in range(i):
            keys2, desc2 = descriptors[j]
            score = 0.
            if desc1 is not None and desc2 is not None and len(desc2) > 1:
                matches = [a for pair in matcher.knnMatch(desc1, desc2, k=2) if len(pair) == 2
                           for a, b in [pair] if a.distance < .75 * b.distance]
                if len(matches) >= 8:
                    _, mask = cv2.findHomography(
                        np.float32([keys1[m.queryIdx].pt for m in matches]),
                        np.float32([keys2[m.trainIdx].pt for m in matches]), cv2.RANSAC, 4)
                    if mask is not None:
                        score = float(mask.sum() / min(len(keys1), len(keys2)))
            matrix[i, j] = matrix[j, i] = score
        job.update('matching_similar_photos', processed=i + 1, total=len(photos))
    return matrix


def run(input_dir, output, assets_root, threads=2, progress=None):
    input_dir, output = Path(input_dir).resolve(), Path(output).resolve()
    if not input_dir.is_dir() or output == input_dir or input_dir in output.parents:
        raise ValueError('Separate input/output directories required')
    output.mkdir(parents=True, exist_ok=False)
    job = Job(output, input_dir, progress)
    job.state['pipeline_version'] = VERSION
    try:
        configure_runtime(threads)
        photos, errors = prepare_photos(input_dir, output / 'images', job.update)
        write_json(output / 'manifest.json', {'pipeline_version': VERSION, 'policy': POLICY,
                                             'photos': photos, 'preparation_errors': errors})
        if not photos:
            raise ValueError('Every input image failed decoding')
        ids = [p['image_id'] for p in photos]
        normalize = transforms.Normalize([.485, .456, .406], [.229, .224, .225])
        job.update('loading_scene_model')
        model = load_model(assets_root, 'dinov2.pt')
        dino = feature_matrix(photos, output, model, transforms.Compose([
            transforms.Resize((224, 224)), transforms.ToTensor(), normalize]), job, 'extracting_scene_features')
        del model
        job.update('loading_center_model')
        model = load_model(assets_root, 'resnet50.pt')
        center = feature_matrix(photos, output, model, transforms.Compose([
            transforms.Resize(256), transforms.CenterCrop(224), transforms.ToTensor(), normalize]),
            job, 'extracting_center_features')
        del model
        orb = orb_scores(photos, output, job)
        similarity = .7 * (dino @ dino.T) + .3 * np.minimum(orb * 3, 1)
        job.update('grouping_similar_photos')
        groups, small, unassigned = build_groups(similarity, center @ center.T)
        job.update('exporting')
        result = {'pipeline_version': VERSION,
                  'groups': [{'group_id': f'similar_{i + 1:04d}', 'image_ids': [ids[j] for j in g]}
                             for i, g in enumerate(groups)],
                  'small_groups': [[ids[j] for j in g] for g in small],
                  'unassigned_image_ids': [ids[j] for j in unassigned], 'errors': errors,
                  'counts': {'photos_prepared': len(photos), 'groups': len(groups),
                             'grouped_photos': sum(map(len, groups)), 'small_groups': len(small),
                             'small_group_photos': sum(map(len, small)),
                             'unassigned_photos': len(unassigned), 'errors': len(errors)}}
        write_json(output / 'result.json', result)
        job.finish('completed_with_errors' if errors else 'completed', counts=result['counts'])
        return result
    except BaseException:
        job.finish('failed')
        raise
