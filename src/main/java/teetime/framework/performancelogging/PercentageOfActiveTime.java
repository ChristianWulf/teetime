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
package teetime.framework.performancelogging;

class PercentageOfActiveTime implements ActivationStateLogger.IFormatingStrategy {

	/**
	 *
	 */
	private final ActivationStateLogger stateLogger;

	/**
	 * @param stateLogger
	 */
	PercentageOfActiveTime(final ActivationStateLogger stateLogger) {
		this.stateLogger = stateLogger;
	}

	@Override
	public String formatData() {
		String result = "\n	Formating of the data to get Percentage of active time:\n\n";

		for (StateLoggable stage : this.stateLogger.getStages()) {
			result += this.stateLogger.formateName(stage);

			long firstTimeStamp = stage.getStates().get(0).getTimeStamp();
			long lastTimeStamp = Long.MIN_VALUE;
			long commulativeActiveTime = 0;
			long lastActiveTimeStamp = 0;
			boolean lastActive = false;

			for (StateChange state : stage.getStates()) {
				long currentTimeStamp = state.getTimeStamp();
				if (state.getCause() == StateChange.NOTHING_FAILED) {
					if (!lastActive) {
						lastActiveTimeStamp = currentTimeStamp;
					}
					lastActive = true;
				} else {
					if (lastActive && lastActiveTimeStamp != 0) {
						commulativeActiveTime += (currentTimeStamp - lastActiveTimeStamp);
					}
					lastActive = false;
				}
				if (currentTimeStamp > lastTimeStamp) {
					lastTimeStamp = currentTimeStamp;
				}
				if (currentTimeStamp < firstTimeStamp) {
					firstTimeStamp = currentTimeStamp;
				}
			}

			long totalTime = (lastActiveTimeStamp - firstTimeStamp);

			// Add formated data to the line
			result += " Percentage of ActiveTime: "
					// I differentiate between stages that were active the whole time and the ones that were interrupted in between.
					// this will help to keep track of the necessary information.
					+ ((totalTime != 0) ? (commulativeActiveTime / (lastActiveTimeStamp - firstTimeStamp)) : "100") + "%";

			result += "\n";
		}
		return result.replace("\n", String.format("%n"));
	}

}
