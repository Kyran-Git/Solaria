"""Private authenticated container health probe (no tokens in arguments or logs)."""

from __future__ import annotations

import json
import os
from http.client import HTTPConnection


def main() -> int:
    token = os.environ.get("SOLARIA_STARTUP_TOKEN")
    if not token:
        return 1
    connection = HTTPConnection("127.0.0.1", 8000, timeout=3)
    try:
        connection.request("GET", "/v1/health", headers={"Authorization": f"Bearer {token}"})
        response = connection.getresponse()
        health = json.loads(response.read())
        return 0 if response.status == 200 and health.get("status") == "ok" else 1
    except (OSError, ValueError):
        return 1
    finally:
        connection.close()


if __name__ == "__main__":
    raise SystemExit(main())
