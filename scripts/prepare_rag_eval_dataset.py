#!/usr/bin/env python3
"""Build the reproducible v1 English RAG evaluation dataset.

The default selection is intentionally fixed to:
- 120 hard-only HotpotQA distractor-dev examples, balanced across bridge/comparison type.
- 30 SQuAD 2.0 dev examples whose official label is unanswerable.

Only Python's standard library is required. The script consumes official source JSON files
that the developer downloads separately, then writes a normalized case file, a deduplicated
passage corpus, and a manifest describing the exact selection parameters.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable


HOTPOT_TYPES = ("bridge", "comparison")
# The official distractor development split is intentionally hard-only.
HOTPOT_LEVELS = ("hard",)
DEFAULT_SEED = 20260727
HOTPOT_OFFICIAL_URL = "http://curtis.ml.cmu.edu/datasets/hotpot/hotpot_dev_distractor_v1.json"
SQUAD_OFFICIAL_URL = "https://rajpurkar.github.io/SQuAD-explorer/dataset/dev-v2.0.json"


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as source:
        return json.load(source)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def normalized_text(value: str) -> str:
    return " ".join(value.split())


def passage_id(dataset: str, title: str, text: str) -> str:
    payload = f"{dataset}\0{title}\0{text}".encode("utf-8")
    return f"{dataset}-{hashlib.sha256(payload).hexdigest()[:20]}"


def add_passage(
    passages: dict[str, dict[str, Any]],
    dataset: str,
    title: str,
    sentences: Iterable[str],
) -> str:
    sentence_list = [normalized_text(str(sentence)) for sentence in sentences]
    text = normalized_text(" ".join(sentence_list))
    identifier = passage_id(dataset, title, text)
    candidate = {
        "passageId": identifier,
        "dataset": dataset,
        "title": title,
        "text": text,
        "sentences": sentence_list,
    }
    previous = passages.setdefault(identifier, candidate)
    if previous != candidate:
        raise ValueError(f"Passage hash collision for {identifier}")
    return identifier


def select_hotpot_cases(
    source_rows: list[dict[str, Any]],
    count: int,
    seed: int,
    passages: dict[str, dict[str, Any]],
) -> list[dict[str, Any]]:
    strata = [(question_type, level) for question_type in HOTPOT_TYPES for level in HOTPOT_LEVELS]
    if count % len(strata) != 0:
        raise ValueError(f"HotpotQA count must be divisible by {len(strata)} for balanced sampling")
    quota = count // len(strata)

    grouped: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for row in source_rows:
        key = (str(row.get("type", "")).lower(), str(row.get("level", "")).lower())
        if key in strata:
            grouped[key].append(row)

    rng = random.Random(seed)
    selected: list[dict[str, Any]] = []
    for key in strata:
        candidates = sorted(grouped[key], key=lambda item: str(item.get("_id", "")))
        if len(candidates) < quota:
            raise ValueError(f"Not enough HotpotQA rows for stratum {key}: need {quota}, got {len(candidates)}")
        selected.extend(rng.sample(candidates, quota))

    cases: list[dict[str, Any]] = []
    for row in sorted(selected, key=lambda item: str(item["_id"])):
        context_passages: list[str] = []
        passages_by_title: dict[str, tuple[str, list[str]]] = {}
        for raw_title, raw_sentences in row["context"]:
            title = str(raw_title)
            sentences = [str(sentence) for sentence in raw_sentences]
            identifier = add_passage(passages, "hotpotqa", title, sentences)
            context_passages.append(identifier)
            passages_by_title[title] = (identifier, sentences)

        evidence: list[dict[str, Any]] = []
        for raw_title, raw_sentence_index in row["supporting_facts"]:
            title = str(raw_title)
            sentence_index = int(raw_sentence_index)
            if title not in passages_by_title:
                raise ValueError(f"Supporting title {title!r} is absent from context for {row['_id']}")
            identifier, sentences = passages_by_title[title]
            if sentence_index < 0 or sentence_index >= len(sentences):
                raise ValueError(f"Invalid supporting sentence index for {row['_id']}: {title}[{sentence_index}]")
            evidence.append(
                {
                    "passageId": identifier,
                    "title": title,
                    "sentenceIndex": sentence_index,
                    "sentence": normalized_text(sentences[sentence_index]),
                    "relevance": 2,
                }
            )

        cases.append(
            {
                "caseId": f"hotpotqa:{row['_id']}",
                "dataset": "hotpotqa",
                "task": "multi_hop_qa",
                "questionType": str(row["type"]).lower(),
                "difficulty": str(row["level"]).lower(),
                "question": row["question"],
                "answerable": True,
                "referenceAnswers": [row["answer"]],
                "contextPassageIds": context_passages,
                "goldEvidence": evidence,
            }
        )
    return cases


def collect_squad_unanswerable(source: dict[str, Any]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for article in source.get("data", []):
        title = str(article.get("title", ""))
        for paragraph_index, paragraph in enumerate(article.get("paragraphs", [])):
            context = str(paragraph.get("context", ""))
            for question in paragraph.get("qas", []):
                if question.get("is_impossible") is True:
                    rows.append(
                        {
                            "id": str(question["id"]),
                            "title": title,
                            "paragraphIndex": paragraph_index,
                            "context": context,
                            "question": question["question"],
                        }
                    )
    return rows


def select_squad_cases(
    source: dict[str, Any],
    count: int,
    seed: int,
    passages: dict[str, dict[str, Any]],
) -> list[dict[str, Any]]:
    candidates = sorted(collect_squad_unanswerable(source), key=lambda item: item["id"])
    if len(candidates) < count:
        raise ValueError(f"Not enough SQuAD 2.0 unanswerable rows: need {count}, got {len(candidates)}")

    selected = random.Random(seed + 1).sample(candidates, count)
    cases: list[dict[str, Any]] = []
    for row in sorted(selected, key=lambda item: item["id"]):
        identifier = add_passage(passages, "squad2", row["title"], [row["context"]])
        cases.append(
            {
                "caseId": f"squad2:{row['id']}",
                "dataset": "squad2",
                "task": "unanswerable_qa",
                "questionType": "unanswerable",
                "difficulty": None,
                "question": row["question"],
                "answerable": False,
                "referenceAnswers": [],
                "contextPassageIds": [identifier],
                "goldEvidence": [],
            }
        )
    return cases


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as destination:
        for row in rows:
            destination.write(json.dumps(row, ensure_ascii=False, sort_keys=True))
            destination.write("\n")


def build_dataset(
    hotpot_path: Path,
    squad_path: Path,
    output_dir: Path,
    hotpot_count: int = 120,
    squad_count: int = 30,
    seed: int = DEFAULT_SEED,
) -> dict[str, Any]:
    passages: dict[str, dict[str, Any]] = {}
    hotpot_cases = select_hotpot_cases(read_json(hotpot_path), hotpot_count, seed, passages)
    squad_cases = select_squad_cases(read_json(squad_path), squad_count, seed, passages)
    cases = sorted(hotpot_cases + squad_cases, key=lambda item: item["caseId"])
    corpus = sorted(passages.values(), key=lambda item: item["passageId"])

    output_dir.mkdir(parents=True, exist_ok=True)
    write_jsonl(output_dir / "cases.jsonl", cases)
    write_jsonl(output_dir / "corpus.jsonl", corpus)

    manifest = {
        "version": "rag-eval-en-v1",
        "seed": seed,
        "caseCount": len(cases),
        "corpusPassageCount": len(corpus),
        "license": "CC BY-SA 4.0",
        "sources": {
            "hotpotqa": {
                "homepage": "https://hotpotqa.github.io/",
                "downloadUrl": HOTPOT_OFFICIAL_URL,
                "fileName": hotpot_path.name,
                "sha256": sha256_file(hotpot_path),
            },
            "squad2": {
                "homepage": "https://rajpurkar.github.io/SQuAD-explorer/",
                "downloadUrl": SQUAD_OFFICIAL_URL,
                "fileName": squad_path.name,
                "sha256": sha256_file(squad_path),
            },
        },
        "selection": {
            "hotpotqa": {
                "sourceSplit": "distractor-dev",
                "caseCount": len(hotpot_cases),
                "strata": {
                    f"{question_type}:{level}": hotpot_count // (len(HOTPOT_TYPES) * len(HOTPOT_LEVELS))
                    for question_type in HOTPOT_TYPES
                    for level in HOTPOT_LEVELS
                },
            },
            "squad2": {
                "sourceSplit": "dev-v2.0",
                "caseCount": len(squad_cases),
                "filter": "is_impossible == true",
            },
        },
    }
    with (output_dir / "manifest.json").open("w", encoding="utf-8", newline="\n") as destination:
        json.dump(manifest, destination, ensure_ascii=False, indent=2, sort_keys=True)
        destination.write("\n")
    return manifest


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--hotpot", type=Path, required=True, help="Official hotpot_dev_distractor_v1.json")
    parser.add_argument("--squad", type=Path, required=True, help="Official SQuAD dev-v2.0.json")
    parser.add_argument("--output", type=Path, required=True, help="Directory for cases/corpus/manifest outputs")
    parser.add_argument("--hotpot-count", type=int, default=120)
    parser.add_argument("--squad-count", type=int, default=30)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    manifest = build_dataset(
        args.hotpot,
        args.squad,
        args.output,
        hotpot_count=args.hotpot_count,
        squad_count=args.squad_count,
        seed=args.seed,
    )
    print(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
