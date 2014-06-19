/***************************************************************************
 * Copyright 2014 Kieker Project (http://kieker-monitoring.net)
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
 ***************************************************************************/
package teetime.variant.methodcall.examples.experiment16;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import teetime.util.ConstructorClosure;
import teetime.variant.explicitScheduling.examples.throughput.TimestampObject;
import teetime.variant.explicitScheduling.framework.core.Analysis;
import teetime.variant.methodcall.framework.core.Pipeline;
import teetime.variant.methodcall.framework.core.RunnableStage;
import teetime.variant.methodcall.framework.core.StageWithPort;
import teetime.variant.methodcall.framework.core.pipe.SpScPipe;
import teetime.variant.methodcall.framework.core.pipe.UnorderedGrowablePipe;
import teetime.variant.methodcall.stage.CollectorSink;
import teetime.variant.methodcall.stage.Distributor;
import teetime.variant.methodcall.stage.NoopFilter;
import teetime.variant.methodcall.stage.ObjectProducer;
import teetime.variant.methodcall.stage.Relay;
import teetime.variant.methodcall.stage.StartTimestampFilter;
import teetime.variant.methodcall.stage.StopTimestampFilter;

/**
 * @author Christian Wulf
 * 
 * @since 1.10
 */
public class MethodCallThroughputAnalysis16 extends Analysis {

	private static final int NUM_WORKER_THREADS = Runtime.getRuntime().availableProcessors();

	private int numInputObjects;
	private ConstructorClosure<TimestampObject> inputObjectCreator;
	private int numNoopFilters;

	private final List<List<TimestampObject>> timestampObjectsList = new LinkedList<List<TimestampObject>>();

	private Distributor<TimestampObject> distributor;
	private Thread producerThread;

	private Thread[] workerThreads;

	private int numWorkerThreads;

	@Override
	public void init() {
		super.init();
		Pipeline<Void, TimestampObject> producerPipeline = this.buildProducerPipeline(this.numInputObjects, this.inputObjectCreator);
		this.producerThread = new Thread(new RunnableStage(producerPipeline));

		this.numWorkerThreads = Math.min(NUM_WORKER_THREADS, this.numWorkerThreads);

		this.workerThreads = new Thread[this.numWorkerThreads];
		for (int i = 0; i < this.workerThreads.length; i++) {
			List<TimestampObject> resultList = new ArrayList<TimestampObject>(this.numInputObjects);
			this.timestampObjectsList.add(resultList);

			Runnable workerRunnable = this.buildPipeline(producerPipeline, resultList);
			this.workerThreads[i] = new Thread(workerRunnable);
		}

		// this.producerThread.start();
		//
		// try {
		// this.producerThread.join();
		// } catch (InterruptedException e1) {
		// // TODO Auto-generated catch block
		// e1.printStackTrace();
		// }
	}

	private Pipeline<Void, TimestampObject> buildProducerPipeline(final int numInputObjects, final ConstructorClosure<TimestampObject> inputObjectCreator) {
		final ObjectProducer<TimestampObject> objectProducer = new ObjectProducer<TimestampObject>(numInputObjects, inputObjectCreator);
		this.distributor = new Distributor<TimestampObject>();

		final Pipeline<Void, TimestampObject> pipeline = new Pipeline<Void, TimestampObject>();
		pipeline.setFirstStage(objectProducer);
		pipeline.setLastStage(this.distributor);

		UnorderedGrowablePipe.connect(objectProducer.getOutputPort(), this.distributor.getInputPort());

		return pipeline;
	}

	/**
	 * @param numNoopFilters
	 * @since 1.10
	 */
	private Runnable buildPipeline(final StageWithPort<Void, TimestampObject> previousStage, final List<TimestampObject> timestampObjects) {
		Relay<TimestampObject> relay = new Relay<TimestampObject>();
		@SuppressWarnings("unchecked")
		final NoopFilter<TimestampObject>[] noopFilters = new NoopFilter[this.numNoopFilters];
		// create stages
		final StartTimestampFilter startTimestampFilter = new StartTimestampFilter();
		for (int i = 0; i < noopFilters.length; i++) {
			noopFilters[i] = new NoopFilter<TimestampObject>();
		}
		final StopTimestampFilter stopTimestampFilter = new StopTimestampFilter();
		final CollectorSink<TimestampObject> collectorSink = new CollectorSink<TimestampObject>(timestampObjects);

		final Pipeline<TimestampObject, Object> pipeline = new Pipeline<TimestampObject, Object>();
		pipeline.setFirstStage(relay);
		pipeline.addIntermediateStage(startTimestampFilter);
		pipeline.addIntermediateStages(noopFilters);
		pipeline.addIntermediateStage(stopTimestampFilter);
		pipeline.setLastStage(collectorSink);

		SpScPipe.connect(previousStage.getOutputPort(), relay.getInputPort());

		UnorderedGrowablePipe.connect(relay.getOutputPort(), startTimestampFilter.getInputPort());

		UnorderedGrowablePipe.connect(startTimestampFilter.getOutputPort(), noopFilters[0].getInputPort());
		for (int i = 0; i < noopFilters.length - 1; i++) {
			UnorderedGrowablePipe.connect(noopFilters[i].getOutputPort(), noopFilters[i + 1].getInputPort());
		}
		UnorderedGrowablePipe.connect(noopFilters[noopFilters.length - 1].getOutputPort(), stopTimestampFilter.getInputPort());
		UnorderedGrowablePipe.connect(stopTimestampFilter.getOutputPort(), collectorSink.getInputPort());

		return new RunnableStage(pipeline);
	}

	@Override
	public void start() {
		super.start();

		this.producerThread.start();

		for (Thread workerThread : this.workerThreads) {
			workerThread.start();
		}

		try {
			this.producerThread.join();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		try {
			for (Thread workerThread : this.workerThreads) {
				workerThread.join();
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void setInput(final int numInputObjects, final ConstructorClosure<TimestampObject> inputObjectCreator) {
		this.numInputObjects = numInputObjects;
		this.inputObjectCreator = inputObjectCreator;
	}

	public int getNumNoopFilters() {
		return this.numNoopFilters;
	}

	public void setNumNoopFilters(final int numNoopFilters) {
		this.numNoopFilters = numNoopFilters;
	}

	public List<List<TimestampObject>> getTimestampObjectsList() {
		return this.timestampObjectsList;
	}

	public int getNumWorkerThreads() {
		return this.numWorkerThreads;
	}

	public void setNumWorkerThreads(final int numWorkerThreads) {
		this.numWorkerThreads = numWorkerThreads;
	}

}