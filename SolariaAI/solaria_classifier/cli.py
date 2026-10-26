from __future__ import annotations

import argparse

from .config import TrainConfig


def main() -> None:
    parser = argparse.ArgumentParser(prog="solaria-classifier")
    sub = parser.add_subparsers(dest="command", required=True)
    t = sub.add_parser("train")
    t.add_argument("data_dir")
    t.add_argument("--output-dir", default="runs")
    t.add_argument("--epochs", type=int, default=10)
    t.add_argument("--batch-size", type=int, default=32)
    t.add_argument("--backbone", default="resnet18")
    t.add_argument("--seed", type=int, default=42)
    e = sub.add_parser("export")
    e.add_argument("checkpoint")
    e.add_argument("output")
    p = sub.add_parser("parity")
    p.add_argument("checkpoint")
    p.add_argument("onnx")
    s = sub.add_parser("serve", help="Run the teacher's authenticated HTTP service")
    mode = s.add_mutually_exclusive_group(required=True)
    mode.add_argument("--mock", action="store_true")
    mode.add_argument("--model")
    s.add_argument("--metadata")
    s.add_argument("--port", type=int, default=8000)
    s.add_argument(
        "--host",
        choices=["127.0.0.1", "0.0.0.0"],
        default="127.0.0.1",
        help="Use 0.0.0.0 only inside Docker with a loopback-published host port and startup token.",
    )
    s.add_argument("--threshold", type=float, default=0.6)
    s.add_argument("--workers", type=int, default=2)
    s.add_argument("--queue-size", type=int, default=30)
    s.add_argument("--timeout", type=float, default=15.0)
    s.add_argument(
        "--scenario",
        choices=[
            "success",
            "low_confidence",
            "quality_blur",
            "quality_no_page",
            "model_unavailable",
            "slow",
        ],
        default="success",
    )
    args = parser.parse_args()
    if args.command == "serve":
        from .http_service import serve

        serve(args)
    elif args.command == "train":
        from .train import train

        train(
            TrainConfig(
                data_dir=args.data_dir,
                output_dir=args.output_dir,
                epochs=args.epochs,
                batch_size=args.batch_size,
                backbone=args.backbone,
                seed=args.seed,
            )
        )
    elif args.command == "export":
        from .export import export_onnx

        export_onnx(args.checkpoint, args.output)
    else:
        from .export import check_onnx_parity

        print(check_onnx_parity(args.checkpoint, args.onnx))
