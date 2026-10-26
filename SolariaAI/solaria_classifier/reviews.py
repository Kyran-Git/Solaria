"""Bounded, volatile classroom review records; never a training dataset or build command."""

from __future__ import annotations

import copy
import threading
import time
import uuid
from collections import OrderedDict
from datetime import datetime, timezone

from .scan_processor import ServiceError


def timestamp():
    return datetime.now(timezone.utc).isoformat()


class ReviewStore:
    def __init__(self, classes, *, capacity=200, ttl=7200):
        self.classes = tuple(classes)
        self.capacity, self.ttl = capacity, ttl
        self.lock = threading.Lock()
        self.records = OrderedDict()

    def _expire(self):
        now = time.monotonic()
        for scan_id, (created, _) in list(self.records.items()):
            if now - created >= self.ttl:
                del self.records[scan_id]

    def add(self, result, student_id=None):
        scan_id = uuid.uuid4().hex
        record = {
            "scan_id": scan_id,
            "student_id": student_id,
            "received_at": timestamp(),
            "prediction": copy.deepcopy(result),
            "revision": 0,
            "final_label": result["label"],
            "accepted": result["accepted"],
            "decider": "ai",
            "overrides": [],
        }
        with self.lock:
            self._expire()
            while len(self.records) >= self.capacity:
                self.records.popitem(last=False)
            self.records[scan_id] = (time.monotonic(), record)
        return scan_id

    def _get(self, scan_id):
        self._expire()
        if scan_id not in self.records:
            raise ServiceError(404, "NOT_FOUND", "Review expired or was not found.")
        return self.records[scan_id][1]

    def get(self, scan_id):
        with self.lock:
            return copy.deepcopy(self._get(scan_id))

    def feed(self):
        with self.lock:
            self._expire()
            records = copy.deepcopy([record for _, record in reversed(self.records.values())])
        return {
            "api_version": "api_v1",
            "reviews": records,
            "retention": {"capacity": self.capacity, "ttl_seconds": self.ttl, "persistent": False},
        }

    def override(self, scan_id, payload):
        if not isinstance(payload, dict) or set(payload) != {"label", "revision", "note"}:
            raise ServiceError(400, "INPUT_INVALID", "Supply label, revision and note only.")
        label, revision, note = payload["label"], payload["revision"], payload["note"]
        if not isinstance(label, str) or label not in self.classes:
            raise ServiceError(422, "LABEL_NOT_IN_CLASSES", "Use a class advertised by health.")
        if type(revision) is not int or revision < 0:
            raise ServiceError(400, "INPUT_INVALID", "Revision must be a nonnegative integer.")
        if not isinstance(note, str) or len(note) > 500 or any(ord(c) < 32 for c in note):
            raise ServiceError(
                400, "INPUT_INVALID", "Note must be at most 500 printable characters."
            )
        with self.lock:
            record = self._get(scan_id)
            if revision != record["revision"]:
                raise ServiceError(
                    409, "REVIEW_CONFLICT", "Review changed; refresh before overriding."
                )
            if record["prediction"]["quality"]["status"] != "pass":
                raise ServiceError(
                    409, "QUALITY_REJECTED", "Image quality failed; ask for a rescan."
                )
            if record["revision"] >= 20:
                raise ServiceError(
                    409, "REVIEW_CONFLICT", "Override limit reached; start a new scan."
                )
            record["revision"] += 1
            record["final_label"], record["accepted"], record["decider"] = label, True, "teacher"
            record["overrides"].append(
                {
                    "label": label,
                    "note": note.strip(),
                    "at": timestamp(),
                    "revision": record["revision"],
                    "decider": "teacher",
                }
            )
            return copy.deepcopy(record)
