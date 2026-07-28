#!/usr/bin/env python3
"""Safely overlay complete targeted-retry pairs onto a RAG evaluation run."""

from __future__ import annotations

import argparse
import json
import os
import shutil
from pathlib import Path
from typing import Any


class MergeError(ValueError):
    pass


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            value = json.loads(line)
            if not isinstance(value, dict):
                raise MergeError(f"{path}:{line_number} must contain a JSON object")
            rows.append(value)
    return rows


def row_key(row: dict[str, Any]) -> tuple[str, str]:
    case_id = row.get("caseId")
    variant = row.get("variant")
    if not isinstance(case_id, str) or not case_id:
        raise MergeError("Every prediction must have a non-empty caseId")
    if variant not in {"baseline", "rerank"}:
        raise MergeError(f"Unsupported prediction variant for {case_id}: {variant}")
    return case_id, variant


def index_rows(rows: list[dict[str, Any]], label: str) -> dict[tuple[str, str], dict[str, Any]]:
    indexed: dict[tuple[str, str], dict[str, Any]] = {}
    for row in rows:
        key = row_key(row)
        if key in indexed:
            raise MergeError(f"Duplicate {label} prediction: {key}")
        indexed[key] = row
    return indexed


def merge_rows(
    original: list[dict[str, Any]], retry: list[dict[str, Any]]
) -> tuple[list[dict[str, Any]], list[str]]:
    original_by_key = index_rows(original, "original")
    retry_by_key = index_rows(retry, "retry")
    retry_case_ids = sorted({case_id for case_id, _ in retry_by_key})
    if not retry_case_ids:
        raise MergeError("Retry prediction file is empty")

    for case_id in retry_case_ids:
        expected = {(case_id, "baseline"), (case_id, "rerank")}
        actual = {key for key in retry_by_key if key[0] == case_id}
        if actual != expected:
            raise MergeError(f"Retry case {case_id} must contain one baseline and one rerank row")
        if not expected.issubset(original_by_key):
            raise MergeError(f"Retry case {case_id} does not exist as a complete original pair")
        if retry_by_key[(case_id, "rerank")].get("rerankFallback") is not False:
            raise MergeError(f"Retry case {case_id} still has rerankFallback=true")

    merged = [retry_by_key.get(row_key(row), row) for row in original]
    if len(merged) != len(original):
        raise MergeError("Merged prediction count changed unexpectedly")
    return merged, retry_case_ids


def write_jsonl_atomic(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    try:
        with temporary.open("w", encoding="utf-8", newline="\n") as handle:
            for row in rows:
                handle.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")))
                handle.write("\n")
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--original", required=True, type=Path)
    parser.add_argument("--retry", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--backup", type=Path)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    original = read_jsonl(args.original)
    retry = read_jsonl(args.retry)
    merged, retry_case_ids = merge_rows(original, retry)
    if args.backup:
        if args.backup.exists():
            raise MergeError(f"Backup already exists: {args.backup}")
        args.backup.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(args.original, args.backup)
    write_jsonl_atomic(args.output, merged)
    print(json.dumps({
        "predictionCount": len(merged),
        "replacedCaseCount": len(retry_case_ids),
        "replacedPredictionCount": len(retry_case_ids) * 2,
    }, indent=2))


if __name__ == "__main__":
    main()
