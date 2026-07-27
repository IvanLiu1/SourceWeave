package com.ivanliu.ragproject.evaluation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Data
@Component
@ConfigurationProperties(prefix = "rag.evaluation")
public class RagEvaluationProperties {

    /** Explicit safety switch. The evaluator never runs during a normal application start. */
    private boolean enabled = false;

    /** prepare: build the isolated index; run: generate predictions; all: do both. */
    private String mode = "all";

    private Path datasetDir = Path.of("evaluation/datasets/rag-eval-en-v1");
    private Path outputDir = Path.of("evaluation/runs/rag-eval-en-v1");
    private String indexName = "sourceweave_eval_rag_en_v1";
    private boolean resetIndex = false;
    private boolean overwriteOutput = false;
    private boolean generateAnswers = true;
    private boolean exitOnCompletion = true;

    /** Zero means all cases; a positive value is useful for a cheap smoke run. */
    private int maxCases = 0;

    private int candidateSize = 50;
    private int topK = 5;
    private int indexBatchSize = 50;
    private int answerMaxTokens = 200;
    private int answerTimeoutSeconds = 90;
    private String requesterId = "system-evaluation";
    private String gitCommit = "unknown";
}
