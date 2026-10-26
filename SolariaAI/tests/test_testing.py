import os
import subprocess
from unittest.mock import Mock

import pytest

from solaria_classifier import testing


def test_runner_executes_checks_in_order_and_shuts_down(monkeypatch):
    steps = []
    process = Mock()
    monkeypatch.setattr(testing, "validate_environment", lambda: steps.append("environment"))
    monkeypatch.setattr(testing, "run_step", lambda title, *args, **kw: steps.append(title))
    monkeypatch.setattr(testing.subprocess, "Popen", lambda *args, **kw: process)
    monkeypatch.setattr(testing, "wait_for_service", lambda *args: steps.append("ready"))
    monkeypatch.setattr("sys.argv", ["testing"])
    assert testing.main() == 0
    assert [step.split(":")[0] for step in steps] == [
        "environment",
        "2/6",
        "3/6",
        "4/6",
        "ready",
        "6/6",
    ]
    process.terminate.assert_called_once()
    process.wait.assert_called_once_with(timeout=10)


def test_runner_fails_fast_before_starting_service(monkeypatch):
    process = Mock()
    monkeypatch.setattr(testing, "validate_environment", lambda: None)
    monkeypatch.setattr(testing.subprocess, "Popen", process)

    def failed(*args, **kwargs):
        raise subprocess.CalledProcessError(1, ["test-command"])

    monkeypatch.setattr(testing, "run_step", failed)
    monkeypatch.setattr("sys.argv", ["testing"])
    assert testing.main() == 1
    process.assert_not_called()


@pytest.mark.parametrize("failure", [RuntimeError("startup failed"), KeyboardInterrupt()])
def test_service_is_stopped_after_readiness_failure(monkeypatch, failure):
    process = Mock()
    monkeypatch.setattr(testing.subprocess, "Popen", lambda *args, **kw: process)

    def failed(*args):
        raise failure

    monkeypatch.setattr(testing, "wait_for_service", failed)
    with pytest.raises(type(failure)), testing.mock_service(8000, 1):
        pytest.fail("Failed readiness must not yield a service")
    process.terminate.assert_called_once()


def test_service_cleanup_and_private_token_on_smoke_failure(monkeypatch):
    process = Mock()
    launched = {}
    monkeypatch.setenv("SOLARIA_STARTUP_TOKEN", "host-secret-not-for-tests")

    def launch(*args, **kwargs):
        launched.update(kwargs)
        return process

    monkeypatch.setattr(testing.subprocess, "Popen", launch)
    monkeypatch.setattr(testing, "wait_for_service", lambda *args: None)
    with pytest.raises(RuntimeError, match="smoke failed"), testing.mock_service(8000, 1) as env:
        assert env["SOLARIA_STARTUP_TOKEN"] != os.environ["SOLARIA_STARTUP_TOKEN"]
        assert env == launched["env"]
        raise RuntimeError("smoke failed")
    process.terminate.assert_called_once()


def test_service_force_kills_if_shutdown_times_out(monkeypatch):
    process = Mock()
    process.wait.side_effect = [subprocess.TimeoutExpired("serve", 10), 0]
    monkeypatch.setattr(testing.subprocess, "Popen", lambda *args, **kw: process)
    monkeypatch.setattr(testing, "wait_for_service", lambda *args: None)
    with testing.mock_service(8000, 1):
        pass
    process.kill.assert_called_once()
    assert process.wait.call_count == 2


def test_readiness_detects_early_service_exit():
    process = Mock(returncode=1)
    process.poll.return_value = 1
    with pytest.raises(RuntimeError, match="exited before readiness"):
        testing.wait_for_service(process, 8000, "private", 1)


def test_readiness_has_bounded_timeout():
    process = Mock()
    process.poll.return_value = None
    with pytest.raises(RuntimeError, match="did not become ready"):
        testing.wait_for_service(process, 8000, "private", 0)
