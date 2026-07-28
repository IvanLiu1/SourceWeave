import json
import sys
import tempfile
import unittest
from collections import Counter
from pathlib import Path


SCRIPTS_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS_DIR))

from prepare_rag_eval_dataset import build_dataset  # noqa: E402


class PrepareRagEvalDatasetTest(unittest.TestCase):

    def test_builds_balanced_reproducible_dataset(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            hotpot_path = root / "hotpot.json"
            squad_path = root / "squad.json"
            self._write_json(hotpot_path, self._hotpot_rows())
            self._write_json(squad_path, self._squad_source())

            first = root / "first"
            second = root / "second"
            manifest = build_dataset(hotpot_path, squad_path, first, hotpot_count=4, squad_count=3, seed=7)
            build_dataset(hotpot_path, squad_path, second, hotpot_count=4, squad_count=3, seed=7)

            self.assertEqual(7, manifest["caseCount"])
            self.assertEqual((first / "cases.jsonl").read_bytes(), (second / "cases.jsonl").read_bytes())
            self.assertEqual((first / "corpus.jsonl").read_bytes(), (second / "corpus.jsonl").read_bytes())
            self.assertEqual((first / "manifest.json").read_bytes(), (second / "manifest.json").read_bytes())

            cases = self._read_jsonl(first / "cases.jsonl")
            hotpot_cases = [case for case in cases if case["dataset"] == "hotpotqa"]
            squad_cases = [case for case in cases if case["dataset"] == "squad2"]
            strata = Counter((case["questionType"], case["difficulty"]) for case in hotpot_cases)
            self.assertEqual(2, len(strata))
            self.assertTrue(all(count == 2 for count in strata.values()))
            self.assertTrue(all(case["answerable"] is False for case in squad_cases))
            self.assertTrue(all(case["referenceAnswers"] == [] for case in squad_cases))
            self.assertTrue(all(case["goldEvidence"] for case in hotpot_cases))

    def _hotpot_rows(self):
        rows = []
        for question_type in ("bridge", "comparison"):
            for level in ("easy", "medium", "hard"):
                for index in range(3):
                    row_id = f"{question_type}-{level}-{index}"
                    title = f"Title {row_id}"
                    rows.append(
                        {
                            "_id": row_id,
                            "answer": f"Answer {row_id}",
                            "question": f"Question {row_id}?",
                            "type": question_type,
                            "level": level,
                            "context": [
                                [title, ["Distractor sentence.", f"Supporting sentence for {row_id}."]],
                                [f"Distractor {row_id}", ["Unrelated content."]],
                            ],
                            "supporting_facts": [[title, 1]],
                        }
                    )
        return rows

    def _squad_source(self):
        articles = []
        for index in range(5):
            articles.append(
                {
                    "title": f"SQuAD Article {index}",
                    "paragraphs": [
                        {
                            "context": f"Context that does not answer impossible question {index}.",
                            "qas": [
                                {
                                    "id": f"squad-{index}",
                                    "question": f"Impossible question {index}?",
                                    "answers": [],
                                    "is_impossible": True,
                                },
                                {
                                    "id": f"answerable-{index}",
                                    "question": f"Answerable question {index}?",
                                    "answers": [{"text": "Context", "answer_start": 0}],
                                    "is_impossible": False,
                                },
                            ],
                        }
                    ],
                }
            )
        return {"version": "v2.0", "data": articles}

    def _write_json(self, path, value):
        path.write_text(json.dumps(value), encoding="utf-8")

    def _read_jsonl(self, path):
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]


if __name__ == "__main__":
    unittest.main()
