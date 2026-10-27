"""Private, persistent service credentials; never print secret values."""

from __future__ import annotations

import os
import secrets
from pathlib import Path


def load_token(*, create: bool = False) -> str | None:
    explicit = os.environ.get("SOLARIA_STARTUP_TOKEN")
    if explicit:
        return explicit
    filename = os.environ.get("SOLARIA_STARTUP_TOKEN_FILE")
    if not filename:
        return None
    path = Path(filename)
    if create:
        path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        try:
            descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        except FileExistsError:
            pass
        else:
            with os.fdopen(descriptor, "w", encoding="ascii") as stream:
                stream.write(secrets.token_urlsafe(32) + "\n")
    token = path.read_text(encoding="ascii").strip()
    if not token or len(token) > 256 or not token.isascii() or not token.isprintable():
        raise ValueError("Invalid startup token file; restore a valid private credential file.")
    return token
