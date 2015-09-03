/**
 * Copyright (C) 2015 Christian Wulf, Nelson Tavares de Sousa (http://christianwulf.github.io/teetime)
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
package teetime.stage.taskfarm;

import java.util.LinkedList;
import java.util.List;

import teetime.framework.AbstractCompositeStage;
import teetime.framework.InputPort;
import teetime.framework.OutputPort;
import teetime.stage.basic.distributor.dynamic.DynamicDistributor;
import teetime.stage.basic.merger.dynamic.DynamicMerger;
import teetime.stage.taskfarm.adaptation.AdaptationThread;
import teetime.stage.taskfarm.monitoring.PipeMonitoringService;
import teetime.stage.taskfarm.monitoring.SingleTaskFarmMonitoringService;

/**
 * The TaskFarmStage implements the task farm parallelization pattern in
 * TeeTime. It dynamically adds CPU resources at runtime depending on
 * the current CPU load and the behavior of the enclosed stage.
 *
 * @author Christian Claus Wiechmann
 *
 * @param <I>
 *            Input type of Task Farm
 * @param <O>
 *            Output type of Task Farm
 * @param <T>
 *            Type of enclosed stage
 */
public final class TaskFarmStage<I, O, T extends ITaskFarmDuplicable<I, O>> extends AbstractCompositeStage {

	private final List<ITaskFarmDuplicable<I, O>> enclosedStageInstances = new LinkedList<ITaskFarmDuplicable<I, O>>();

	private final DynamicDistributor<I> distributor = new DynamicDistributor<I>();
	private final DynamicMerger<O> merger;

	private final TaskFarmConfiguration<I, O, T> configuration = new TaskFarmConfiguration<I, O, T>();

	private final AdaptationThread<I, O, T> adaptationThread;

	private final PipeMonitoringService pipeMonitoringService = new PipeMonitoringService();
	private final SingleTaskFarmMonitoringService taskFarmMonitoringService;

	public TaskFarmStage(final T workerStage) {
		this(workerStage, null, 100);
	}

	public TaskFarmStage(final T workerStage, final int pipeCapacity) {
		this(workerStage, null, pipeCapacity);
	}

	public TaskFarmStage(final T workerStage, final DynamicMerger<O> merger, final int pipeCapacity) {
		super();

		if (null == workerStage) {
			throw new IllegalArgumentException("The constructor of a Task Farm may not be called with null as the worker stage.");
		}

		if (merger == null) {
			this.merger = new DynamicMerger<O>() {
				@Override
				public void onStarting() throws Exception {
					adaptationThread.start();
					super.onStarting();
				}

				@Override
				public void onTerminating() throws Exception {
					adaptationThread.stopAdaptationThread();
					super.onTerminating();
				}
			};
		} else {
			this.merger = merger;
		}

		taskFarmMonitoringService = new SingleTaskFarmMonitoringService(this);
		adaptationThread = new AdaptationThread<I, O, T>(this);

		configuration.setPipeCapacity(pipeCapacity);

		this.init(workerStage);
	}

	private void init(final T includedStage) {
		final InputPort<I> stageInputPort = includedStage.getInputPort();
		connectPorts(this.distributor.getNewOutputPort(), stageInputPort, configuration.getPipeCapacity());

		final OutputPort<O> stageOutputPort = includedStage.getOutputPort();
		connectPorts(stageOutputPort, this.merger.getNewInputPort(), configuration.getPipeCapacity());

		addThreadableStage(this.merger);
		addThreadableStage(includedStage.getInputPort().getOwningStage());

		enclosedStageInstances.add(includedStage);
	}

	public InputPort<I> getInputPort() {
		return this.distributor.getInputPort();
	}

	public OutputPort<O> getOutputPort() {
		return this.merger.getOutputPort();
	}

	public TaskFarmConfiguration<I, O, T> getConfiguration() {
		return this.configuration;
	}

	public ITaskFarmDuplicable<I, O> getBasicEnclosedStage() {
		return this.enclosedStageInstances.get(0);
	}

	public List<ITaskFarmDuplicable<I, O>> getEnclosedStageInstances() {
		return this.enclosedStageInstances;
	}

	public DynamicDistributor<I> getDistributor() {
		return this.distributor;
	}

	public DynamicMerger<O> getMerger() {
		return this.merger;
	}

	public PipeMonitoringService getPipeMonitoringService() {
		return this.pipeMonitoringService;
	}

	public SingleTaskFarmMonitoringService getTaskFarmMonitoringService() {
		return this.taskFarmMonitoringService;
	}
}
