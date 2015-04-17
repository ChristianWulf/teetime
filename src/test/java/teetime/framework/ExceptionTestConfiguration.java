/**
 * Copyright (C) 2015 TeeTime (http://teetime.sourceforge.net)
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

import teetime.framework.pipe.PipeFactoryRegistry.PipeOrdering;
import teetime.framework.pipe.PipeFactoryRegistry.ThreadCommunication;

public class ExceptionTestConfiguration extends AnalysisConfiguration {

	public ExceptionTestConfiguration() {
		ExceptionTestProducerStage first = new ExceptionTestProducerStage();
		ExceptionTestConsumerStage second = new ExceptionTestConsumerStage();
		ExceptionTestProducerStage third = new ExceptionTestProducerStage();

		PIPE_FACTORY_REGISTRY.getPipeFactory(ThreadCommunication.INTER, PipeOrdering.QUEUE_BASED, false)
				.create(first.getOutputPort(), second.getInputPort());
		// this.addThreadableStage(new ExceptionTestStage());

		this.addThreadableStage(first);
		this.addThreadableStage(second);
		this.addThreadableStage(third);
	}
}
