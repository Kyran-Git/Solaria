"""Canonical astronomy class list for the Solaria classifier.

This module is the single source of truth for the class ids used by the
dataset folders, the checkpoint class mapping, the ONNX metadata sidecar, the
service contracts, and the mock fixtures.

Two rules that must not drift:

1. **Wire ids are lowercase.** They are dataset directory names and model
   labels, so they must match byte-for-byte across the Python service, the
   exported metadata, and the mod. Display names (``Venus``, ``Saturn``, ...)
   are presentation only and never appear on the wire.
2. **Mercury is intentionally absent.** The required set is the ten bodies
   below; do not add a Mercury folder or label.

The order below is the documented curriculum order. It is *not* the model's
class index order: ``torchvision.datasets.ImageFolder`` sorts directory names
alphabetically, so the saved ``classes`` list from a real run will be
alphabetical. Always read the class order from the checkpoint or metadata
sidecar rather than assuming this tuple's order.
"""

from __future__ import annotations

#: The ten required class ids, in documented curriculum order.
CLASS_IDS: tuple[str, ...] = (
    "sun",
    "earth",
    "moon",
    "mars",
    "jupiter",
    "venus",
    "saturn",
    "neptune",
    "uranus",
    "pluto",
)

#: Human-readable display names. Presentation only; never sent on the wire.
DISPLAY_NAMES: dict[str, str] = {
    "sun": "Sun",
    "earth": "Earth",
    "moon": "Moon",
    "mars": "Mars",
    "jupiter": "Jupiter",
    "venus": "Venus",
    "saturn": "Saturn",
    "neptune": "Neptune",
    "uranus": "Uranus",
    "pluto": "Pluto",
}

#: Classes whose body is a dwarf planet rather than a planet. Copy that must
#: describe Pluto has to say "dwarf planet".
DWARF_PLANETS: frozenset[str] = frozenset({"pluto"})

#: Convenience alias for callers that want a plain list.
CLASS_LABELS: list[str] = list(CLASS_IDS)


def is_valid_class(label: str) -> bool:
    """Return True when ``label`` is one of the ten required class ids."""
    return label in CLASS_IDS


def display_name(label: str) -> str:
    """Return the display name for ``label``, or the label itself if unknown."""
    return DISPLAY_NAMES.get(label, label)
