#: Classes whose body is a dwarf planet rather than a planet. Copy that must
#: describe Pluto has to say "dwarf planet".
DWARF_PLANETS: frozenset[str] = frozenset({"pluto"})


def is_valid_class(label: str) -> bool: