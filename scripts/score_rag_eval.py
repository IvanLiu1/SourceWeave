#!/usr/bin/env python3
"""Score SourceWeave RAG evaluation predictions and validate paired A/B runs.

Only Python's standard library is required. HotpotQA baseline/rerank rows are accepted only
when both variants contain the same 50 ordered Elasticsearch candidates and scores.
"""

from __future__ import annotations

import argparse
import json
import math
import random
import re
import statistics
import string
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable


BASELINE = "baseline"
RERANK = "rerank"
PAIRED_VARIANTS = (BASELINE, RERANK)
EXPECTED_CANDIDATES = 50
TOP_K = 5
DEFAULT_SEED = 20260727
DEFAULT_BOOTSTRAP_ITERATIONS = 2000
ABSTENTION = "INSUFFICIENT_EVIDENCE"
HOTPOT_METRICS = (
    "CandidateRecall@50",
    "Recall@5",
    "AllEvidenceHit@5",
    "nDCG@5",
    "MRR@5",
    "AnswerEM",
    "AnswerTokenF1",
    "CitationPrecision",
    "CitationRecall",
    "JointAccuracy",
)


class EvaluationError(ValueError):
    """Raised when an evaluation input would produce an invalid or unfair comparison."""


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            if not line.strip():
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as error:
                raise EvaluationError(f"Invalid JSON in {path}:{line_number}: {error}") from error
            if not isinstance(value, dict):
                raise EvaluationError(f"Expected an object in {path}:{line_number}")
            rows.append(value)
    return rows


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as destination:
        json.dump(value, destination, ensure_ascii=False, indent=2, sort_keys=True)
        destination.write("\n")


def normalize_answer(value: str) -> str:
    lowered = value.lower()
    without_punctuation = "".join(character for character in lowered if character not in string.punctuation)
    without_articles = re.sub(r"\b(a|an|the)\b", " ", without_punctuation)
    return " ".join(without_articles.split())


def exact_match(prediction: str, references: list[str]) -> float:
    normalized_prediction = normalize_answer(prediction)
    return float(any(normalized_prediction == normalize_answer(reference) for reference in references))


def answer_f1(prediction: str, reference: str) -> float:
    normalized_prediction = normalize_answer(prediction)
    normalized_reference = normalize_answer(reference)
    special_answers = {"yes", "no", "noanswer"}
    if (
        normalized_prediction in special_answers or normalized_reference in special_answers
    ) and normalized_prediction != normalized_reference:
        return 0.0

    prediction_tokens = normalized_prediction.split()
    reference_tokens = normalized_reference.split()
    common = Counter(prediction_tokens) & Counter(reference_tokens)
    common_count = sum(common.values())
    if common_count == 0:
        return 0.0
    precision = common_count / len(prediction_tokens)
    recall = common_count / len(reference_tokens)
    return 2 * precision * recall / (precision + recall)


def max_answer_f1(prediction: str, references: list[str]) -> float:
    return max((answer_f1(prediction, reference) for reference in references), default=0.0)


def percentile(values: list[float], probability: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = (len(ordered) - 1) * probability
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def mean(values: Iterable[float]) -> float | None:
    materialized = list(values)
    return statistics.fmean(materialized) if materialized else None


def validate_candidates(
    case: dict[str, Any],
    prediction: dict[str, Any],
    corpus_ids: set[str],
) -> list[dict[str, Any]]:
    case_id = case["caseId"]
    variant = prediction["variant"]
    candidates = prediction.get("candidates")
    if not isinstance(candidates, list) or len(candidates) != EXPECTED_CANDIDATES:
        actual = len(candidates) if isinstance(candidates, list) else "missing"
        raise EvaluationError(
            f"{case_id}/{variant}: expected exactly {EXPECTED_CANDIDATES} candidates, got {actual}"
        )

    candidate_ids: list[str] = []
    for index, candidate in enumerate(candidates):
        if not isinstance(candidate, dict):
            raise EvaluationError(f"{case_id}/{variant}: candidate {index} must be an object")
        passage_id = candidate.get("passageId")
        score = candidate.get("esScore")
        if not isinstance(passage_id, str) or not passage_id:
            raise EvaluationError(f"{case_id}/{variant}: candidate {index} has no passageId")
        if passage_id not in corpus_ids:
            raise EvaluationError(f"{case_id}/{variant}: unknown candidate passageId {passage_id}")
        if isinstance(score, bool) or not isinstance(score, (int, float)) or not math.isfinite(score):
            raise EvaluationError(f"{case_id}/{variant}: candidate {index} has invalid esScore")
        candidate_ids.append(passage_id)
    if len(set(candidate_ids)) != len(candidate_ids):
        raise EvaluationError(f"{case_id}/{variant}: candidate passage IDs must be unique")

    retrieved = prediction.get("retrievedPassageIds")
    if not isinstance(retrieved, list) or len(retrieved) != TOP_K:
        actual = len(retrieved) if isinstance(retrieved, list) else "missing"
        raise EvaluationError(f"{case_id}/{variant}: expected exactly {TOP_K} retrieved passages, got {actual}")
    if len(set(retrieved)) != len(retrieved):
        raise EvaluationError(f"{case_id}/{variant}: retrieved passage IDs must be unique")
    if any(passage_id not in candidate_ids for passage_id in retrieved):
        raise EvaluationError(f"{case_id}/{variant}: retrieved passages must come from the 50 candidates")
    if variant == BASELINE and retrieved != candidate_ids[:TOP_K]:
        raise EvaluationError(f"{case_id}/{variant}: baseline Top 5 must be the original ES Top 5")
    return candidates


def validate_inputs(
    cases: list[dict[str, Any]],
    corpus: list[dict[str, Any]],
    predictions: list[dict[str, Any]],
) -> dict[str, Any]:
    cases_by_id = {str(case["caseId"]): case for case in cases}
    if len(cases_by_id) != len(cases):
        raise EvaluationError("Case IDs must be unique")
    corpus_ids = {str(passage["passageId"]) for passage in corpus}
    if len(corpus_ids) != len(corpus):
        raise EvaluationError("Corpus passage IDs must be unique")

    predictions_by_key: dict[tuple[str, str], dict[str, Any]] = {}
    variants: set[str] = set()
    for prediction in predictions:
        case_id = prediction.get("caseId")
        variant = prediction.get("variant")
        if case_id not in cases_by_id:
            raise EvaluationError(f"Prediction references unknown case ID: {case_id}")
        if not isinstance(variant, str) or not variant:
            raise EvaluationError(f"{case_id}: prediction variant is required")
        key = (case_id, variant)
        if key in predictions_by_key:
            raise EvaluationError(f"Duplicate prediction for {case_id}/{variant}")
        predictions_by_key[key] = prediction
        variants.add(variant)

        answer = prediction.get("answer")
        if not isinstance(answer, str):
            raise EvaluationError(f"{case_id}/{variant}: answer must be a string")
        citations = prediction.get("citedPassageIds", [])
        if not isinstance(citations, list) or any(not isinstance(item, str) for item in citations):
            raise EvaluationError(f"{case_id}/{variant}: citedPassageIds must be a string array")
        if len(set(citations)) != len(citations):
            raise EvaluationError(f"{case_id}/{variant}: cited passage IDs must be unique")
        if any(passage_id not in corpus_ids for passage_id in citations):
            raise EvaluationError(f"{case_id}/{variant}: cited passage ID is absent from the corpus")

        if cases_by_id[case_id]["dataset"] == "hotpotqa":
            validate_candidates(cases_by_id[case_id], prediction, corpus_ids)

    paired = set(PAIRED_VARIANTS).issubset(variants)
    paired_case_count = 0
    if paired:
        hotpot_cases = [case for case in cases if case["dataset"] == "hotpotqa"]
        for case in hotpot_cases:
            case_id = case["caseId"]
            baseline = predictions_by_key.get((case_id, BASELINE))
            rerank = predictions_by_key.get((case_id, RERANK))
            if baseline is None or rerank is None:
                raise EvaluationError(f"Paired run is missing baseline or rerank for {case_id}")
            if baseline["candidates"] != rerank["candidates"]:
                raise EvaluationError(
                    f"{case_id}: baseline and rerank must use the exact same ordered candidates and ES scores"
                )
            paired_case_count += 1

    return {
        "casesById": cases_by_id,
        "predictionsByKey": predictions_by_key,
        "variants": sorted(variants),
        "paired": paired,
        "pairedCaseCount": paired_case_count,
    }


def retrieval_metrics(case: dict[str, Any], prediction: dict[str, Any]) -> dict[str, float]:
    retrieved = prediction["retrievedPassageIds"]
    candidate_ids = {candidate["passageId"] for candidate in prediction["candidates"]}
    evidence = case["goldEvidence"]
    gold_passage_ids = {fact["passageId"] for fact in evidence}
    candidate_fact_hits = sum(1 for fact in evidence if fact["passageId"] in candidate_ids)
    fact_hits = sum(1 for fact in evidence if fact["passageId"] in retrieved)
    candidate_recall = candidate_fact_hits / len(evidence) if evidence else 0.0
    recall = fact_hits / len(evidence) if evidence else 0.0
    all_evidence = float(bool(evidence) and fact_hits == len(evidence))

    dcg = 0.0
    for rank, passage_id in enumerate(retrieved, start=1):
        relevance = 2 if passage_id in gold_passage_ids else 0
        dcg += (2**relevance - 1) / math.log2(rank + 1)
    ideal_relevances = [2] * min(len(gold_passage_ids), TOP_K)
    ideal_dcg = sum((2**relevance - 1) / math.log2(rank + 1) for rank, relevance in enumerate(ideal_relevances, 1))
    ndcg = dcg / ideal_dcg if ideal_dcg else 0.0

    first_relevant_rank = next(
        (rank for rank, passage_id in enumerate(retrieved, start=1) if passage_id in gold_passage_ids),
        None,
    )
    reciprocal_rank = 1 / first_relevant_rank if first_relevant_rank else 0.0
    return {
        "CandidateRecall@50": candidate_recall,
        "Recall@5": recall,
        "AllEvidenceHit@5": all_evidence,
        "nDCG@5": ndcg,
        "MRR@5": reciprocal_rank,
    }


def answer_and_citation_metrics(case: dict[str, Any], prediction: dict[str, Any]) -> dict[str, float]:
    answer = prediction["answer"]
    references = [str(reference) for reference in case["referenceAnswers"]]
    answer_em = exact_match(answer, references)
    answer_token_f1 = max_answer_f1(answer, references)

    evidence = case["goldEvidence"]
    gold_passage_ids = {fact["passageId"] for fact in evidence}
    citations = prediction.get("citedPassageIds", [])
    correct_citations = sum(1 for passage_id in citations if passage_id in gold_passage_ids)
    citation_precision = correct_citations / len(citations) if citations else 0.0
    cited_fact_count = sum(1 for fact in evidence if fact["passageId"] in citations)
    citation_recall = cited_fact_count / len(evidence) if evidence else 0.0
    return {
        "AnswerEM": answer_em,
        "AnswerTokenF1": answer_token_f1,
        "CitationPrecision": citation_precision,
        "CitationRecall": citation_recall,
        "JointAccuracy": float(answer_em == 1.0 and citation_recall == 1.0),
    }


def score_hotpot_case(case: dict[str, Any], prediction: dict[str, Any]) -> dict[str, float]:
    return retrieval_metrics(case, prediction) | answer_and_citation_metrics(case, prediction)


def score_squad_case(prediction: dict[str, Any]) -> dict[str, float]:
    abstained = float(prediction["answer"].strip().upper() == ABSTENTION)
    return {"AbstentionAccuracy": abstained, "FalseAnswerRate": 1.0 - abstained}


def latency_summary(predictions: list[dict[str, Any]]) -> dict[str, float | int | None]:
    latencies = [float(row["latencyMs"]) for row in predictions if isinstance(row.get("latencyMs"), (int, float))]
    rerank_latencies = [
        float(row["rerankLatencyMs"])
        for row in predictions
        if isinstance(row.get("rerankLatencyMs"), (int, float))
    ]
    fallback_rows = [row for row in predictions if isinstance(row.get("rerankFallback"), bool)]
    return {
        "sampleCount": len(latencies),
        "latencyMsP50": percentile(latencies, 0.50),
        "latencyMsP95": percentile(latencies, 0.95),
        "rerankLatencyMsP50": percentile(rerank_latencies, 0.50),
        "rerankLatencyMsP95": percentile(rerank_latencies, 0.95),
        "rerankFallbackRate": mean(float(row["rerankFallback"]) for row in fallback_rows),
    }


def aggregate_case_metrics(rows: list[dict[str, float]]) -> dict[str, float | int | None]:
    metric_names = sorted({name for row in rows for name in row})
    return {"caseCount": len(rows)} | {name: mean(row[name] for row in rows if name in row) for name in metric_names}


def paired_bootstrap_delta(
    baseline: list[float],
    rerank: list[float],
    iterations: int,
    seed: int,
) -> tuple[float, float]:
    if len(baseline) != len(rerank) or not baseline:
        raise EvaluationError("Paired bootstrap requires equal non-empty samples")
    rng = random.Random(seed)
    deltas: list[float] = []
    for _ in range(iterations):
        indices = [rng.randrange(len(baseline)) for _ in range(len(baseline))]
        baseline_mean = statistics.fmean(baseline[index] for index in indices)
        rerank_mean = statistics.fmean(rerank[index] for index in indices)
        deltas.append(rerank_mean - baseline_mean)
    lower = percentile(deltas, 0.025)
    upper = percentile(deltas, 0.975)
    assert lower is not None and upper is not None
    return lower, upper


def build_report(
    cases: list[dict[str, Any]],
    corpus: list[dict[str, Any]],
    predictions: list[dict[str, Any]],
    bootstrap_iterations: int = DEFAULT_BOOTSTRAP_ITERATIONS,
    seed: int = DEFAULT_SEED,
) -> dict[str, Any]:
    validated = validate_inputs(cases, corpus, predictions)
    cases_by_id = validated["casesById"]
    predictions_by_key = validated["predictionsByKey"]

    scores_by_variant: dict[str, dict[str, dict[str, dict[str, float]]]] = defaultdict(
        lambda: defaultdict(dict)
    )
    predictions_by_variant: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for (case_id, variant), prediction in predictions_by_key.items():
        case = cases_by_id[case_id]
        predictions_by_variant[variant].append(prediction)
        if case["dataset"] == "hotpotqa":
            scores_by_variant[variant]["hotpotqa"][case_id] = score_hotpot_case(case, prediction)
        elif case["dataset"] == "squad2":
            scores_by_variant[variant]["squad2"][case_id] = score_squad_case(prediction)

    variants: dict[str, Any] = {}
    for variant in validated["variants"]:
        hotpot_scores = scores_by_variant[variant]["hotpotqa"]
        squad_scores = scores_by_variant[variant]["squad2"]
        by_question_type: dict[str, Any] = {}
        for question_type in sorted(
            {cases_by_id[case_id]["questionType"] for case_id in hotpot_scores}
        ):
            typed_rows = [
                metrics
                for case_id, metrics in hotpot_scores.items()
                if cases_by_id[case_id]["questionType"] == question_type
            ]
            by_question_type[question_type] = aggregate_case_metrics(typed_rows)
        variants[variant] = {
            "hotpotqa": aggregate_case_metrics(list(hotpot_scores.values())) | {"byQuestionType": by_question_type},
            "squad2": aggregate_case_metrics(list(squad_scores.values())),
            "latency": latency_summary(predictions_by_variant[variant]),
        }

    paired_deltas: dict[str, Any] = {}
    if validated["paired"]:
        baseline_scores = scores_by_variant[BASELINE]["hotpotqa"]
        rerank_scores = scores_by_variant[RERANK]["hotpotqa"]
        paired_case_ids = sorted(baseline_scores)
        for metric_index, metric in enumerate(HOTPOT_METRICS):
            baseline_values = [baseline_scores[case_id][metric] for case_id in paired_case_ids]
            rerank_values = [rerank_scores[case_id][metric] for case_id in paired_case_ids]
            baseline_mean = statistics.fmean(baseline_values)
            rerank_mean = statistics.fmean(rerank_values)
            delta = rerank_mean - baseline_mean
            confidence_interval = paired_bootstrap_delta(
                baseline_values,
                rerank_values,
                bootstrap_iterations,
                seed + metric_index,
            )
            paired_deltas[metric] = {
                "baseline": baseline_mean,
                "rerank": rerank_mean,
                "absoluteDelta": delta,
                "relativeDeltaPercent": (delta / baseline_mean * 100) if baseline_mean else None,
                "confidenceInterval95": list(confidence_interval),
            }

    return {
        "evaluation": {
            "caseCount": len(cases),
            "corpusPassageCount": len(corpus),
            "predictionCount": len(predictions),
            "seed": seed,
            "bootstrapIterations": bootstrap_iterations,
        },
        "validation": {
            "paired": validated["paired"],
            "pairedHotpotCaseCount": validated["pairedCaseCount"],
            "candidateCount": EXPECTED_CANDIDATES,
            "topK": TOP_K,
            "identicalOrderedCandidatesAndScores": validated["paired"],
        },
        "variants": variants,
        "pairedDeltas": {"hotpotqa": paired_deltas} if paired_deltas else {},
    }


def format_percent(value: Any) -> str:
    return "—" if value is None else f"{float(value) * 100:.2f}%"


def render_markdown(report: dict[str, Any]) -> str:
    lines = [
        "# RAG Evaluation Report",
        "",
        (
            f"Cases: {report['evaluation']['caseCount']} · "
            f"Predictions: {report['evaluation']['predictionCount']} · "
            f"Fixed candidates: {report['validation']['candidateCount']} · "
            f"Top K: {report['validation']['topK']}"
        ),
        "",
    ]
    deltas = report.get("pairedDeltas", {}).get("hotpotqa", {})
    if deltas:
        lines.extend(
            [
                "## Baseline vs rerank (HotpotQA)",
                "",
                "| Metric | Baseline | Rerank | Delta | 95% paired bootstrap CI |",
                "|---|---:|---:|---:|---:|",
            ]
        )
        for metric in HOTPOT_METRICS:
            row = deltas[metric]
            interval = row["confidenceInterval95"]
            lines.append(
                f"| {metric} | {format_percent(row['baseline'])} | {format_percent(row['rerank'])} "
                f"| {format_percent(row['absoluteDelta'])} | "
                f"[{format_percent(interval[0])}, {format_percent(interval[1])}] |"
            )
        lines.append("")

    lines.extend(["## Variant details", ""])
    for variant, details in report["variants"].items():
        hotpot = details["hotpotqa"]
        squad = details["squad2"]
        latency = details["latency"]
        lines.extend(
            [
                f"### {variant}",
                "",
                f"- HotpotQA cases: {hotpot['caseCount']}; Recall@5: {format_percent(hotpot.get('Recall@5'))}; "
                f"Answer EM: {format_percent(hotpot.get('AnswerEM'))}; Joint Accuracy: {format_percent(hotpot.get('JointAccuracy'))}.",
                f"- SQuAD2 cases: {squad['caseCount']}; Abstention Accuracy: {format_percent(squad.get('AbstentionAccuracy'))}; "
                f"False Answer Rate: {format_percent(squad.get('FalseAnswerRate'))}.",
                f"- End-to-end latency p50/p95: {latency['latencyMsP50']} / {latency['latencyMsP95']} ms; "
                f"rerank latency p50/p95: {latency['rerankLatencyMsP50']} / {latency['rerankLatencyMsP95']} ms.",
                "",
            ]
        )
    return "\n".join(lines).rstrip() + "\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--corpus", type=Path, required=True)
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True, help="Directory for summary.json and report.md")
    parser.add_argument("--bootstrap-iterations", type=int, default=DEFAULT_BOOTSTRAP_ITERATIONS)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.bootstrap_iterations <= 0:
        raise EvaluationError("--bootstrap-iterations must be positive")
    report = build_report(
        read_jsonl(args.cases),
        read_jsonl(args.corpus),
        read_jsonl(args.predictions),
        bootstrap_iterations=args.bootstrap_iterations,
        seed=args.seed,
    )
    write_json(args.output / "summary.json", report)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "report.md").write_text(render_markdown(report), encoding="utf-8", newline="\n")
    print(json.dumps(report["validation"], ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
