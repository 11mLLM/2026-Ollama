import subprocess

from config import PROJECT_OLLAMA_MODELS


def _run_ollama(args: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["ollama", *args],
        check=False,
        capture_output=True,
        text=True,
    )


def list_running_models() -> list[str]:
    result = _run_ollama(["ps"])
    if result.returncode != 0:
        return []

    models = []
    for line in result.stdout.splitlines()[1:]:
        parts = line.split()
        if parts:
            models.append(parts[0])
    return models


def stop_model(model: str) -> bool:
    result = _run_ollama(["stop", model])
    return result.returncode == 0


def stop_unneeded_models() -> list[str]:
    stopped = []
    for model in list_running_models():
        normalized = model.removesuffix(":latest")
        if model not in PROJECT_OLLAMA_MODELS and normalized not in PROJECT_OLLAMA_MODELS:
            if stop_model(model):
                stopped.append(model)
    return stopped

