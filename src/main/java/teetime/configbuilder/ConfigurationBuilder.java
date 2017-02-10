
package teetime.configbuilder;

import java.util.Arrays;
import java.util.function.Function;

import teetime.framework.*;
import teetime.stage.InitialElementProducer;
import teetime.stage.basic.AbstractTransformation;
import teetime.stage.basic.ITransformation;
import teetime.stage.io.Printer;
import teetime.stage.string.ToLowerCase;

public class ConfigurationBuilder {

	private final Configuration configuration = new Configuration();

	private ConfigurationBuilder() {}

	private <O> ConfigurationBuilder.Connection<O> start(final AbstractProducerStage<O> stage) {
		final OutputPort<O> outputPort = stage.getOutputPort();
		return new Connection<O>(outputPort);
	}

	public static <O> ConfigurationBuilder.Connection<O> from(final AbstractProducerStage<O> stage) {
		final ConfigurationBuilder config = new ConfigurationBuilder();
		return config.start(stage);
	}

	public class Connection<I> {

		private final OutputPort<I> lastPort;

		private Connection(final OutputPort<I> lastPort) {
			this.lastPort = lastPort;
		}

		public <O> Connection<O> to(final ITransformation<I, O> stage) {
			final InputPort<I> inputPort = stage.getInputPort();
			final OutputPort<O> outputPort = stage.getOutputPort();

			ConfigurationBuilder.this.configuration.connectPorts(this.lastPort, inputPort, 4); // TODO 4 hard coded

			return new Connection<O>(outputPort);
		}

		// Not sure if should be part of the builder or should be used by "to(new MyStage(), s -> s.getMyInputPort(), s -> s.getMyOutputPort()"
		public <S extends AbstractStage, O> Connection<O> to(final S stage, final Function<S, InputPort<I>> inputPort, final Function<S, OutputPort<O>> outputPort) {
			final ITransformation<I, O> transformerStage = TransfomerStage.of(stage, inputPort, outputPort);
			return this.to(transformerStage);
		}

		public Configuration end(final AbstractConsumerStage<I> stage) {
			final InputPort<I> inputPort = stage.getInputPort();
			ConfigurationBuilder.this.configuration.connectPorts(this.lastPort, inputPort, 4); // TODO 4 hard coded

			return ConfigurationBuilder.this.configuration;
		}

		public OutputPort<I> getOutputPort() {
			return this.lastPort;
		}

	}

	// TODO outsource to own stage
	public static class TransfomerStage<I, O> implements ITransformation<I, O> {

		private final InputPort<I> inputPort;
		private final OutputPort<O> outputPort;

		private TransfomerStage(final InputPort<I> inputPort, final OutputPort<O> outputPort) {
			this.inputPort = inputPort;
			this.outputPort = outputPort;
		}

		@Override
		public InputPort<I> getInputPort() {
			return this.inputPort;
		}

		@Override
		public OutputPort<O> getOutputPort() {
			return this.outputPort;
		}

		public static <S extends AbstractStage, I, O> TransfomerStage<I, O> of(final S stage, final Function<S, InputPort<I>> inputPort,
				final Function<S, OutputPort<O>> outputPort) {
			return new TransfomerStage<I, O>(inputPort.apply(stage), outputPort.apply(stage));
		}

	}

	///////// EXAMPLE /////////

	public static void main(final String[] args) {

		final Configuration config = ConfigurationBuilder.from(new InitialElementProducer<String>(Arrays.asList("uno", "dos", "tres")))
				.to(new ToUpperCaseStage())
				.to(TransfomerStage.of(new ToLowerCase(), s -> s.getInputPort(), s -> s.getOutputPort()))
				.to(new StringLengthStage())
				.end(new Printer<Integer>());

		final Execution<Configuration> execution = new Execution<Configuration>(config);
		execution.executeBlocking();

	}

	private static class ToUpperCaseStage extends AbstractTransformation<String, String> {

		@Override
		protected void execute(final String string) {
			final String upperCaseString = string.toUpperCase();
			this.getOutputPort().send(upperCaseString);
		}

	}

	private static class StringLengthStage extends AbstractTransformation<String, Integer> {

		@Override
		protected void execute(final String string) {
			final int length = string.length();
			this.getOutputPort().send(length);
		}

	}

}
