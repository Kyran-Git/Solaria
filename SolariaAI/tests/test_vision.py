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
