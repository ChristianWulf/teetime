package teetime.variant.methodcallWithPorts.examples.kiekerdays;

import teetime.variant.explicitScheduling.framework.core.Analysis;
import teetime.variant.methodcallWithPorts.framework.core.RunnableStage;
import teetime.variant.methodcallWithPorts.framework.core.StageWithPort;

import kieker.common.record.IMonitoringRecord;

public class TcpTraceLogging extends Analysis {

	private Thread tcpThread;

	@Override
	public void init() {
		super.init();
		StageWithPort<Void, IMonitoringRecord> tcpPipeline = this.buildTcpPipeline();
		this.tcpThread = new Thread(new RunnableStage<Void>(tcpPipeline));
	}

	@Override
	public void start() {
		super.start();

		this.tcpThread.start();

		try {
			this.tcpThread.join();
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}

	private StageWithPort<Void, IMonitoringRecord> buildTcpPipeline() {
		TCPReaderSink tcpReader = new TCPReaderSink();
		// EndStage<IMonitoringRecord> endStage = new EndStage<IMonitoringRecord>();
		//
		// SingleElementPipe.connect(tcpReader.getOutputPort(), endStage.getInputPort());
		//
		// // create and configure pipeline
		// Pipeline<Void, IMonitoringRecord> pipeline = new Pipeline<Void, IMonitoringRecord>();
		// pipeline.setFirstStage(tcpReader);
		// pipeline.setLastStage(endStage);
		return tcpReader;
	}

	public static void main(final String[] args) {
		final TcpTraceLogging analysis = new TcpTraceLogging();

		analysis.init();
		try {
			analysis.start();
		} finally {
			analysis.onTerminate();
		}
	}

}
