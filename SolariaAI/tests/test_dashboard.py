import re
from html.parser import HTMLParser
from http.client import HTTPConnection
from pathlib import Path

import pytest
from test_http_service import running_service

from solaria_classifier.http_service import WEB_ASSETS


def get(server, path, method="GET"):
    connection = HTTPConnection("127.0.0.1", server.server_port, timeout=5)
    try:
        connection.request(method, path)
        response = connection.getresponse()
        return response.status, dict(response.getheaders()), response.read()
    finally:
        connection.close()


@pytest.mark.parametrize("path", WEB_ASSETS)
def test_public_dashboard_assets_never_contain_private_token(path):
    with running_service() as server:
        status, headers, body = get(server, path)
        assert status == 200
        assert headers["Content-Type"] == WEB_ASSETS[path][1]
        assert int(headers["Content-Length"]) == len(body)
        assert headers["Cache-Control"] == "no-store"
        assert headers["X-Content-Type-Options"] == "nosniff"
        assert headers["X-Frame-Options"] == "DENY"
        assert "connect-src 'self'" in headers["Content-Security-Policy"]
        assert b"test-private-token" not in body
        assert b"localStorage" not in body and b"sessionStorage" not in body


@pytest.mark.parametrize(
    ("method", "path"),
    [
        ("GET", "/v1/health"),
        ("POST", "/v1/classify"),
        ("GET", "/v1/reviews"),
        ("GET", "/v1/reviews/" + "a" * 32),
        ("POST", "/v1/reviews/" + "a" * 32 + "/override"),
        ("POST", "/v1/reviews/" + "a" * 32 + "/ack"),
        ("POST", "/"),
        ("GET", "/assets/../http_service.py"),
        ("GET", "/assets/missing.js"),
        ("GET", "/?token=test-private-token"),
    ],
)
def test_dashboard_does_not_bypass_api_auth_or_serve_arbitrary_paths(method, path):
    with running_service() as server:
        status, _, body = get(server, path, method)
        assert status == 401
        assert b"UNAUTHORIZED" in body
        assert b"test-private-token" not in body


class PageParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = []
        self.assets = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if "id" in attrs:
            self.ids.append(attrs["id"])
        if tag == "script":
            self.assets.append(attrs["src"])
        if tag == "link" and attrs.get("rel") == "stylesheet":
            self.assets.append(attrs["href"])


def test_dashboard_contains_help_controls_and_local_assets():
    with running_service() as server:
        _, _, body = get(server, "/")
        parser = PageParser()
        parser.feed(body.decode("utf-8"))
        assert len(parser.ids) == len(set(parser.ids))
        assert {
            "token",
            "connect-form",
            "review-feed",
            "override-form",
            "override-label",
            "history",
        } <= set(parser.ids)
        assert not {"image-file", "classify", "sample", "scenario"} & set(parser.ids)
        assert b'type="file"' not in body
        assert b"Students scan their drawings in Minecraft" in body
        assert set(parser.assets) == {"/assets/dashboard.js", "/assets/dashboard.css"}
        assert b"SOLARIA_STARTUP_TOKEN" in body
        assert b"UNAUTHORIZED" in body


def test_teacher_script_matches_markup_and_never_submits_images():
    web = Path(__file__).parents[1] / "solaria_classifier" / "web"
    parser = PageParser()
    parser.feed((web / "index.html").read_text(encoding="utf-8"))
    script = (web / "dashboard.js").read_text(encoding="utf-8")
    assert set(re.findall(r'el\("([\w-]+)"\)', script)) <= set(parser.ids)
    assert "/v1/reviews" in script and "/override" in script
    assert "/v1/classify" not in script
    assert "innerHTML" not in script
    assert "localStorage" not in script and "sessionStorage" not in script
