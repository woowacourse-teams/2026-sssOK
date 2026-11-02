#!/usr/bin/env python3
"""Local analysis with persisted JSON results."""
import argparse
from pathlib import Path
import sys


def main():
    parser = argparse.ArgumentParser(description='사진 폴더를 얼굴과 몸 특징으로 사람별 분류합니다. 분석은 로컬에서 수행합니다.')
    parser.add_argument('--input', type=Path, required=True, help='입력 사진 폴더. 바로 아래 파일만 처리합니다.')
    parser.add_argument('--output', type=Path, required=True, help='새 결과 폴더. 기존 폴더는 덮어쓰지 않습니다.')
    parser.add_argument('--assets-root', type=Path, default=Path(__file__).resolve().parent.parent,
                        help='yunet.onnx, sface.onnx, ssdlite.pth, osnet.pth가 있는 폴더')
    parser.add_argument('--kind', choices=['PERSON', 'SIMILAR'], default='PERSON')
    parser.add_argument('--threads', type=int, default=4)
    args = parser.parse_args()
    if args.threads < 1:
        parser.error('--threads must be positive')
    if __package__ in (None, ""):
        sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
    if args.kind == 'SIMILAR':
        from pipeline.similar_analysis import run
    else:
        from pipeline.photo_analysis import run
    try:
        result = run(args.input, args.output, args.assets_root, args.threads)
    except KeyboardInterrupt:
        print('실행이 중단됐습니다. 결과 폴더의 job.json을 확인하세요.', file=sys.stderr)
        return 130
    except Exception as exc:
        print(f'실행 실패: {exc}', file=sys.stderr)
        return 1
    return 2 if result['errors'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
