package com.ivanliu.ragproject.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rag.evaluation.enabled", havingValue = "true")
public class RagEvaluationCommand {

    private static final Logger logger = LoggerFactory.getLogger(RagEvaluationCommand.class);
    private final RagEvaluationExecutor executor;
    private final RagEvaluationProperties properties;
    private final ConfigurableApplicationContext applicationContext;

    public RagEvaluationCommand(RagEvaluationExecutor executor,
                                RagEvaluationProperties properties,
                                ConfigurableApplicationContext applicationContext) {
        this.executor = executor;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() throws Exception {
        RagEvaluationExecutor.ExecutionSummary summary = executor.execute();
        logger.info("Evaluation command completed: {}", summary);
        if (properties.isExitOnCompletion()) {
            logger.info("Closing the evaluation application context after command completion");
            applicationContext.close();
        }
    }
}
