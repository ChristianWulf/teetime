/**
 * Copyright (C) 2015 Christian Wulf, Nelson Tavares de Sousa (http://teetime.sourceforge.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package teetime.framework;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import teetime.stage.Counter;
import teetime.stage.InitialElementProducer;
import teetime.stage.basic.Sink;

public class AbstractCompositeStageTest {

	@Test
	public void testNestedStages() {
		Execution<NestesConfig> exec = new Execution<NestesConfig>(new NestesConfig());
		assertThat(exec.getConfiguration().getContext().getThreadableStages().size(), is(3));
	}

	private class NestesConfig extends Configuration {

		private final InitialElementProducer<Object> init;
		private final Sink sink;
		private final TestNestingCompositeStage compositeStage;

		public NestesConfig() {
			init = new InitialElementProducer<Object>(new Object());
			sink = new Sink();
			compositeStage = new TestNestingCompositeStage();
			connectPorts(init.getOutputPort(), compositeStage.firstCompositeStage.firstCounter.getInputPort());
			connectPorts(compositeStage.secondCompositeStage.secondCounter.getOutputPort(), sink.getInputPort());

		}
	}

	private class TestCompositeStage extends AbstractCompositeStage {

		private final Counter firstCounter = new Counter();
		private final Counter secondCounter = new Counter();

		public TestCompositeStage() {
			addThreadableStage(firstCounter);
			connectPorts(firstCounter.getOutputPort(), secondCounter.getInputPort());
		}

	}

	private class TestNestingCompositeStage extends AbstractCompositeStage {

		public TestCompositeStage firstCompositeStage;
		public TestCompositeStage secondCompositeStage;

		public TestNestingCompositeStage() {
			firstCompositeStage = new TestCompositeStage();
			secondCompositeStage = new TestCompositeStage();
			connectPorts(firstCompositeStage.secondCounter.getOutputPort(), secondCompositeStage.firstCounter.getInputPort());
		}

	}

}
