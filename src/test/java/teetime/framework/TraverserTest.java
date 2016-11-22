/**
 * Copyright © 2015 Christian Wulf, Nelson Tavares de Sousa (http://teetime-framework.github.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package teetime.framework;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import teetime.stage.Counter;
import teetime.stage.CountingMapMerger;
import teetime.stage.InitialElementProducer;
import teetime.stage.basic.Sink;
import teetime.stage.basic.distributor.Distributor;
import teetime.stage.basic.distributor.strategy.NonBlockingRoundRobinStrategy;
import teetime.stage.basic.merger.Merger;
import teetime.stage.io.File2SeqOfWords;
import teetime.stage.string.WordCounter;
import teetime.stage.util.CountingMap;

public class TraverserTest {

	@Test
	public void traverse() {
		TestConfiguration tc = new TestConfiguration();
		new Execution<TestConfiguration>(tc);

		Traverser traversor = new Traverser(new IntraStageCollector(tc.init));
		traversor.traverse(tc.init);

		Set<AbstractStage> comparingStages = new HashSet<AbstractStage>();
		comparingStages.add(tc.init);
		comparingStages.add(tc.f2b);
		comparingStages.add(tc.distributor);

		OutputPort<?> distributorOutputPort0 = tc.distributor.getOutputPorts().get(0);
		assertThat(tc.distributor.getOwningThread(), is(not(distributorOutputPort0.pipe.getTargetPort().getOwningStage().getOwningThread())));
		assertEquals(comparingStages, traversor.getVisitedStages());
	}

	// WordCounterConfiguration
	private static class TestConfiguration extends Configuration {

		public final InitialElementProducer<File> init;
		public final File2SeqOfWords f2b;
		public Distributor<String> distributor;

		public TestConfiguration() {
			int threads = 2;
			init = new InitialElementProducer<File>(new File(""));
			f2b = new File2SeqOfWords("UTF-8", 512);
			distributor = new Distributor<String>(new NonBlockingRoundRobinStrategy());
			CountingMapMerger<String> result = new CountingMapMerger<String>();

			// last part
			final Merger<CountingMap<String>> merger = new Merger<CountingMap<String>>();
			// CountingMapMerger (already as field)

			// Connecting the stages of the first part of the config
			connectPorts(init.getOutputPort(), f2b.getInputPort());
			connectPorts(f2b.getOutputPort(), distributor.getInputPort());

			// Middle part... multiple instances of WordCounter are created and connected to the merger and distrubuter stages
			for (int i = 0; i < threads; i++) {
				// final InputPortSizePrinter<String> inputPortSizePrinter = new InputPortSizePrinter<String>();
				final WordCounter wc = new WordCounter();
				// intraFact.create(inputPortSizePrinter.getOutputPort(), wc.getInputPort());

				connectPorts(distributor.getNewOutputPort(), wc.getInputPort());
				connectPorts(wc.getOutputPort(), merger.getNewInputPort());
				// Add WordCounter as a threadable stage, so it runs in its own thread
				wc.getInputPort().getOwningStage().declareActive();
			}

			// Connect the stages of the last part
			connectPorts(merger.getOutputPort(), result.getInputPort());

			// Add the first and last part to the threadable stages
			merger.declareActive();
		}

	}

	@Test(expected = IllegalStateException.class)
	public void unconnectedInputPortShouldThrowException() throws Exception {
		UnconnectedInputPortConfig config = new UnconnectedInputPortConfig();
		new Execution<Configuration>(config);
	}

	private static class UnconnectedInputPortConfig extends Configuration {
		public UnconnectedInputPortConfig() {
			Counter<Object> counter = new Counter<Object>();
			Sink<Object> sink = new Sink<Object>();
			connectPorts(counter.getOutputPort(), sink.getInputPort());
		}
	}

}
