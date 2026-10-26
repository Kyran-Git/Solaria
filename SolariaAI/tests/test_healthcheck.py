from unittest.mock import Mock

import pytest

from solaria_classifier import healthcheck


def test_healthcheck_requires_token(monkeypatch):
    monkeypatch.delenv("SOLARIA_STARTUP_TOKEN", raising=False)
    assert healthcheck.main() == 1


@pytest.mark.parametrize(
    ("status", "body", "expected"),
    [(200, b'{"status":"ok"}', 0), (401, b"{}", 1), (200, b"bad-json", 1)],
)
def test_healthcheck_authenticates_and_closes(monkeypatch, status, body, expected):
    monkeypatch.setenv("SOLARIA_STARTUP_TOKEN", "private-token")
    connection = Mock()
    connection.getresponse.return_value.status = status
    connection.getresponse.return_value.read.return_value = body
    monkeypatch.setattr(
        "solaria_classifier.healthcheck.HTTPConnection", lambda *args, **kwargs: connection
    )
    assert healthcheck.main() == expected
    connection.request.assert_called_once_with(
        "GET", "/v1/health", headers={"Authorization": "Bearer private-token"}
    )
    connection.close.assert_called_once()


def test_healthcheck_connection_failure(monkeypatch):
    monkeypatch.setenv("SOLARIA_STARTUP_TOKEN", "private-token")
    connection = Mock()
    connection.request.side_effect = OSError("offline")
    monkeypatch.setattr(
        "solaria_classifier.healthcheck.HTTPConnection", lambda *args, **kwargs: connection
    )
    assert healthcheck.main() == 1
    connection.close.assert_called_once()
