package teetime.variant.methodcallWithPorts.examples.traceReconstructionWithThreads;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import teetime.util.concurrent.hashmap.ConcurrentHashMapWithDefault;
import teetime.util.concurrent.hashmap.TraceBuffer;
import teetime.variant.methodcallWithPorts.framework.core.Configuration;
import teetime.variant.methodcallWithPorts.framework.core.Pipeline;
import teetime.variant.methodcallWithPorts.framework.core.StageWithPort;
import teetime.variant.methodcallWithPorts.framework.core.pipe.SingleElementPipe;
import teetime.variant.methodcallWithPorts.framework.core.pipe.SpScPipe;
import teetime.variant.methodcallWithPorts.stage.Clock;
import teetime.variant.methodcallWithPorts.stage.Counter;
import teetime.variant.methodcallWithPorts.stage.ElementDelayMeasuringStage;
import teetime.variant.methodcallWithPorts.stage.ElementThroughputMeasuringStage;
import teetime.variant.methodcallWithPorts.stage.InstanceCounter;
import teetime.variant.methodcallWithPorts.stage.InstanceOfFilter;
import teetime.variant.methodcallWithPorts.stage.Relay;
import teetime.variant.methodcallWithPorts.stage.basic.Sink;
import teetime.variant.methodcallWithPorts.stage.basic.distributor.Distributor;
import teetime.variant.methodcallWithPorts.stage.io.TCPReader;
import teetime.variant.methodcallWithPorts.stage.kieker.traceReconstruction.TraceReconstructionFilter;

import kieker.analysis.plugin.filter.flow.TraceEventRecords;
import kieker.common.record.IMonitoringRecord;
import kieker.common.record.flow.IFlowRecord;
import kieker.common.record.flow.trace.TraceMetadata;

public class TcpTraceReconstructionAnalysisWithThreadsConfiguration extends Configuration {

	private static final int NUM_VIRTUAL_CORES = Runtime.getRuntime().availableProcessors();
	private static final int MIO = 1000000;
	private static final int TCP_RELAY_MAX_SIZE = 2 * MIO;

	private final List<TraceEventRecords> elementCollection = new LinkedList<TraceEventRecords>();

	private int numWorkerThreads;

	private final ConcurrentHashMapWithDefault<Long, TraceBuffer> traceId2trace = new ConcurrentHashMapWithDefault<Long, TraceBuffer>(new TraceBuffer());

	private final StageFactory<Counter<IMonitoringRecord>> recordCounterFactory;
	private final StageFactory<ElementDelayMeasuringStage<IMonitoringRecord>> recordDelayFilterFactory;
	private final StageFactory<ElementThroughputMeasuringStage<IMonitoringRecord>> recordThroughputFilterFactory;
	private final StageFactory<InstanceCounter<IMonitoringRecord, TraceMetadata>> traceMetadataCounterFactory;
	private final StageFactory<TraceReconstructionFilter> traceReconstructionFilterFactory;
	private final StageFactory<Counter<TraceEventRecords>> traceCounterFactory;
	private final StageFactory<ElementThroughputMeasuringStage<TraceEventRecords>> traceThroughputFilterFactory;

	private final List<SpScPipe<IMonitoringRecord>> tcpRelayPipes = new LinkedList<SpScPipe<IMonitoringRecord>>();

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public TcpTraceReconstructionAnalysisWithThreadsConfiguration() {
		super();

		try {
			this.recordCounterFactory = new StageFactory(Counter.class.getConstructor());
			this.recordDelayFilterFactory = new StageFactory(ElementDelayMeasuringStage.class.getConstructor());
			this.recordThroughputFilterFactory = new StageFactory(ElementThroughputMeasuringStage.class.getConstructor());
			this.traceMetadataCounterFactory = new StageFactory(InstanceCounter.class.getConstructor(Class.class));
			this.traceReconstructionFilterFactory = new StageFactory(TraceReconstructionFilter.class.getConstructor(ConcurrentHashMapWithDefault.class));
			this.traceCounterFactory = new StageFactory(Counter.class.getConstructor());
			this.traceThroughputFilterFactory = new StageFactory(ElementThroughputMeasuringStage.class.getConstructor());
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException(e);
		} catch (SecurityException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public void buildConfiguration() {
		final Pipeline<TCPReader, Distributor<IMonitoringRecord>> tcpPipeline = this.buildTcpPipeline();
		this.getFiniteProducerStages().add(tcpPipeline);

		final Pipeline<Clock, Distributor<Long>> clockStage = this.buildClockPipeline(1000);
		this.getInfiniteProducerStages().add(clockStage);

		final Pipeline<Clock, Distributor<Long>> clock2Stage = this.buildClockPipeline(2000);
		this.getInfiniteProducerStages().add(clock2Stage);

		this.numWorkerThreads = Math.min(NUM_VIRTUAL_CORES, this.numWorkerThreads);
		for (int i = 0; i < this.numWorkerThreads; i++) {
			StageWithPort pipeline = this.buildPipeline(tcpPipeline.getLastStage(), clockStage.getLastStage(), clock2Stage.getLastStage());
			this.getConsumerStages().add(pipeline);
		}
	}

	private Pipeline<TCPReader, Distributor<IMonitoringRecord>> buildTcpPipeline() {
		TCPReader tcpReader = new TCPReader();
		Distributor<IMonitoringRecord> distributor = new Distributor<IMonitoringRecord>();

		SingleElementPipe.connect(tcpReader.getOutputPort(), distributor.getInputPort());

		// create and configure pipeline
		Pipeline<TCPReader, Distributor<IMonitoringRecord>> pipeline = new Pipeline<TCPReader, Distributor<IMonitoringRecord>>("TCP reader pipeline");
		pipeline.setFirstStage(tcpReader);
		pipeline.setLastStage(distributor);
		return pipeline;
	}

	private Pipeline<Clock, Distributor<Long>> buildClockPipeline(final long intervalDelayInMs) {
		Clock clock = new Clock();
		clock.setInitialDelayInMs(intervalDelayInMs);
		clock.setIntervalDelayInMs(intervalDelayInMs);
		Distributor<Long> distributor = new Distributor<Long>();

		SingleElementPipe.connect(clock.getOutputPort(), distributor.getInputPort());

		// create and configure pipeline
		Pipeline<Clock, Distributor<Long>> pipeline = new Pipeline<Clock, Distributor<Long>>();
		pipeline.setFirstStage(clock);
		pipeline.setLastStage(distributor);
		return pipeline;
	}

	private static class StageFactory<T extends StageWithPort> {

		private final Constructor<T> constructor;
		private final List<T> stages = new ArrayList<T>();

		public StageFactory(final Constructor<T> constructor) {
			this.constructor = constructor;
		}

		public T create(final Object... initargs) {
			try {
				T stage = this.constructor.newInstance(initargs);
				this.stages.add(stage);
				return stage;
			} catch (InstantiationException e) {
				throw new IllegalStateException(e);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			} catch (IllegalArgumentException e) {
				throw new IllegalStateException(e);
			} catch (InvocationTargetException e) {
				throw new IllegalStateException(e);
			}
		}

		public List<T> getStages() {
			return this.stages;
		}
	}

	private StageWithPort buildPipeline(final Distributor<IMonitoringRecord> tcpReaderPipeline,
			final Distributor<Long> clockStage, final Distributor<Long> clock2Stage) {
		// create stages
		Relay<IMonitoringRecord> relay = new Relay<IMonitoringRecord>();
		Counter<IMonitoringRecord> recordCounter = this.recordCounterFactory.create();
		ElementThroughputMeasuringStage<IMonitoringRecord> recordThroughputFilter = this.recordThroughputFilterFactory.create();
		// ElementDelayMeasuringStage<IMonitoringRecord> recordThroughputFilter = this.recordDelayFilterFactory.create();
		InstanceCounter<IMonitoringRecord, TraceMetadata> traceMetadataCounter = this.traceMetadataCounterFactory.create(TraceMetadata.class);
		new InstanceCounter<IMonitoringRecord, TraceMetadata>(TraceMetadata.class);
		final InstanceOfFilter<IMonitoringRecord, IFlowRecord> instanceOfFilter = new InstanceOfFilter<IMonitoringRecord, IFlowRecord>(
				IFlowRecord.class);
		final TraceReconstructionFilter traceReconstructionFilter = this.traceReconstructionFilterFactory.create(this.traceId2trace);
		Counter<TraceEventRecords> traceCounter = this.traceCounterFactory.create();
		ElementThroughputMeasuringStage<TraceEventRecords> traceThroughputFilter = this.traceThroughputFilterFactory.create();
		Sink<TraceEventRecords> endStage = new Sink<TraceEventRecords>();
		// EndStage<IMonitoringRecord> endStage = new EndStage<IMonitoringRecord>();

		// connect stages
		SpScPipe<IMonitoringRecord> tcpRelayPipe = SpScPipe.connect(tcpReaderPipeline.getNewOutputPort(), relay.getInputPort(), TCP_RELAY_MAX_SIZE);
		this.tcpRelayPipes.add(tcpRelayPipe);
		// SysOutFilter<TraceEventRecords> sysout = new SysOutFilter<TraceEventRecords>(tcpRelayPipe);

		SpScPipe.connect(clockStage.getNewOutputPort(), recordThroughputFilter.getTriggerInputPort(), 10);
		SpScPipe.connect(clock2Stage.getNewOutputPort(), traceThroughputFilter.getTriggerInputPort(), 10);

		SingleElementPipe.connect(relay.getOutputPort(), recordCounter.getInputPort());
		SingleElementPipe.connect(recordCounter.getOutputPort(), recordThroughputFilter.getInputPort());
		SingleElementPipe.connect(recordThroughputFilter.getOutputPort(), traceMetadataCounter.getInputPort());
		SingleElementPipe.connect(traceMetadataCounter.getOutputPort(), instanceOfFilter.getInputPort());
		SingleElementPipe.connect(instanceOfFilter.getOutputPort(), traceReconstructionFilter.getInputPort());
		SingleElementPipe.connect(traceReconstructionFilter.getTraceValidOutputPort(), traceCounter.getInputPort());
		// SingleElementPipe.connect(traceReconstructionFilter.getOutputPort(), traceThroughputFilter.getInputPort());
		// SingleElementPipe.connect(traceThroughputFilter.getOutputPort(), traceCounter.getInputPort());
		SingleElementPipe.connect(traceCounter.getOutputPort(), endStage.getInputPort());

		// create and configure pipeline
		Pipeline<Relay<IMonitoringRecord>, Sink<TraceEventRecords>> pipeline = new Pipeline<Relay<IMonitoringRecord>, Sink<TraceEventRecords>>("Worker pipeline");
		pipeline.setFirstStage(relay);
		pipeline.addIntermediateStage(recordCounter);
		pipeline.addIntermediateStage(recordThroughputFilter);
		pipeline.addIntermediateStage(traceMetadataCounter);
		pipeline.addIntermediateStage(instanceOfFilter);
		// pipeline.addIntermediateStage(this.recordThroughputFilter);
		pipeline.addIntermediateStage(traceReconstructionFilter);
		// pipeline.addIntermediateStage(traceThroughputFilter);
		pipeline.addIntermediateStage(traceCounter);
		// pipeline.addIntermediateStage(sysout);
		pipeline.setLastStage(endStage);

		return pipeline;
	}

	public List<TraceEventRecords> getElementCollection() {
		return this.elementCollection;
	}

	public int getNumRecords() {
		int sum = 0;
		for (Counter<IMonitoringRecord> stage : this.recordCounterFactory.getStages()) {
			sum += stage.getNumElementsPassed();
		}
		return sum;
	}

	public int getNumTraces() {
		int sum = 0;
		for (Counter<TraceEventRecords> stage : this.traceCounterFactory.getStages()) {
			sum += stage.getNumElementsPassed();
		}
		return sum;
	}

	public List<Long> getRecordDelays() {
		List<Long> throughputs = new LinkedList<Long>();
		for (ElementDelayMeasuringStage<IMonitoringRecord> stage : this.recordDelayFilterFactory.getStages()) {
			throughputs.addAll(stage.getDelays());
		}
		return throughputs;
	}

	public List<Long> getRecordThroughputs() {
		List<Long> throughputs = new LinkedList<Long>();
		for (ElementThroughputMeasuringStage<IMonitoringRecord> stage : this.recordThroughputFilterFactory.getStages()) {
			throughputs.addAll(stage.getThroughputs());
		}
		return throughputs;
	}

	public List<Long> getTraceThroughputs() {
		List<Long> throughputs = new LinkedList<Long>();
		for (ElementThroughputMeasuringStage<TraceEventRecords> stage : this.traceThroughputFilterFactory.getStages()) {
			throughputs.addAll(stage.getThroughputs());
		}
		return throughputs;
	}

	public List<Integer> getNumTraceMetadatas() {
		List<Integer> numTraceMetadatas = new LinkedList<Integer>();
		for (InstanceCounter<IMonitoringRecord, TraceMetadata> stage : this.traceMetadataCounterFactory.getStages()) {
			numTraceMetadatas.add(stage.getCounter());
		}
		return numTraceMetadatas;
	}

	public List<SpScPipe<IMonitoringRecord>> getTcpRelayPipes() {
		return this.tcpRelayPipes;
	}

	public int getNumWorkerThreads() {
		return this.numWorkerThreads;
	}

	public void setNumWorkerThreads(final int numWorkerThreads) {
		this.numWorkerThreads = numWorkerThreads;
	}

	public int getMaxElementsCreated() {
		return this.traceId2trace.getMaxElements();
	}

}