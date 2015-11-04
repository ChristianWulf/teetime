/**
 * Copyright (C) 2015 Christian Wulf, Nelson Tavares de Sousa (http://teetime-framework.github.io)
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import teetime.framework.signal.ValidatingSignal;
import teetime.framework.validation.AnalysisNotValidException;

/**
 * Represents an Execution to which stages can be added and executed later.
 * This needs a {@link Configuration},
 * in which the adding and configuring of stages takes place.
 * To start the analysis {@link #executeBlocking()} needs to be executed.
 * This class will automatically create threads and join them without any further commitment.
 *
 * @author Christian Wulf, Nelson Tavares de Sousa
 *
 * @param <T>
 *            the type of the {@link Configuration}
 *
 * @since 2.0
 */
public final class Execution<T extends Configuration> {

	private static final Logger LOGGER = LoggerFactory.getLogger(Execution.class);

	private final T configuration;
	private final ConfigurationContext configurationContext;

	/**
	 * Creates a new {@link Execution} that skips validating the port connections and uses the default listener.
	 *
	 * @param configuration
	 *            to be used for the analysis
	 */
	public Execution(final T configuration) {
		this(configuration, false);
	}

	/**
	 * Creates a new {@link Execution} that uses a specific listener.
	 *
	 * @param configuration
	 *            to be used for the analysis
	 * @param validationEnabled
	 *            whether or not the validation should be executed
	 */
	public Execution(final T configuration, final boolean validationEnabled) {
		this.configuration = configuration;
		this.configurationContext = configuration.getContext();
		if (configuration.isInitialized()) {
			throw new IllegalStateException("3001 - Configuration has already been used.");
		}
		configuration.setInitialized(true);
		if (validationEnabled) {
			validateStages();
		}
		init();
	}

	// BETTER validate concurrently
	private void validateStages() {
		final Set<AbstractStage> threadableStages = configurationContext.getThreadableStages();
		for (AbstractStage stage : threadableStages) {
			// // portConnectionValidator.validate(stage);
			// }

			final ValidatingSignal validatingSignal = new ValidatingSignal();
			stage.onSignal(validatingSignal, null);
			if (validatingSignal.getInvalidPortConnections().size() > 0) {
				throw new AnalysisNotValidException(validatingSignal.getInvalidPortConnections());
			}
		}
	}

	/**
	 * This initializes the analysis and needs to be run right before starting it.
	 *
	 */
	private final void init() {
		configurationContext.initializeServices();
	}

	/**
	 * Calling this method will block the current thread until the execution terminates.
	 *
	 * @throws ExecutionException
	 *             if at least one exception in one thread has occurred within the execution. The exception contains the pairs of thread and throwable
	 *
	 * @since 2.0
	 */
	public void waitForTermination() {
		configurationContext.waitForConfigurationToTerminate();

		Map<Thread, List<Exception>> threadExceptionsMap = configuration.getFactory().getThreadExceptionsMap();
		Iterator<Entry<Thread, List<Exception>>> iterator = threadExceptionsMap.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<Thread, List<Exception>> entry = iterator.next();
			if (entry.getValue().isEmpty()) {
				iterator.remove();
			}
		}

		if (!threadExceptionsMap.isEmpty()) {
			throw new ExecutionException(threadExceptionsMap);
		}
	}

	// TODO: implement
	public void abortEventually() {
		configurationContext.abortConfigurationRun();
		waitForTermination();
	}

	/**
	 * This method will start this execution and block until it is finished.
	 *
	 * @throws ExecutionException
	 *             if at least one exception in one thread has occurred within the execution. The exception contains the pairs of thread and throwable.
	 *
	 * @since 2.0
	 */
	public void executeBlocking() {
		executeNonBlocking();
		waitForTermination();
	}

	/**
	 * This method starts this execution without waiting for its termination. The method {@link #waitForTermination()} must be called to unsure a correct termination
	 * of the execution.
	 *
	 * @since 2.0
	 */
	public void executeNonBlocking() {
		if (configuration.isExecuted()) {
			throw new IllegalStateException("3002 - Any configuration instance may only be executed once.");
		}
		configuration.setExecuted(true);
		configurationContext.executeConfiguration();
	}

	/**
	 * Retrieves the Configuration which was used to add and arrange all stages needed for this execution.
	 *
	 * @return the configuration used for this execution
	 */
	public T getConfiguration() {
		return this.configuration;
	}

	private static List<Configuration> configLoader(final String[] args) {
		List<Configuration> instances = new ArrayList<Configuration>();
		for (String each : args) {
			try {
				Class<?> clazz = Class.forName(each);
				Object obj = clazz.newInstance();
				if (obj instanceof Configuration) {
					instances.add((Configuration) obj);
				}
			} catch (ClassNotFoundException e) {
				LOGGER.error("Could not find class " + each);
			} catch (InstantiationException e) {
				LOGGER.error("Could not instantiate class " + each, e);
			} catch (IllegalAccessException e) {
				LOGGER.error("IllegalAccessException arised while instantiating class " + each, e);
			}
		}
		return instances;
	}

	public static void main(final String[] args) {
		List<Configuration> instances = configLoader(args);
		for (Configuration configuration : instances) {
			new Execution<Configuration>(configuration).executeBlocking();
		}
	}
}
