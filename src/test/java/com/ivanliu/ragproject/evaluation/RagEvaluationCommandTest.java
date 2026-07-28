package com.ivanliu.ragproject.evaluation;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagEvaluationCommandTest {

    @Test
    void executesOnceAndClosesCliContext() throws Exception {
        RagEvaluationExecutor executor = mock(RagEvaluationExecutor.class);
        RagEvaluationProperties properties = new RagEvaluationProperties();
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(executor.execute()).thenReturn(new RagEvaluationExecutor.ExecutionSummary("run", 1226, 150, 300));

        new RagEvaluationCommand(executor, properties, context).run();

        verify(executor).execute();
        verify(context).close();
    }
}
