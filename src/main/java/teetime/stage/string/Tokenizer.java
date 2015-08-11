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
package teetime.stage.string;

import java.util.regex.Pattern;

import teetime.framework.AbstractConsumerStage;
import teetime.framework.OutputPort;

public final class Tokenizer extends AbstractConsumerStage<String> {

	private final OutputPort<String> outputPort = this.createOutputPort();
	private final String regex;
	private final Pattern pattern;

	public Tokenizer(final String regex) {
		this.regex = regex;
		pattern = Pattern.compile(regex);
	}

	@Override
	protected void execute(final String element) {
		// Matcher matcher = pattern.matcher(element);
		// while (matcher.find()) {
		// String token = element.substring(matcher.start(), matcher.end());
		// outputPort.send(token);
		// }
		String[] tokens = element.split(regex);
		for (String token : tokens) {
			outputPort.send(token);
		}
		// Scanner is much slower
		// Pattern is equally fast
	}

	public OutputPort<String> getOutputPort() {
		return this.outputPort;
	}

}
