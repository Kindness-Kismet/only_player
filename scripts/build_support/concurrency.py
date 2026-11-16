import argparse

DEFAULT_JOBS = 12
MAX_JOBS = 64


def parse_jobs(value: str) -> int:
    try:
        jobs = int(value)
    except ValueError:
        raise argparse.ArgumentTypeError(f"jobs must be an integer from 1 to {MAX_JOBS}") from None
    if not 1 <= jobs <= MAX_JOBS:
        raise argparse.ArgumentTypeError(f"jobs must be an integer from 1 to {MAX_JOBS}")
    return jobs


def gradle_concurrency_args(jobs: int) -> list[str]:
    return [f"--max-workers={jobs}"]
