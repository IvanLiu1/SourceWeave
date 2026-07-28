package com.ivanliu.ragproject.evaluation;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ivanliu.ragproject.client.EmbeddingClient;
import com.ivanliu.ragproject.client.RerankClient;
import com.ivanliu.ragproject.service.ModelProviderConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RagEvaluationExecutor {

    private static final Logger logger = LoggerFactory.getLogger(RagEvaluationExecutor.class);
    private static final String SAFE_INDEX_PREFIX = "sourceweave_eval_";
    private static final String DATASET_VERSION_FALLBACK = "rag-eval-en-v1";

    private final RagEvaluationProperties properties;
    private final RagEvaluationAnswerClient answerClient;
    private final EmbeddingClient embeddingClient;
    private final RerankClient rerankClient;
    private final ModelProviderConfigService modelProviderConfigService;
    private final ElasticsearchClient elasticsearchClient;
    private final ObjectMapper objectMapper;

    public RagEvaluationExecutor(RagEvaluationProperties properties,
                                 RagEvaluationAnswerClient answerClient,
                                 EmbeddingClient embeddingClient,
                                 RerankClient rerankClient,
                                 ModelProviderConfigService modelProviderConfigService,
                                 ElasticsearchClient elasticsearchClient,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.answerClient = answerClient;
        this.embeddingClient = embeddingClient;
        this.rerankClient = rerankClient;
        this.modelProviderConfigService = modelProviderConfigService;
        this.elasticsearchClient = elasticsearchClient;
        this.objectMapper = objectMapper;
    }

    public ExecutionSummary execute() throws Exception {
        validateConfiguration();
        Mode mode = Mode.parse(properties.getMode());
        Path datasetDir = properties.getDatasetDir().toAbsolutePath().normalize();
        Path casesPath = datasetDir.resolve("cases.jsonl");
        Path corpusPath = datasetDir.resolve("corpus.jsonl");
        Path manifestPath = datasetDir.resolve("manifest.json");
        requireReadable(casesPath);
        requireReadable(corpusPath);
        requireReadable(manifestPath);

        List<EvaluationPassage> corpus = readJsonLines(corpusPath, EvaluationPassage.class);
        List<EvaluationCase> cases = readJsonLines(casesPath, EvaluationCase.class);
        JsonNode manifest = objectMapper.readTree(manifestPath.toFile());
        validateDataset(corpus, cases, manifest);

        String embeddingModel = embeddingClient.currentModelVersion();
        String corpusSha256 = sha256(corpusPath);
        String indexFingerprint = sha256(corpusSha256 + "\n" + embeddingModel + "\nstandard-analyzer-v1");

        logger.info("RAG evaluation started: mode={}, datasetDir={}, cases={}, passages={}, index={}",
                mode, datasetDir, cases.size(), corpus.size(), properties.getIndexName());

        String datasetVersion = manifest.path("version").asText(DATASET_VERSION_FALLBACK);
        if (mode.preparesIndex()) {
            prepareIndex(corpus, embeddingModel, indexFingerprint, datasetVersion);
        } else {
            requireCompatibleIndex(corpus.size(), indexFingerprint);
        }

        int predictionCount = 0;
        if (mode.generatesPredictions()) {
            predictionCount = generatePredictions(cases, corpusSha256, indexFingerprint, manifest);
        }

        logger.info("RAG evaluation finished: mode={}, predictions={}", mode, predictionCount);
        return new ExecutionSummary(mode.name().toLowerCase(Locale.ROOT), corpus.size(), cases.size(), predictionCount);
    }

    private void prepareIndex(List<EvaluationPassage> corpus,
                              String embeddingModel,
                              String indexFingerprint,
                              String datasetVersion) throws Exception {
        boolean exists = indexExists();
        if (exists && properties.isResetIndex()) {
            validateSafeIndexName(properties.getIndexName());
            logger.warn("Deleting isolated evaluation index before rebuild: {}", properties.getIndexName());
            elasticsearchClient.indices().delete(request -> request.index(properties.getIndexName()));
            exists = false;
        }
        if (exists) {
            requireCompatibleIndex(corpus.size(), indexFingerprint);
            logger.info("Reusing compatible evaluation index: {}", properties.getIndexName());
            return;
        }

        createIndex(embeddingDimension(), embeddingModel, indexFingerprint, datasetVersion);
        int batchSize = Math.max(properties.getIndexBatchSize(), 1);
        for (int start = 0; start < corpus.size(); start += batchSize) {
            int end = Math.min(start + batchSize, corpus.size());
            List<EvaluationPassage> batch = corpus.subList(start, end);
            List<String> texts = batch.stream().map(EvaluationPassage::text).toList();
            EmbeddingClient.EmbeddingUsageResult embeddingResult = embeddingClient.embedWithUsage(
                    texts,
                    properties.getRequesterId(),
                    EmbeddingClient.UsageType.UPLOAD
            );
            if (embeddingResult.vectors().size() != batch.size()) {
                throw new IllegalStateException("Embedding count does not match evaluation passage count");
            }
            List<EvaluationEsDocument> documents = new ArrayList<>(batch.size());
            for (int index = 0; index < batch.size(); index++) {
                EvaluationPassage passage = batch.get(index);
                documents.add(new EvaluationEsDocument(
                        passage.passageId(),
                        passage.dataset(),
                        passage.title(),
                        passage.text(),
                        embeddingResult.vectors().get(index),
                        embeddingResult.modelVersion(),
                        indexFingerprint
                ));
            }
            bulkIndex(documents);
            logger.info("Indexed evaluation passages: {}/{}", end, corpus.size());
        }
        elasticsearchClient.indices().refresh(request -> request.index(properties.getIndexName()));
        requireCompatibleIndex(corpus.size(), indexFingerprint);
    }

    private int generatePredictions(List<EvaluationCase> allCases,
                                    String corpusSha256,
                                    String indexFingerprint,
                                    JsonNode manifest) throws Exception {
        if (!rerankClient.isEnabled()) {
            throw new IllegalStateException("Rerank must be enabled and configured for a baseline/rerank evaluation run");
        }

        List<EvaluationCase> cases = selectCases(allCases, properties.getCaseIds(), properties.getMaxCases());
        List<String> questions = cases.stream().map(EvaluationCase::question).toList();
        EmbeddingClient.EmbeddingUsageResult queryEmbeddings = embeddingClient.embedWithUsage(
                questions,
                properties.getRequesterId(),
                EmbeddingClient.UsageType.QUERY
        );
        if (queryEmbeddings.vectors().size() != cases.size()) {
            throw new IllegalStateException("Embedding count does not match evaluation question count");
        }

        Path outputDir = properties.getOutputDir().toAbsolutePath().normalize();
        Files.createDirectories(outputDir);
        Path predictionsPath = outputDir.resolve("predictions.jsonl");
        Path partialPath = outputDir.resolve("predictions.jsonl.partial");
        Path metadataPath = outputDir.resolve("run-metadata.json");
        ensureOutputCanBeWritten(predictionsPath, partialPath, metadataPath);

        Instant startedAt = Instant.now();
        int predictionCount = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(partialPath, StandardCharsets.UTF_8)) {
            for (int caseIndex = 0; caseIndex < cases.size(); caseIndex++) {
                EvaluationCase evaluationCase = cases.get(caseIndex);
                float[] queryVector = queryEmbeddings.vectors().get(caseIndex);

                long retrievalStartedAt = System.nanoTime();
                List<RetrievedPassage> candidates = retrieveCandidates(evaluationCase.question(), queryVector);
                long retrievalLatencyMs = elapsedMillis(retrievalStartedAt);

                List<RetrievedPassage> baselineTopK = List.copyOf(candidates.subList(0, properties.getTopK()));
                long rerankStartedAt = System.nanoTime();
                List<RerankClient.RerankResult> ranked = rerankClient.rerank(
                        evaluationCase.question(),
                        candidates.stream().map(RetrievedPassage::text).toList(),
                        properties.getTopK()
                );
                long rerankLatencyMs = elapsedMillis(rerankStartedAt);
                boolean rerankFallback = !hasCompleteRerank(ranked, candidates.size(), properties.getTopK());
                List<RetrievedPassage> rerankTopK = selectReranked(candidates, ranked, properties.getTopK());
                List<RerankScoreSnapshot> rerankScores = snapshotRerankScores(
                        candidates,
                        ranked,
                        properties.getTopK()
                );

                GeneratedAnswer baselineAnswer = generateAnswer(evaluationCase, baselineTopK);
                GeneratedAnswer rerankAnswer = generateAnswer(evaluationCase, rerankTopK);
                List<CandidateSnapshot> candidateSnapshot = candidates.stream()
                        .map(candidate -> new CandidateSnapshot(candidate.passageId(), candidate.esScore()))
                        .toList();

                Prediction baselinePrediction = buildPrediction(
                        evaluationCase,
                        "baseline",
                        candidateSnapshot,
                        baselineTopK,
                        List.of(),
                        baselineAnswer,
                        retrievalLatencyMs,
                        0,
                        false
                );
                Prediction rerankPrediction = buildPrediction(
                        evaluationCase,
                        "rerank",
                        candidateSnapshot,
                        rerankTopK,
                        rerankScores,
                        rerankAnswer,
                        retrievalLatencyMs,
                        rerankLatencyMs,
                        rerankFallback
                );
                writeJsonLine(writer, baselinePrediction);
                writeJsonLine(writer, rerankPrediction);
                writer.flush();
                predictionCount += 2;
                logger.info("Evaluated case {}/{}: caseId={}, rerankFallback={}",
                        caseIndex + 1, cases.size(), evaluationCase.caseId(), rerankFallback);
            }
        } catch (Exception exception) {
            logger.error("Evaluation stopped; completed pairs remain in {}", partialPath, exception);
            throw exception;
        }

        moveCompletedOutput(partialPath, predictionsPath);
        Map<String, Object> metadata = buildRunMetadata(
                startedAt,
                Instant.now(),
                manifest,
                corpusSha256,
                sha256(properties.getDatasetDir().toAbsolutePath().normalize().resolve("cases.jsonl")),
                indexFingerprint,
                cases.size(),
                predictionCount,
                queryEmbeddings.modelVersion()
        );
        writePrettyJson(metadataPath, metadata);
        return predictionCount;
    }

    private GeneratedAnswer generateAnswer(EvaluationCase evaluationCase,
                                           List<RetrievedPassage> passages) {
        if (!properties.isGenerateAnswers()) {
            return GeneratedAnswer.notGenerated();
        }
        long startedAt = System.nanoTime();
        RagEvaluationAnswerClient.AnswerResult result = answerClient.answer(
                properties.getRequesterId(),
                evaluationCase.question(),
                passages,
                properties.getAnswerMaxTokens(),
                properties.getAnswerTimeoutSeconds()
        );
        return new GeneratedAnswer(
                result.answer(),
                result.citedPassageIds(),
                elapsedMillis(startedAt),
                result.parseError(),
                result.supported(),
                result.supportReason(),
                result.promptTokens(),
                result.completionTokens(),
                result.modelVersion(),
                true
        );
    }

    private Prediction buildPrediction(EvaluationCase evaluationCase,
                                       String variant,
                                       List<CandidateSnapshot> candidates,
                                       List<RetrievedPassage> selected,
                                       List<RerankScoreSnapshot> rerankScores,
                                       GeneratedAnswer answer,
                                       long retrievalLatencyMs,
                                       long rerankLatencyMs,
                                       boolean rerankFallback) {
        return new Prediction(
                evaluationCase.caseId(),
                variant,
                candidates,
                selected.stream().map(RetrievedPassage::passageId).toList(),
                rerankScores,
                answer.answer(),
                answer.citedPassageIds(),
                retrievalLatencyMs + rerankLatencyMs + answer.generationLatencyMs(),
                retrievalLatencyMs,
                rerankLatencyMs,
                answer.generationLatencyMs(),
                rerankFallback,
                answer.generated(),
                answer.parseError(),
                answer.supported(),
                answer.supportReason(),
                answer.promptTokens(),
                answer.completionTokens(),
                answer.modelVersion()
        );
    }

    private List<RetrievedPassage> retrieveCandidates(String question, float[] queryVector) throws IOException {
        List<Float> vector = new ArrayList<>(queryVector.length);
        for (float value : queryVector) {
            vector.add(value);
        }
        int recallWindow = properties.getCandidateSize() * 3;
        SearchResponse<EvaluationEsDocument> response = elasticsearchClient.search(search -> {
            search.index(properties.getIndexName());
            search.knn(knn -> knn
                    .field("vector")
                    .queryVector(vector)
                    .k(recallWindow)
                    .numCandidates(recallWindow * 2)
            );
            search.query(query -> query.match(match -> match.field("text").query(question)));
            search.rescore(rescore -> rescore
                    .windowSize(recallWindow)
                    .query(rescoreQuery -> rescoreQuery
                            .queryWeight(0.2d)
                            .rescoreQueryWeight(1.0d)
                            .query(inner -> inner.match(match -> match
                                    .field("text")
                                    .query(question)
                                    .operator(Operator.And)
                            ))
                    )
            );
            search.size(properties.getCandidateSize());
            return search;
        }, EvaluationEsDocument.class);

        List<RetrievedPassage> candidates = response.hits().hits().stream()
                .map(hit -> {
                    EvaluationEsDocument source = hit.source();
                    if (source == null) {
                        throw new IllegalStateException("Evaluation search hit has no source");
                    }
                    return new RetrievedPassage(
                            source.passageId(),
                            source.title(),
                            source.text(),
                            hit.score()
                    );
                })
                .toList();
        if (candidates.size() != properties.getCandidateSize()) {
            throw new IllegalStateException("Expected exactly " + properties.getCandidateSize()
                    + " Elasticsearch candidates but got " + candidates.size() + " for question: " + question);
        }
        if (new HashSet<>(candidates.stream().map(RetrievedPassage::passageId).toList()).size() != candidates.size()) {
            throw new IllegalStateException("Elasticsearch returned duplicate evaluation candidate IDs");
        }
        return candidates;
    }

    static List<RetrievedPassage> selectReranked(List<RetrievedPassage> candidates,
                                                 List<RerankClient.RerankResult> ranked,
                                                 int topK) {
        if (!hasCompleteRerank(ranked, candidates.size(), topK)) {
            return List.copyOf(candidates.subList(0, Math.min(topK, candidates.size())));
        }
        LinkedHashSet<Integer> selectedIndices = new LinkedHashSet<>();
        for (RerankClient.RerankResult result : ranked) {
            if (result.index() >= 0 && result.index() < candidates.size()) {
                selectedIndices.add(result.index());
            }
            if (selectedIndices.size() >= topK) {
                break;
            }
        }
        return selectedIndices.stream().map(candidates::get).toList();
    }

    static List<RerankScoreSnapshot> snapshotRerankScores(List<RetrievedPassage> candidates,
                                                          List<RerankClient.RerankResult> ranked,
                                                          int topK) {
        if (!hasCompleteRerank(ranked, candidates.size(), topK)) {
            return List.of();
        }
        LinkedHashSet<Integer> selectedIndices = new LinkedHashSet<>();
        List<RerankScoreSnapshot> snapshots = new ArrayList<>();
        for (RerankClient.RerankResult result : ranked) {
            if (result.index() >= 0
                    && result.index() < candidates.size()
                    && selectedIndices.add(result.index())) {
                snapshots.add(new RerankScoreSnapshot(
                        candidates.get(result.index()).passageId(),
                        result.score()
                ));
            }
            if (snapshots.size() >= Math.min(topK, candidates.size())) {
                break;
            }
        }
        return List.copyOf(snapshots);
    }

    static boolean hasCompleteRerank(List<RerankClient.RerankResult> ranked,
                                     int candidateCount,
                                     int topK) {
        if (ranked == null || ranked.isEmpty()) {
            return false;
        }
        Set<Integer> validIndices = new HashSet<>();
        for (RerankClient.RerankResult result : ranked) {
            if (result.index() >= 0 && result.index() < candidateCount) {
                validIndices.add(result.index());
            }
        }
        return validIndices.size() >= Math.min(topK, candidateCount);
    }

    private void createIndex(int dimension,
                             String embeddingModel,
                             String indexFingerprint,
                             String datasetVersion) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode mappings = root.putObject("mappings");
        ObjectNode metadata = mappings.putObject("_meta");
        metadata.put("dataset", datasetVersion);
        metadata.put("embeddingModel", embeddingModel);
        metadata.put("indexFingerprint", indexFingerprint);
        ObjectNode fields = mappings.putObject("properties");
        fields.putObject("passageId").put("type", "keyword");
        fields.putObject("dataset").put("type", "keyword");
        fields.putObject("title").put("type", "text").put("analyzer", "standard");
        fields.putObject("text").put("type", "text").put("analyzer", "standard");
        fields.putObject("modelVersion").put("type", "keyword");
        fields.putObject("indexFingerprint").put("type", "keyword");
        fields.putObject("vector")
                .put("type", "dense_vector")
                .put("dims", dimension)
                .put("index", true)
                .put("similarity", "cosine");

        String mappingJson = objectMapper.writeValueAsString(root);
        CreateIndexRequest request = CreateIndexRequest.of(builder -> builder
                .index(properties.getIndexName())
                .withJson(new StringReader(mappingJson))
        );
        elasticsearchClient.indices().create(request);
        logger.info("Created isolated evaluation index: {}, dimension={}", properties.getIndexName(), dimension);
    }

    private void bulkIndex(List<EvaluationEsDocument> documents) throws IOException {
        List<BulkOperation> operations = documents.stream()
                .map(document -> BulkOperation.of(operation -> operation.index(index -> index
                        .index(properties.getIndexName())
                        .id(document.passageId())
                        .document(document)
                )))
                .toList();
        BulkResponse response = elasticsearchClient.bulk(BulkRequest.of(builder -> builder.operations(operations)));
        if (!response.errors()) {
            return;
        }
        String firstError = response.items().stream()
                .map(BulkResponseItem::error)
                .filter(error -> error != null)
                .map(error -> error.reason())
                .findFirst()
                .orElse("unknown bulk indexing error");
        throw new IllegalStateException("Evaluation bulk indexing failed: " + firstError);
    }

    private boolean indexExists() throws IOException {
        BooleanResponse response = elasticsearchClient.indices()
                .exists(request -> request.index(properties.getIndexName()));
        return response.value();
    }

    private void requireCompatibleIndex(int expectedCount, String indexFingerprint) throws IOException {
        if (!indexExists()) {
            throw new IllegalStateException("Evaluation index does not exist; run with mode=prepare or mode=all first");
        }
        long total = elasticsearchClient.count(request -> request.index(properties.getIndexName())).count();
        long matchingFingerprint = elasticsearchClient.count(request -> request
                .index(properties.getIndexName())
                .query(query -> query.term(term -> term
                        .field("indexFingerprint")
                        .value(indexFingerprint)
                ))
        ).count();
        if (total != expectedCount || matchingFingerprint != expectedCount) {
            throw new IllegalStateException("Evaluation index is incompatible: expected " + expectedCount
                    + " documents with fingerprint " + indexFingerprint + " but found total=" + total
                    + ", matching=" + matchingFingerprint + ". Re-run with rag.evaluation.reset-index=true");
        }
    }

    private int embeddingDimension() {
        Integer dimension = modelProviderConfigService
                .getActiveProvider(ModelProviderConfigService.SCOPE_EMBEDDING)
                .dimension();
        if (dimension == null || dimension <= 0) {
            throw new IllegalStateException("Active embedding provider has no valid dimension");
        }
        return dimension;
    }

    static List<EvaluationCase> selectCases(List<EvaluationCase> cases,
                                            List<String> requestedCaseIds,
                                            int maxCases) {
        if (requestedCaseIds != null && !requestedCaseIds.isEmpty()) {
            LinkedHashSet<String> requested = new LinkedHashSet<>();
            for (String caseId : requestedCaseIds) {
                if (caseId == null || caseId.isBlank()) {
                    throw new IllegalArgumentException("rag.evaluation.case-ids cannot contain blank IDs");
                }
                if (!requested.add(caseId)) {
                    throw new IllegalArgumentException("rag.evaluation.case-ids contains duplicate ID: " + caseId);
                }
            }
            if (maxCases > 0) {
                throw new IllegalArgumentException(
                        "rag.evaluation.case-ids cannot be combined with rag.evaluation.max-cases");
            }
            Map<String, EvaluationCase> casesById = new LinkedHashMap<>();
            for (EvaluationCase evaluationCase : cases) {
                casesById.put(evaluationCase.caseId(), evaluationCase);
            }
            List<String> missing = requested.stream()
                    .filter(caseId -> !casesById.containsKey(caseId))
                    .toList();
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Unknown rag.evaluation.case-ids: " + missing);
            }
            return requested.stream().map(casesById::get).toList();
        }
        if (maxCases <= 0 || maxCases >= cases.size()) {
            return cases;
        }
        return List.copyOf(cases.subList(0, maxCases));
    }

    private void validateConfiguration() {
        validateSafeIndexName(properties.getIndexName());
        if (properties.getCandidateSize() != 50) {
            throw new IllegalArgumentException("rag.evaluation.candidate-size must be 50 for v1");
        }
        if (properties.getTopK() != 5) {
            throw new IllegalArgumentException("rag.evaluation.top-k must be 5 for v1");
        }
        if (properties.getMaxCases() < 0) {
            throw new IllegalArgumentException("rag.evaluation.max-cases cannot be negative");
        }
        if (properties.getCaseIds() != null
                && !properties.getCaseIds().isEmpty()
                && properties.getMaxCases() > 0) {
            throw new IllegalArgumentException(
                    "rag.evaluation.case-ids cannot be combined with rag.evaluation.max-cases");
        }
    }

    static void validateSafeIndexName(String indexName) {
        if (indexName == null
                || !indexName.startsWith(SAFE_INDEX_PREFIX)
                || !indexName.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Evaluation index must start with " + SAFE_INDEX_PREFIX
                    + " and contain only lowercase letters, digits, '_' or '-'");
        }
    }

    private void validateDataset(List<EvaluationPassage> corpus,
                                 List<EvaluationCase> cases,
                                 JsonNode manifest) {
        int expectedCases = manifest.path("caseCount").asInt(-1);
        int expectedPassages = manifest.path("corpusPassageCount").asInt(-1);
        if (expectedCases != cases.size() || expectedPassages != corpus.size()) {
            throw new IllegalStateException("Dataset files do not match manifest counts");
        }
        Set<String> passageIds = new HashSet<>();
        for (EvaluationPassage passage : corpus) {
            if (passage.passageId() == null || passage.passageId().isBlank()
                    || passage.text() == null || passage.text().isBlank()
                    || !passageIds.add(passage.passageId())) {
                throw new IllegalStateException("Evaluation corpus contains invalid or duplicate passages");
            }
        }
        Set<String> caseIds = new HashSet<>();
        for (EvaluationCase evaluationCase : cases) {
            if (evaluationCase.caseId() == null || evaluationCase.caseId().isBlank()
                    || evaluationCase.question() == null || evaluationCase.question().isBlank()
                    || !caseIds.add(evaluationCase.caseId())) {
                throw new IllegalStateException("Evaluation cases contain invalid or duplicate rows");
            }
        }
    }

    private void ensureOutputCanBeWritten(Path predictionsPath,
                                          Path partialPath,
                                          Path metadataPath) throws IOException {
        if (properties.isOverwriteOutput()) {
            return;
        }
        if (Files.exists(predictionsPath) || Files.exists(partialPath) || Files.exists(metadataPath)) {
            throw new IllegalStateException("Evaluation output already exists in " + predictionsPath.getParent()
                    + "; choose another output directory or set rag.evaluation.overwrite-output=true");
        }
    }

    private void moveCompletedOutput(Path partialPath, Path predictionsPath) throws IOException {
        try {
            if (properties.isOverwriteOutput()) {
                Files.move(partialPath, predictionsPath,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(partialPath, predictionsPath, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException ignored) {
            if (properties.isOverwriteOutput()) {
                Files.move(partialPath, predictionsPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(partialPath, predictionsPath);
            }
        }
    }

    private Map<String, Object> buildRunMetadata(Instant startedAt,
                                                 Instant completedAt,
                                                 JsonNode manifest,
                                                 String corpusSha256,
                                                 String casesSha256,
                                                 String indexFingerprint,
                                                 int caseCount,
                                                 int predictionCount,
                                                 String embeddingModel) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", "complete");
        metadata.put("datasetVersion", manifest.path("version").asText(DATASET_VERSION_FALLBACK));
        metadata.put("startedAt", startedAt.toString());
        metadata.put("completedAt", completedAt.toString());
        metadata.put("caseCount", caseCount);
        metadata.put("predictionCount", predictionCount);
        metadata.put("corpusSha256", corpusSha256);
        metadata.put("casesSha256", casesSha256);
        metadata.put("indexName", properties.getIndexName());
        metadata.put("indexFingerprint", indexFingerprint);
        metadata.put("textAnalyzer", "standard");
        metadata.put("candidateSize", properties.getCandidateSize());
        metadata.put("topK", properties.getTopK());
        metadata.put("embeddingModel", embeddingModel);
        metadata.put("rerankModel", rerankClient.currentModelVersion());
        metadata.put("answerModel", properties.isGenerateAnswers() ? answerClient.currentModelVersion() : "disabled");
        metadata.put("answerPromptVersion", RagEvaluationAnswerClient.PROMPT_VERSION);
        metadata.put("generateAnswers", properties.isGenerateAnswers());
        metadata.put("gitCommit", properties.getGitCommit());
        metadata.put("retrievalDefinition", "KNN(k=150) + match query + BM25 rescore");
        return metadata;
    }

    private <T> List<T> readJsonLines(Path path, Class<T> type) throws IOException {
        List<T> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    rows.add(objectMapper.readValue(line, type));
                }
            }
        }
        return List.copyOf(rows);
    }

    private void writeJsonLine(BufferedWriter writer, Object value) throws IOException {
        writer.write(objectMapper.writeValueAsString(value));
        writer.newLine();
    }

    private void writePrettyJson(Path path, Object value) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private void requireReadable(Path path) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("Evaluation input is not readable: " + path);
        }
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0, Math.round((System.nanoTime() - startedAtNanos) / 1_000_000d));
    }

    enum Mode {
        PREPARE,
        RUN,
        ALL;

        static Mode parse(String raw) {
            try {
                return valueOf(raw == null ? "ALL" : raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("rag.evaluation.mode must be prepare, run, or all", exception);
            }
        }

        boolean preparesIndex() {
            return this == PREPARE || this == ALL;
        }

        boolean generatesPredictions() {
            return this == RUN || this == ALL;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvaluationPassage(String passageId,
                                    String dataset,
                                    String title,
                                    String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvaluationCase(String caseId,
                                 String dataset,
                                 String task,
                                 String questionType,
                                 String question) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EvaluationEsDocument(String passageId,
                                String dataset,
                                String title,
                                String text,
                                float[] vector,
                                String modelVersion,
                                String indexFingerprint) {
    }

    public record RetrievedPassage(String passageId,
                                   String title,
                                   String text,
                                   double esScore) {
    }

    public record CandidateSnapshot(String passageId, double esScore) {
    }

    public record RerankScoreSnapshot(String passageId, double score) {
    }

    record GeneratedAnswer(String answer,
                           List<String> citedPassageIds,
                           long generationLatencyMs,
                           boolean parseError,
                           boolean supported,
                           String supportReason,
                           int promptTokens,
                           int completionTokens,
                           String modelVersion,
                           boolean generated) {
        static GeneratedAnswer notGenerated() {
            return new GeneratedAnswer("", List.of(), 0, false, false, "", 0, 0, "disabled", false);
        }
    }

    public record Prediction(String caseId,
                             String variant,
                             List<CandidateSnapshot> candidates,
                             List<String> retrievedPassageIds,
                             List<RerankScoreSnapshot> rerankScores,
                             String answer,
                             List<String> citedPassageIds,
                             long latencyMs,
                             long retrievalLatencyMs,
                             long rerankLatencyMs,
                             long generationLatencyMs,
                             boolean rerankFallback,
                             boolean answerGenerated,
                             boolean generationParseError,
                             boolean answerSupported,
                             String supportReason,
                             int promptTokens,
                             int completionTokens,
                             String answerModel) {
    }

    public record ExecutionSummary(String mode,
                                   int corpusPassageCount,
                                   int caseCount,
                                   int predictionCount) {
    }
}
