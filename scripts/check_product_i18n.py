#!/usr/bin/env python3
"""Fail when a bundled 0.8 normal-product translation drifts from the base resource contract."""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
RESOURCE_FILES = ("product_080_strings.xml", "about_080_strings.xml")
LOCALES = ("de", "fr", "pl", "nl", "lt")
FORMAT = re.compile(r"%(?!%)(?:(\d+)\$)?(?:[-#+ 0,(<]*)?(?:\d+)?(?:\.\d+)?([a-zA-Z])")


def read_strings(path: Path) -> dict[str, str]:
    root = ET.parse(path).getroot()
    out: dict[str, str] = {}
    for node in root.findall("string"):
        name = node.attrib.get("name")
        if not name or node.attrib.get("translatable", "true").lower() == "false":
            continue
        if name in out:
            raise ValueError(f"duplicate string key {name!r} in {path}")
        out[name] = "".join(node.itertext())
    return out


def read_contract(directory: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    for file_name in RESOURCE_FILES:
        path = directory / file_name
        if not path.exists():
            raise FileNotFoundError(path)
        for key, value in read_strings(path).items():
            if key in out:
                raise ValueError(f"duplicate translated key {key!r} across 0.8 resource files in {directory}")
            out[key] = value
    return out


def placeholders(value: str) -> tuple[tuple[str, str], ...]:
    found = []
    implicit = 0
    for match in FORMAT.finditer(value):
        index = match.group(1)
        if index is None:
            implicit += 1
            index = f"implicit:{implicit}"
        found.append((index, match.group(2).lower()))
    return tuple(found)


def main() -> int:
    base = read_contract(RES / "values")
    errors: list[str] = []
    for locale in LOCALES:
        directory = RES / f"values-{locale}"
        try:
            translated = read_contract(directory)
        except (FileNotFoundError, ValueError, ET.ParseError) as error:
            errors.append(f"{locale}: {error}")
            continue
        missing = sorted(set(base) - set(translated))
        extra = sorted(set(translated) - set(base))
        if missing:
            errors.append(f"{locale}: missing keys: {', '.join(missing)}")
        if extra:
            errors.append(f"{locale}: extra keys: {', '.join(extra)}")
        for key in sorted(set(base) & set(translated)):
            expected = placeholders(base[key])
            actual = placeholders(translated[key])
            if expected != actual:
                errors.append(
                    f"{locale}:{key}: placeholder contract {actual!r} != base {expected!r}"
                )
    if errors:
        print("Product i18n contract FAILED:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1
    print(f"Product i18n contract OK: {len(base)} translatable keys across {1 + len(LOCALES)} locales")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
