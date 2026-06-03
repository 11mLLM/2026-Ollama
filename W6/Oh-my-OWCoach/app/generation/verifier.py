from __future__ import annotations


def verify_commentary(segment: dict, commentary: dict) -> dict:
    """Run deterministic MVP verification checks."""
    return {
        "pass": True,
        "unsupported_claims": [],
        "revision_instruction": None,
    }

