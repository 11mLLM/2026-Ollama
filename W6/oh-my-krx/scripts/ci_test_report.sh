#!/usr/bin/env bash
set -euo pipefail

RESULT_DIR="${BACKEND_TEST_RESULTS_DIR:-backend/build/test-results/test}"

python3 - "$RESULT_DIR" <<'PY'
from __future__ import annotations

import pathlib
import sys
import xml.etree.ElementTree as ET


def escape_annotation(value: str) -> str:
    return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


result_dir = pathlib.Path(sys.argv[1])
print("== Backend test XML failures/errors ==")

if not result_dir.is_dir():
    print(f"No backend test results directory found: {result_dir}")
    raise SystemExit(0)

found = False
for file in sorted(result_dir.glob("*.xml")):
    try:
        root = ET.parse(file).getroot()
    except ET.ParseError as exc:
        found = True
        message = f"{file}: XML parse error: {exc}"
        print(message)
        print(f"::error file={file},title=Backend test report parse error::{escape_annotation(message)}")
        continue

    for testcase in root.iter("testcase"):
        case_name = testcase.attrib.get("name", "unknown-test")
        class_name = testcase.attrib.get("classname", root.attrib.get("name", "unknown-class"))
        for child in testcase:
            if child.tag not in {"failure", "error"}:
                continue
            found = True
            kind = child.tag
            message_attr = child.attrib.get("message", "").strip()
            text = " ".join((child.text or "").split())
            summary = f"{class_name}.{case_name} {kind}: {message_attr} {text}"[:1800].strip()
            print(file)
            print(summary)
            print(f"::error file={file},title=Backend test failure::{escape_annotation(summary)}")

if not found:
    print("No XML failure/error nodes found.")
PY
