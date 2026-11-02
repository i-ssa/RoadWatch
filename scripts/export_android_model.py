"""Export the verified cleaned-v3 checkpoint for RoadWatch's offline Android detector.

Use a Python environment with torch, ultralytics, onnx, and onnxruntime installed.
Usage: python scripts/export_android_model.py /path/to/best.pt
"""
import argparse
import hashlib
import json
import shutil
import tempfile
from pathlib import Path

CHECKPOINT_SHA256 = "9dad6abd86b08e02d76a02f5e0190ddf373b39de9b8487646f01a541e8810291"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkpoint", type=Path)
    args = parser.parse_args()
    digest = hashlib.sha256(args.checkpoint.read_bytes()).hexdigest()
    if digest != CHECKPOINT_SHA256:
        raise SystemExit("Checkpoint does not match the cleaned-v3 model documented by SemProject_MLEngine")
    import onnx
    import onnxruntime as ort
    import ultralytics
    import numpy as np
    from ultralytics import YOLO
    output = Path(__file__).resolve().parents[1] / "app/src/main/assets"
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temporary:
        checkpoint = Path(temporary) / "best.pt"
        shutil.copyfile(args.checkpoint, checkpoint)
        exported = Path(YOLO(str(checkpoint)).export(format="onnx", imgsz=640, batch=1, dynamic=False, simplify=False, opset=17, nms=False, device="cpu"))
        model = onnx.load(str(exported))
        # ONNX Runtime Android 1.22 supports IR <= 10; this export uses no later IR features.
        model.ir_version = 10
        onnx.checker.check_model(model)
        onnx.save(model, str(exported))
        session = ort.InferenceSession(str(exported), providers=["CPUExecutionProvider"])
        result = session.run(None, {session.get_inputs()[0].name: np.zeros((1,3,640,640), dtype=np.float32)})[0]
        assert result.shape == (1,5,8400), result.shape
        assert np.isfinite(result).all()
        shutil.copyfile(exported, output / "pothole.onnx")
    (output / "pothole-model.json").write_text(json.dumps({
        "source_repository": "https://github.com/i-ssa/SemProject_MLEngine",
        "source_commit": "4a8a469b46d48983f379b464c9bce7b0904bd8e4",
        "checkpoint_sha256": digest,
        "onnx_sha256": hashlib.sha256((output / "pothole.onnx").read_bytes()).hexdigest(),
        "ultralytics_version": ultralytics.__version__,
        "input": [1,3,640,640], "output": [1,5,8400], "opset": 17,
        "confidence": 0.25, "iou": 0.45, "classes": ["pothole"]
    }, indent=2) + "\n")
    print("Exported and validated", output / "pothole.onnx")


if __name__ == "__main__":
    main()
