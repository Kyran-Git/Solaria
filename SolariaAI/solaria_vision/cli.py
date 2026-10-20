from __future__ import annotations

import argparse
from pathlib import Path

import cv2

from .capture import capture_camera, capture_file
from .clean import CleaningConfig, clean_image


def main() -> None:
    parser = argparse.ArgumentParser(prog="solaria-clean")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--input", type=Path)
    source.add_argument("--camera", type=int)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--debug-dir", type=Path)
    args = parser.parse_args()
    image = capture_file(args.input) if args.input else capture_camera(args.camera)
    result = clean_image(image, CleaningConfig(debug_dir=str(args.debug_dir) if args.debug_dir else None))
    if not result.quality.passed or result.image is None:
        raise SystemExit(f"{result.quality.reason_code}: {result.quality.message}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    cv2.imwrite(str(args.output), result.image)
