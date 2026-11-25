import numpy as np
import pytest

cv2 = pytest.importorskip("cv2")

from solaria_vision.clean import CleaningConfig, clean_image
from solaria_vision.quality import QualityConfig, evaluate_quality


def _page(brightness: int = 220) -> np.ndarray:
    image = np.full((500, 700, 3), 30, dtype=np.uint8)
    cv2.rectangle(image, (100, 60), (600, 440), (brightness,) * 3, -1)
    cv2.line(image, (180, 180), (500, 320), (20, 20, 20), 5)
    return image


def test_cleaning_detects_and_rectifies_synthetic_page(tmp_path) -> None:
    result = clean_image(_page(), CleaningConfig(output_size=128, debug_dir=str(tmp_path)))
    assert result.version == "cleaning_v1"
    assert result.quality.passed
    assert result.image is not None and result.image.shape == (128, 128, 3)
    assert (tmp_path / "cleaned.png").exists()


@pytest.mark.parametrize(
    ("image", "page_area", "code"),
    [
        (np.zeros((100, 100, 3), dtype=np.uint8), 0.5, "too_dark"),
        (np.full((100, 100, 3), 255, dtype=np.uint8), 0.5, "too_bright"),
        (np.full((100, 100, 3), 120, dtype=np.uint8), 0.5, "no_drawing_found"),
    ],
)
def test_quality_gates_return_actionable_reason_codes(image, page_area, code) -> None:
    result = evaluate_quality(
        image,
        page_area,
        QualityConfig(min_laplacian_variance=10.0),
    )
    assert result.reason_code == code
    assert result.message


def test_missing_page_is_rejected() -> None:
    result = clean_image(np.full((100, 100, 3), 120, dtype=np.uint8))
    assert result.quality.reason_code == "no_drawing_found"
    assert result.image is None


def _drawing():
    image = np.full((240, 320, 3), 245, dtype=np.uint8)
    cv2.circle(image, (160, 120), 70, (10, 10, 10), 5)
    return image


def test_drawing_without_page_quad_is_cleaned():
    result = clean_image(_drawing(), CleaningConfig(output_size=128))
    assert result.quality.passed
    assert result.page_corners is None
    assert result.image.shape == (128, 128, 3)


@pytest.mark.parametrize(
    "defect", ["too_blurry", "low_contrast", "drawing_too_small", "drawing_off_centre"]
)
def test_additional_quality_gates(defect):
    image = _drawing()
    if defect == "too_blurry":
        image = cv2.GaussianBlur(image, (31, 31), 9)
    elif defect == "low_contrast":
        image[:] = 245
        cv2.circle(image, (160, 120), 70, (228, 228, 228), 5)
    elif defect == "drawing_too_small":
        image[:] = 245
        cv2.circle(image, (160, 120), 4, (10, 10, 10), 2)
    else:
        image[:] = 245
        cv2.circle(image, (35, 120), 25, (10, 10, 10), 4)
    result = evaluate_quality(image)
    assert not result.passed and result.reason_code == defect


def test_blank_page_cannot_become_a_prediction():
    image = np.full((480, 640, 3), 30, dtype=np.uint8)
    cv2.rectangle(image, (60, 40), (580, 440), (245, 245, 245), -1)
    result = clean_image(image)
    assert not result.quality.passed and result.image is None


def _uneven_paper(level: float, height: int = 360, width: int = 420) -> np.ndarray:
    yy, xx = np.mgrid[0:height, 0:width].astype(np.float32)
    gradient = (xx / width - 0.5) * 30.0 + (yy / height - 0.5) * 20.0
    luma = np.clip(level + gradient, 0, 255).astype(np.uint8)
    return np.repeat(luma[..., None], 3, axis=2)


@pytest.mark.parametrize(
    ("paper_level", "stroke_bgr", "thickness", "mean_range"),
    [
        (90.0, (18, 18, 18), 4, (155.0, 220.0)),
        (175.0, (25, 25, 25), 4, (190.0, 232.0)),
        (242.0, (25, 25, 25), 4, (200.0, 235.0)),
        (195.0, (145, 145, 145), 2, (195.0, 232.0)),
    ],
    ids=["dim_page", "normal_page", "bright_page", "pencil_light"],
)
def test_lighting_normalization_bounds_brightness_and_clipping(
    paper_level: float,
    stroke_bgr: tuple[int, int, int],
    thickness: int,
    mean_range: tuple[float, float],
) -> None:
    image = _uneven_paper(paper_level)
    cv2.circle(image, (210, 180), 80, stroke_bgr, thickness)
    if stroke_bgr[0] >= 120:
        for y in range(130, 235, 10):
            cv2.line(image, (155, y), (265, y), (stroke_bgr[0] + 10,) * 3, 1)

    result = clean_image(image, CleaningConfig(output_size=256))
    assert result.quality.passed and result.image is not None
    gray = cv2.cvtColor(result.image, cv2.COLOR_BGR2GRAY)
    mean_luma = float(gray.mean())
    clipped_white = float((gray == 255).mean())

    assert mean_range[0] <= mean_luma <= mean_range[1]
    assert clipped_white <= 0.01
    if stroke_bgr[0] >= 120:
        paper_p90 = float(np.percentile(gray, 90))
        stroke_min = float(gray.min())
        assert paper_p90 - stroke_min >= 45.0
        assert stroke_min <= 175.0


def test_cleaning_preserves_colour_on_coloured_drawing() -> None:
    image = _uneven_paper(155.0)
    cv2.circle(image, (210, 180), 85, (30, 90, 200), 6)  # orange-red ring (BGR)
    cv2.circle(image, (180, 165), 30, (190, 60, 30), -1)  # filled blue spot
    cv2.line(image, (140, 220), (280, 220), (40, 150, 40), 6)  # green line

    result = clean_image(image, CleaningConfig(output_size=256))
    assert result.quality.passed and result.image is not None
    out = result.image
    assert out.shape == (256, 256, 3) and out.dtype == np.uint8

    # Sample the scaled coordinates of each coloured feature (360x420 -> 256x256).
    blue_px = out[int(165 * 256 / 360), int(180 * 256 / 420)].astype(int)
    red_px = out[int(180 * 256 / 360), int((210 + 85) * 256 / 420)].astype(int)
    green_px = out[int(220 * 256 / 360), int(210 * 256 / 420)].astype(int)

    assert blue_px[0] > blue_px[1] + 80 and blue_px[0] > blue_px[2] + 80
    assert red_px[2] > red_px[1] + 40 and red_px[2] > red_px[0] + 80
    assert green_px[1] > green_px[0] + 60 and green_px[1] > green_px[2] + 60

    max_spread = int((out.max(axis=2) - out.min(axis=2)).max())
    assert max_spread >= 120
    gray = cv2.cvtColor(out, cv2.COLOR_BGR2GRAY)
    assert float((gray == 255).mean()) <= 0.01
