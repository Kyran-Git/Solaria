"""Persistent credentials are generated once and never disclosed in output."""

import os
from http.client import HTTPConnection

import pytest

from solaria_classifier.startup_token import load_token


@pytest.fixture
def token_path(monkeypatch, tmp_path):
    monkeypatch.delenv("SOLARIA_STARTUP_TOKEN", raising=False)
    path = tmp_path / "private" / "startup-token.txt"
    monkeypatch.setenv("SOLARIA_STARTUP_TOKEN_FILE", str(path))
    return path


def test_generation_reuses_private_file(token_path, capsys):
    first = load_token(create=True)
    assert len(first) == 43
    assert token_path.read_text().strip() == first
    assert load_token(create=True) == first
    assert load_token() == first
    if os.name != "nt":
        assert token_path.stat().st_mode & 0o777 == 0o600
    assert first not in capsys.readouterr().out


def test_installations_have_unique_credentials(token_path):
    first = load_token(create=True)
    token_path.unlink()
    assert load_token(create=True) != first


@pytest.mark.parametrize("contents", ["", "\n", "x" * 257, "bad\x00token"])
def test_invalid_file_is_not_silently_replaced(token_path, contents):
    token_path.parent.mkdir()
    token_path.write_text(contents)
    with pytest.raises(ValueError, match="Invalid startup token file"):
        load_token(create=True)
    assert token_path.read_text() == contents


def test_probe_does_not_create_missing_credential(token_path):
    with pytest.raises(FileNotFoundError):
        load_token()
    assert not token_path.exists()


def test_explicit_configuration_is_backward_compatible(token_path, monkeypatch):
    monkeypatch.setenv("SOLARIA_STARTUP_TOKEN", "explicit-private-token")
    assert load_token(create=True) == "explicit-private-token"
    assert not token_path.exists()


def test_healthcheck_uses_file(token_path, monkeypatch):
    from unittest.mock import Mock

    from solaria_classifier import healthcheck

    token = load_token(create=True)
    connection = Mock()
    connection.getresponse.return_value.status = 200
    connection.getresponse.return_value.read.return_value = b'{"status":"ok"}'
    monkeypatch.setattr(healthcheck, "HTTPConnection", lambda *args, **kwargs: connection)
    assert healthcheck.main() == 0
    connection.request.assert_called_once_with(
        "GET", "/v1/health", headers={"Authorization": f"Bearer {token}"}
    )


def test_healthcheck_missing_file_is_unhealthy(token_path):
    from solaria_classifier import healthcheck

    assert healthcheck.main() == 1
    assert not token_path.exists()
