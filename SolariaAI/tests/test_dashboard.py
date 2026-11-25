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
        assert set(parser.assets) == {
            "/assets/tokens.css",
            "/assets/dashboard.css",
            "/assets/dashboard.js",
        }
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


def test_dashboard_assets_are_strictly_offline():
    web = Path(__file__).parents[1] / "solaria_classifier" / "web"
    tokens = (web / "tokens.css").read_text(encoding="utf-8")
    for token in (
        "--solaria-panel-bg",
        "--solaria-bevel-hi",
        "--solaria-cyan",
        "--solaria-focus-on-light",
        "--solaria-focus-on-dark",
    ):
        assert token in tokens

    for filename, _ in WEB_ASSETS.values():
        content = (web / filename).read_text(encoding="utf-8")
        assert not re.search(r"https?://", content, re.IGNORECASE), filename
        if filename.endswith((".html", ".css")):
            assert not re.search(
                r"(?:@import\b|url\(\s*['\"]?https?://|//[^\s*])", content, re.IGNORECASE
            ), filename


def _wcag_contrast_ratio(hex_fg: str, hex_bg: str) -> float:
    def luminance(hex_color: str) -> float:
        rgb = [int(hex_color[i : i + 2], 16) / 255.0 for i in (1, 3, 5)]
        linear = [
            c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in rgb
        ]
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]

    bright, dark = sorted([luminance(hex_fg), luminance(hex_bg)], reverse=True)
    return (bright + 0.05) / (dark + 0.05)


def test_dashboard_token_contrast_meets_wcag_aa():
    tokens_css = (
        Path(__file__).parents[1] / "solaria_classifier" / "web" / "tokens.css"
    ).read_text(encoding="utf-8")
    tokens = dict(re.findall(r"(--solaria-[\w-]+):\s*(#[0-9A-Fa-f]{6})\s*;", tokens_css))

    pairs = [
        ("--solaria-ink-on-dark", "--solaria-backdrop"),
        ("--solaria-muted-on-dark", "--solaria-backdrop"),
        ("--solaria-eyebrow-cyan", "--solaria-backdrop"),
        ("--solaria-ink", "--solaria-panel-bg"),
        ("--solaria-ink-muted", "--solaria-panel-bg"),
        ("--solaria-ink", "--solaria-well-light"),
        ("--solaria-ink-well-muted", "--solaria-well-light"),
        ("--solaria-ink-code", "--solaria-well-light"),
        ("--solaria-ink-link", "--solaria-panel-bg"),
        ("--solaria-ink-link", "--solaria-well-light"),
        ("--solaria-ink-input", "--solaria-input-bg"),
        ("--solaria-ink-placeholder", "--solaria-input-bg"),
        ("--solaria-cta-ink", "--solaria-cta-top"),
        ("--solaria-cta-ink", "--solaria-cta-bot"),
        ("--solaria-cta-ink", "--solaria-cta-hover-top"),
        ("--solaria-cta-ink", "--solaria-cta-hover-bot"),
        ("--solaria-btn-ink", "--solaria-btn-top"),
        ("--solaria-btn-ink", "--solaria-btn-bot"),
        ("--solaria-btn-ink", "--solaria-btn-hover-top"),
        ("--solaria-btn-ink", "--solaria-btn-hover-bot"),
        ("--solaria-dis-ink", "--solaria-dis-top"),
        ("--solaria-dis-ink", "--solaria-dis-bot"),
        ("--solaria-ink", "--solaria-row-bg"),
        ("--solaria-ink", "--solaria-row-hover"),
        ("--solaria-row-active-ink", "--solaria-row-active-bg"),
        ("--solaria-badge-ink", "--solaria-badge-bg"),
        ("--solaria-badge-ok-ink", "--solaria-badge-ok-bg"),
        ("--solaria-notice-info-ink", "--solaria-notice-info-bg"),
        ("--solaria-notice-ok-ink", "--solaria-notice-ok-bg"),
        ("--solaria-notice-warn-ink", "--solaria-notice-warn-bg"),
        ("--solaria-notice-err-ink", "--solaria-notice-err-bg"),
        ("--solaria-dark-notice-info-ink", "--solaria-dark-notice-info-bg"),
        ("--solaria-dark-notice-ok-ink", "--solaria-dark-notice-ok-bg"),
        ("--solaria-dark-notice-warn-ink", "--solaria-dark-notice-warn-bg"),
        ("--solaria-dark-notice-err-ink", "--solaria-dark-notice-err-bg"),
    ]
    for fg, bg in pairs:
        ratio = _wcag_contrast_ratio(tokens[fg], tokens[bg])
        assert ratio >= 4.5, f"{fg} ({tokens[fg]}) on {bg} ({tokens[bg]}) = {ratio:.2f}:1"
