#!/usr/bin/env python3
"""Entry point for report summary and RAG commands."""

from __future__ import annotations

import sys
import os
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VENV_PYTHON = ROOT / ".venv" / "bin" / "python"


if VENV_PYTHON.exists() and Path(sys.executable) != VENV_PYTHON:
    os.execv(str(VENV_PYTHON), [str(VENV_PYTHON), *sys.argv])


sys.path.insert(0, str(ROOT))

from reports.cli import main  # noqa: E402


if __name__ == "__main__":
    raise SystemExit(main())
