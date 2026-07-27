import copy
import sys
import unittest
from pathlib import Path


SCRIPTS_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS_DIR))

from score_rag_eval import (  # noqa: E402
    EvaluationError,
    answer_f1,
    build_report,
    exact_match,
)


class ScoreRagEvalTest(unittest.TestCase):

    def setUp(self):
        self.corpus = [
            {
                "passageId": f"passage-{index}",
                "dataset": "hotpotqa",
                "title": f"Passage {index}",
                "text": f"Text {index}",
            }
            for index in range(60)
        ]
        self.cases = [
            {
                "caseId": "hotpotqa:case-1",
                "dataset": "hotpotqa",
                "task": "multi_hop_qa",
                "questionType": "bridge",
                "difficulty": "hard",
                "question": "Who wrote the book?",
                "answerable": True,
                "referenceAnswers": ["The Author"],
                "contextPassageIds": [f"passage-{index}" for index in range(10)],
                "goldEvidence": [
                    {"passageId": "passage-0", "sentenceIndex": 0, "relevance": 2},
                    {"passageId": "passage-10", "sentenceIndex": 0, "relevance": 2},
                ],
            },
            {
                "caseId": "squad2:case-2",
                "dataset": "squad2",
                "task": "unanswerable_qa",
                "questionType": "unanswerable",
                "difficulty": None,
                "question": "An impossible question?",
                "answerable": False,
                "referenceAnswers": [],
                "contextPassageIds": ["passage-59"],
                "goldEvidence": [],
            },
        ]
        self.candidates = [
            {"passageId": f"passage-{index}", "esScore": float(100 - index)}
            for index in range(50)
        ]
        self.predictions = [
            self._hotpot_prediction(
                "baseline",
                [f"passage-{index}" for index in range(5)],
                "Wrong answer",
                ["passage-0"],
            ),
            self._hotpot_prediction(
                "rerank",
                ["passage-0", "passage-10", "passage-2", "passage-3", "passage-4"],
                "An author",
                ["passage-0", "passage-10"],
            ),
            {
                "caseId": "squad2:case-2",
                "variant": "baseline",
                "answer": "INSUFFICIENT_EVIDENCE",
                "citedPassageIds": [],
                "latencyMs": 50.0,
            },
        ]

    def test_scores_metrics_and_paired_delta(self):
        report = build_report(
            self.cases,
            self.corpus,
            self.predictions,
            bootstrap_iterations=100,
            seed=7,
        )

        self.assertTrue(report["validation"]["identicalOrderedCandidatesAndScores"])
        self.assertEqual(1, report["validation"]["pairedHotpotCaseCount"])
        baseline = report["variants"]["baseline"]["hotpotqa"]
        rerank = report["variants"]["rerank"]["hotpotqa"]
        self.assertEqual(1.0, baseline["CandidateRecall@50"])
        self.assertEqual(1.0, rerank["CandidateRecall@50"])
        self.assertEqual(0.5, baseline["Recall@5"])
        self.assertEqual(1.0, rerank["Recall@5"])
        self.assertEqual(0.0, baseline["AllEvidenceHit@5"])
        self.assertEqual(1.0, rerank["AllEvidenceHit@5"])
        self.assertEqual(0.0, baseline["AnswerEM"])
        self.assertEqual(1.0, rerank["AnswerEM"])
        self.assertEqual(1.0, rerank["CitationRecall"])
        self.assertEqual(1.0, rerank["JointAccuracy"])
        self.assertEqual(1.0, report["variants"]["baseline"]["squad2"]["AbstentionAccuracy"])

        recall_delta = report["pairedDeltas"]["hotpotqa"]["Recall@5"]
        self.assertEqual(0.5, recall_delta["absoluteDelta"])
        self.assertEqual([0.5, 0.5], recall_delta["confidenceInterval95"])

    def test_rejects_different_candidate_scores(self):
        predictions = copy.deepcopy(self.predictions)
        predictions[1]["candidates"][0]["esScore"] = 999.0

        with self.assertRaisesRegex(EvaluationError, "exact same ordered candidates and ES scores"):
            build_report(self.cases, self.corpus, predictions, bootstrap_iterations=10)

    def test_rejects_baseline_that_is_not_original_top_five(self):
        predictions = copy.deepcopy(self.predictions)
        predictions[0]["retrievedPassageIds"] = [
            "passage-0",
            "passage-10",
            "passage-2",
            "passage-3",
            "passage-4",
        ]

        with self.assertRaisesRegex(EvaluationError, "baseline Top 5"):
            build_report(self.cases, self.corpus, predictions, bootstrap_iterations=10)

    def test_official_style_answer_normalization(self):
        self.assertEqual(1.0, exact_match("An author!", ["the author"]))
        self.assertEqual(1.0, answer_f1("An author!", "the author"))
        self.assertEqual(0.0, answer_f1("yes", "no"))

    def _hotpot_prediction(self, variant, retrieved, answer, citations):
        return {
            "caseId": "hotpotqa:case-1",
            "variant": variant,
            "candidates": copy.deepcopy(self.candidates),
            "retrievedPassageIds": retrieved,
            "answer": answer,
            "citedPassageIds": citations,
            "latencyMs": 100.0 if variant == "baseline" else 125.0,
            "rerankLatencyMs": 0.0 if variant == "baseline" else 25.0,
            "rerankFallback": False,
        }


if __name__ == "__main__":
    unittest.main()
