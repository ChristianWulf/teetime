package teetime.example;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import teetime.framework.Execution;
import teetime.framework.TeeTimeService;
import teetime.framework.scheduling.globaltaskpool.GlobalTaskPoolScheduling;

public class ThreeStagesGlobalTaskPoolConfigTest {

	@Test
	public void shouldExecuteWithOneThread() {
		shouldExecutePipelineCorrectlyManyElements(10_000, 1);
	}

	@Test
	public void shouldExecuteWithTwoThreads() {
		shouldExecutePipelineCorrectlyManyElements(10_000, 2);
	}

	@Test
	// @Ignore("assertion failed with 9999/10000 elements") // 18.08.17
	public void shouldExecuteWithFourThreads() {
		shouldExecutePipelineCorrectlyManyElements(10_000, 4);
	}

	private void shouldExecutePipelineCorrectlyManyElements(final int numElements, final int numThreads) {
		List<Integer> processedElements = new ArrayList<>();

		ThreeStagesGlobalTaskPoolConfig config = new ThreeStagesGlobalTaskPoolConfig(numElements, processedElements);
		TeeTimeService scheduling = new GlobalTaskPoolScheduling(numThreads, config, 1);
		Execution<ThreeStagesGlobalTaskPoolConfig> execution = new Execution<>(config, true, scheduling);
		execution.executeBlocking();

		for (int i = 0; i < numElements; i++) {
			assertThat(processedElements.get(i), is(i));
		}
		assertThat(processedElements, hasSize(numElements));
	}
}
