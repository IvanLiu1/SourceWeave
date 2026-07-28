import sys
import unittest
from pathlib import Path


SCRIPTS_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS_DIR))

from merge_rag_eval_retry import MergeError, merge_rows  # noqa: E402


def prediction(case_id: str, variant: str, fallback: bool = False, answer: str = "old") -> dict:
    return {
        "caseId": case_id,
        "variant": variant,
        "rerankFallback": fallback,
        "answer": answer,
    }


class MergeRagEvalRetryTest(unittest.TestCase):
    def test_replaces_complete_pair_without_changing_order(self) -> None:
        original = [
            prediction("case-1", "baseline"),
            prediction("case-1", "rerank", True),
            prediction("case-2", "baseline"),
            prediction("case-2", "rerank"),
        ]
        retry = [
            prediction("case-1", "baseline", answer="new-baseline"),
            prediction("case-1", "rerank", answer="new-rerank"),
        ]

        merged, case_ids = merge_rows(original, retry)

        self.assertEqual(["case-1"], case_ids)
        self.assertEqual(["new-baseline", "new-rerank", "old", "old"], [row["answer"] for row in merged])

    def test_rejects_incomplete_or_still_fallback_retry(self) -> None:
        original = [prediction("case-1", "baseline"), prediction("case-1", "rerank", True)]

        with self.assertRaises(MergeError):
            merge_rows(original, [prediction("case-1", "rerank")])
        with self.assertRaises(MergeError):
            merge_rows(original, [
                prediction("case-1", "baseline"),
                prediction("case-1", "rerank", True),
            ])

    def test_appends_complete_pairs_when_recovering_a_partial_run(self) -> None:
        original = [prediction("case-1", "baseline"), prediction("case-1", "rerank")]
        retry = [prediction("case-2", "baseline"), prediction("case-2", "rerank")]

        with self.assertRaises(MergeError):
            merge_rows(original, retry)

        merged, case_ids = merge_rows(original, retry, append_missing=True)

        self.assertEqual(["case-2"], case_ids)
        self.assertEqual(
            [("case-1", "baseline"), ("case-1", "rerank"),
             ("case-2", "baseline"), ("case-2", "rerank")],
            [(row["caseId"], row["variant"]) for row in merged],
        )


if __name__ == "__main__":
    unittest.main()
