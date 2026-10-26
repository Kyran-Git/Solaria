from test_http_service import running_service

from solaria_classifier import smoke


def test_live_smoke_covers_teacher_review_workflow(monkeypatch, capsys):
    with running_service() as server:
        monkeypatch.setenv("SOLARIA_STARTUP_TOKEN", "test-private-token")
        monkeypatch.setattr("sys.argv", ["smoke", "--port", str(server.server_port)])
        smoke.main()
        feed = server.runtime.reviews.feed()["reviews"]
        record = next(r for r in feed if r["prediction"]["request_id"] == "smoke-teacher-review")
        assert record["student_id"] == "synthetic-smoke-student"
        assert record["prediction"]["label"] == "earth"
        assert record["final_label"] == "mars" and record["revision"] == 1
        output = capsys.readouterr().out
        assert "teacher override" in output and "host decision lookup" in output
        assert "test-private-token" not in output
