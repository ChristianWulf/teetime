package teetime.variant.explicitScheduling.framework.core;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Christian Wulf
 * 
 * @since 1.10
 */
public class Context<S extends IStage> {

	/**
	 * @author Christian Wulf
	 * 
	 * @since 1.10
	 */
	private static class InputPortContainer {
		public final List<Object> takenElements = new ArrayList<Object>();
		public IPipe<Object> pipe;

		public InputPortContainer() {}
	}

	private final InputPortContainer[] inputPortContainers;
	private final IOutputPort<S, ?>[] outputPorts;

	// statistics values
	private long numPushedElements = 0;
	private long numTakenElements = 0;

	private long numTakenElementsInCurrentTransaction = 0;

	@SuppressWarnings("unchecked")
	public Context(final IStage owningStage, final List<IInputPort<S, ?>> allTargetPorts) {
		this.inputPortContainers = this.createInputPortLists(owningStage.getInputPorts());
		this.outputPorts = new IOutputPort[owningStage.getOutputPorts().size()];
	}

	@SuppressWarnings("unchecked")
	private InputPortContainer[] createInputPortLists(final List<IInputPort<IStage, ?>> inputPorts) {
		final InputPortContainer[] inputPortContainers = new InputPortContainer[inputPorts.size()];
		for (int i = 0; i < inputPorts.size(); i++) {
			inputPortContainers[i] = new InputPortContainer();
			inputPortContainers[i].pipe = (IPipe<Object>) inputPorts.get(i).getAssociatedPipe();
		}
		return inputPortContainers;
	}

	/**
	 * @since 1.10
	 */
	public <T> void put(final IOutputPort<S, T> port, final T object) {
		final IPipe<? super T> associatedPipe = port.getAssociatedPipe();
		if (associatedPipe == null) {
			return; // ignore unconnected port
			// BETTER return a NullObject rather than checking for null
		}
		associatedPipe.put(object);

		this.outputPorts[port.getIndex()] = port;
		this.numPushedElements++;
	}

	/**
	 * 
	 * @param inputPort
	 * @return
	 * @since 1.10
	 */
	public <T> T tryTake(final IInputPort<S, T> inputPort) {
		final IPipe<? super T> associatedPipe = inputPort.getAssociatedPipe();
		final T token = associatedPipe.tryTake();
		if (token != null) {
			this.logTransaction(inputPort, token);
		}
		return token;
	}

	/**
	 * 
	 * @param inputPort
	 * @return
	 * @since 1.10
	 */
	public <T> T take(final IInputPort<S, T> inputPort) {
		final IPipe<? super T> associatedPipe = inputPort.getAssociatedPipe();
		final T token = associatedPipe.take();
		if (token != null) {
			this.logTransaction(inputPort, token);
		}
		return token;
	}

	private final <T> void logTransaction(final IInputPort<S, T> inputPort, final T token) {
		// final InputPortContainer inputPortContainer = this.inputPortContainers[inputPort.getIndex()];
		// inputPortContainer.takenElements.add(token);

		this.numTakenElementsInCurrentTransaction++;
	}

	/**
	 * 
	 * @param inputPort
	 * @return
	 * 
	 * @since 1.10
	 */
	public <T> T read(final IInputPort<S, T> inputPort) {
		final IPipe<? super T> associatedPipe = inputPort.getAssociatedPipe();
		return associatedPipe.read();
	}

	void commit() {
		// for (final List<Object> takenElements : this.pipesTakenFrom.values()) {
		for (final InputPortContainer inputPortContainer : this.inputPortContainers) {
			// inputPortContainer.takenElements.clear();

			IReservablePipe<Object> reservablePipe = (IReservablePipe<Object>) inputPortContainer.pipe;
			reservablePipe.commit();
		}

		this.numTakenElements += this.numTakenElementsInCurrentTransaction;
		this.numTakenElementsInCurrentTransaction = 0;

		for (final IOutputPort<S, ?> outputPort : this.outputPorts) {
			if (outputPort != null) {
				@SuppressWarnings("unchecked")
				IReservablePipe<Object> reservablePipe = (IReservablePipe<Object>) outputPort.getAssociatedPipe();
				reservablePipe.commit();
			}
		}
	}

	void rollback() {
		for (final InputPortContainer inputPortContainer : this.inputPortContainers) {
			// for (int k = inputPortContainer.takenElements.size() - 1; k >= 0; k--) {
			// final Object element = inputPortContainer.takenElements.get(k);
			// inputPortContainer.pipe.put(element);
			// }
			IReservablePipe<Object> reservablePipe = (IReservablePipe<Object>) inputPortContainer.pipe;
			reservablePipe.rollback();
		}

		for (final IOutputPort<S, ?> outputPort : this.outputPorts) {
			if (outputPort != null) {
				@SuppressWarnings("unchecked")
				IReservablePipe<Object> reservablePipe = (IReservablePipe<Object>) outputPort.getAssociatedPipe();
				reservablePipe.rollback();
			}
		}
	}

	@Override
	public String toString() {
		return "{" + "numTakenElements=" + this.numTakenElements + ", " + "numPushedElements=" + this.numPushedElements + "}";
	}

	/**
	 * @return <code>true</code> iff all input ports are empty, otherwise <code>false</code>.
	 */
	public boolean inputPortsAreEmpty() {
		for (final InputPortContainer inputPortContainer : this.inputPortContainers) {
			if (!inputPortContainer.pipe.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @since 1.10
	 */
	public void clearSucessors() {
		for (int i = 0; i < this.outputPorts.length; i++) {
			this.outputPorts[i] = null;
		}
	}

	/**
	 * @return
	 * @since 1.10
	 */
	public IOutputPort<S, ?>[] getOutputPorts() {
		return this.outputPorts;
	}
}