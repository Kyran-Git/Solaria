from __future__ import annotations

import argparse

from .config import TrainConfig
from .export import check_onnx_parity, export_onnx
from .train import train


def main() -> None:
    parser = argparse.ArgumentParser(prog="solaria-classifier")
    sub = parser.add_subparsers(dest="command", required=True)
    t = sub.add_parser("train"); t.add_argument("data_dir"); t.add_argument("--output-dir", default="runs")
    t.add_argument("--epochs", type=int, default=10); t.add_argument("--batch-size", type=int, default=32)
    t.add_argument("--backbone", default="resnet18"); t.add_argument("--seed", type=int, default=42)
    e = sub.add_parser("export"); e.add_argument("checkpoint"); e.add_argument("output")
    p = sub.add_parser("parity"); p.add_argument("checkpoint"); p.add_argument("onnx")
    args = parser.parse_args()
    if args.command == "train":
        train(TrainConfig(data_dir=args.data_dir, output_dir=args.output_dir, epochs=args.epochs,
                          batch_size=args.batch_size, backbone=args.backbone, seed=args.seed))
    elif args.command == "export": export_onnx(args.checkpoint, args.output)
    else: print(check_onnx_parity(args.checkpoint, args.onnx))
