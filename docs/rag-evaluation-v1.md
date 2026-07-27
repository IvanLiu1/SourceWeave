# RAG Evaluation Dataset v1

## Goal

The first evaluation dataset measures retrieval, reranking, answer correctness, evidence coverage,
and abstention with reproducible English public data.

- 120 examples from the official HotpotQA distractor development split.
- 30 officially unanswerable examples from the SQuAD 2.0 development split.
- Random seed: `20260727`.
- Rerank candidate size: `50`; reported result size: `5`.

HotpotQA distractor dev is hard-only, so the 120 examples are stratified by question type:

- 60 `bridge` questions.
- 60 `comparison` questions.

SQuAD examples must have `is_impossible == true`. They are evaluated separately and must not be
included in HotpotQA retrieval averages.

Official sources:

- HotpotQA: <https://hotpotqa.github.io/>
- SQuAD 2.0: <https://rajpurkar.github.io/SQuAD-explorer/>

Both licenses and attribution requirements must be retained if generated subsets are committed or
redistributed.

## Build the normalized dataset

Download `hotpot_dev_distractor_v1.json` and `dev-v2.0.json` from the official project pages, then run:

```bash
python3 scripts/prepare_rag_eval_dataset.py \
  --hotpot /path/to/hotpot_dev_distractor_v1.json \
  --squad /path/to/dev-v2.0.json \
  --output evaluation/datasets/rag-eval-en-v1
```

The command produces:

- `cases.jsonl`: questions, answers, answerability labels, and gold supporting facts.
- `corpus.jsonl`: deduplicated passages to index once in Elasticsearch.
- `manifest.json`: dataset version, seed, counts, and selection strata.

All passages from the selected cases form one shared corpus. Do not create a separate index for each
question, because that would make retrieval unrealistically easy.

## Rerank experiment

For every HotpotQA question, Elasticsearch executes once and returns the original top 50 candidates.
Both variants consume that exact ordered candidate list:

- Baseline A: take the first five candidates in original ES order.
- Rerank B: rerank all 50 candidates, then take the first five.

The following values must be identical between the paired A/B rows: dataset version, case ID, ES index
snapshot, query, candidate passage IDs and their original ES scores, embedding model, candidate size,
chunking configuration, and permission scope. Store the 50 original candidate IDs before calling the
reranker so a run can be audited without executing ES again.

Report paired deltas for:

- `Recall@5`: retrieved gold supporting facts divided by all gold supporting facts.
- `AllEvidenceHit@5`: one only when every required supporting fact is present in the top five.
- `nDCG@5`: relevance 2 for an official supporting fact, 1 for a manually adjudicated useful passage,
  and 0 otherwise.
- `MRR@5`: reciprocal rank of the first relevant result in the reported Top 5.
- Rerank latency p50/p95, error rate, and fallback rate.

`CandidateRecall@50` is a candidate-generation diagnostic and must be identical for A and B because reranking
cannot add evidence that is absent from the fixed candidate set.

## Prediction format and offline scoring

Write one JSON object per `(caseId, variant)` to a JSONL file. A HotpotQA row has this shape:

```json
{
  "caseId": "hotpotqa:<official-id>",
  "variant": "baseline",
  "candidates": [
    {"passageId": "hotpotqa-...", "esScore": 12.34}
  ],
  "retrievedPassageIds": ["hotpotqa-..."],
  "answer": "short answer or INSUFFICIENT_EVIDENCE",
  "citedPassageIds": ["hotpotqa-..."],
  "latencyMs": 143.2,
  "rerankLatencyMs": 31.5,
  "rerankFallback": false
}
```

`candidates` must contain exactly 50 ordered entries and `retrievedPassageIds` exactly five IDs. Use
the same captured `candidates` array for A and B. The scorer rejects a run if IDs, order, or ES scores
differ; it also verifies that baseline output is exactly the first five candidate IDs.

SQuAD2 rows use the same common fields but do not require retrieval fields. Score a completed run with:

```bash
python3 scripts/score_rag_eval.py \
  --cases evaluation/datasets/rag-eval-en-v1/cases.jsonl \
  --corpus evaluation/datasets/rag-eval-en-v1/corpus.jsonl \
  --predictions /path/to/predictions.jsonl \
  --output /path/to/evaluation-report
```

The scorer writes machine-readable `summary.json` and resume/report-friendly `report.md`, including
paired 95% bootstrap confidence intervals.

## Answer correctness

Evaluation responses use a parseable suffix while keeping the normal answer body:

```text
Answer: <short answer or INSUFFICIENT_EVIDENCE>
Evidence: [1], [3]
```

For the 120 HotpotQA cases, report:

- `Answer EM`: exact match after lowercasing, removing punctuation/articles, and normalizing whitespace.
- `Answer Token F1`: token overlap with the reference answer using the official HotpotQA normalization.
- `Citation Precision`: cited chunks containing a gold supporting fact divided by all cited chunks.
- `Citation Recall`: cited gold supporting facts divided by all required supporting facts.
- `Joint Accuracy`: answer exact match and complete gold-evidence citation coverage in the same case.

For the 30 SQuAD 2.0 cases, report:

- `Abstention Accuracy`: output is `INSUFFICIENT_EVIDENCE` and the answer body contains no asserted answer.
- `False Answer Rate`: the model asserts an answer despite the official unanswerable label.

Do not combine HotpotQA answer accuracy and SQuAD abstention accuracy into one score. Display the two
tracks separately, plus macro averages by HotpotQA question type.

## Human adjudication and reporting

Official supporting facts initialize the qrels. Blindly review every passage that appears in only one
variant's top five; add relevance 1 only when it is genuinely useful for the reference answer. The
reviewer must not know whether a result came from A or B.

Use paired bootstrap resampling by case to produce 95% confidence intervals for A/B deltas. Retrieval is
run once against a frozen index. Generation uses temperature 0; rerun a fixed 30-case stability subset
three times and report mean and standard deviation if the provider is still nondeterministic.

Every published result must include the sample count, absolute metric values, absolute percentage-point
delta, relative delta, dataset manifest, Git commit, model identifiers, prompts, candidate size, top K,
chunk size, overlap, and latency percentile definition.
