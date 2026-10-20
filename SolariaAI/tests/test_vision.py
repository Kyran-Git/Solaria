import numpy as np
import pytest

cv2 = pytest.importorskip("cv2")

from solaria_vision.clean import CleaningConfig, clean_image
from solaria_vision.quality import QualityConfig, evaluate_quality


def _page(brightness: int = 220) -> np.ndarray:
    image = np.full((500, 700, 3), 30, dtype=np.uint8)
    cv2.rectangle(image, (100, 60), (600, 440), (brightness,) * 3, 8)
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
        (np.zeros((100, 100, 3), dtype=np.uint8), 0.5, "image_too_dark"),
        (np.full((100, 100, 3), 250, dtype=np.uint8), 0.5, "image_too_bright"),
        (np.full((100, 100, 3), 120, dtype=np.uint8), 0.5, "image_too_blurry"),
        (np.full((100, 100, 3), 120, dtype=np.uint8), 0.05, "page_region_too_small"),
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
    assert result.quality.reason_code == "page_not_found"
    assert result.image is None
