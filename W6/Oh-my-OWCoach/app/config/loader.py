from __future__ import annotations

from pathlib import Path
from typing import Any

try:
    import yaml
except ModuleNotFoundError:  # pragma: no cover - used before dependencies are installed.
    yaml = None


DEFAULT_CONFIG_PATH = Path("app/config/pipeline.yaml")


def load_pipeline_config(path: Path = DEFAULT_CONFIG_PATH) -> dict[str, Any]:
    if not path.exists():
        raise FileNotFoundError(f"Pipeline config not found: {path}")
    raw_config = path.read_text(encoding="utf-8")
    if yaml is not None:
        config = yaml.safe_load(raw_config) or {}
    else:
        config = parse_simple_yaml(raw_config)
    if not isinstance(config, dict):
        raise ValueError(f"Pipeline config must be a mapping: {path}")
    return config


def parse_simple_yaml(raw_config: str) -> dict[str, Any]:
    config: dict[str, Any] = {}
    current_section: dict[str, Any] | None = None

    for raw_line in raw_config.splitlines():
        line = raw_line.rstrip()
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if not line.startswith(" ") and line.endswith(":"):
            section = line[:-1].strip()
            current_section = {}
            config[section] = current_section
            continue
        if current_section is None or ":" not in line:
            continue
        key, raw_value = line.strip().split(":", 1)
        current_section[key.strip()] = parse_scalar(raw_value.strip())
    return config


def parse_scalar(value: str) -> Any:
    if value.startswith('"') and value.endswith('"'):
        return value[1:-1]
    try:
        if "." in value:
            return float(value)
        return int(value)
    except ValueError:
        return value


def get_nested(config: dict[str, Any], path: str, default: Any = None) -> Any:
    current: Any = config
    for key in path.split("."):
        if not isinstance(current, dict) or key not in current:
            return default
        current = current[key]
    return current
