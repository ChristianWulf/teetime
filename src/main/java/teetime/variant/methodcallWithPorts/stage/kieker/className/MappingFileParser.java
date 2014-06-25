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
package teetime.variant.methodcallWithPorts.stage.kieker.className;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import kieker.common.logging.Log;
import kieker.common.util.filesystem.FSUtil;

/**
 * @author Christian Wulf
 * 
 * @since 1.10
 */
public class MappingFileParser {

	protected Log logger;

	private static final Map<String, String> filePrefixRegistry = new HashMap<String, String>();

	static {
		filePrefixRegistry.put(FSUtil.MAP_FILENAME, FSUtil.FILE_PREFIX);
		filePrefixRegistry.put(FSUtil.LEGACY_MAP_FILENAME, FSUtil.LEGACY_FILE_PREFIX);
	}

	public MappingFileParser(final Log logger) {
		this.logger = logger;
	}

	public ClassNameRegistry parseFromStream(final InputStream inputStream) {
		final ClassNameRegistry classNameRegistry = new ClassNameRegistry();

		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(inputStream, FSUtil.ENCODING));
			String line;
			while ((line = in.readLine()) != null) { // NOPMD (assign)
				this.parseTextLine(line, classNameRegistry);
			}
		} catch (final IOException ex) {
			this.logger.error("Error reading mapping file", ex);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (final IOException ex) {
					this.logger.error("Exception while closing input stream for mapping file", ex);
				}
			}
		}

		return classNameRegistry;
	}

	private void parseTextLine(final String line, final Map<Integer, String> stringRegistry) {
		if (line.length() == 0) {
			return; // ignore empty lines
		}
		final int split = line.indexOf('=');
		if (split == -1) {
			this.logger.error("Failed to find character '=' in line: {" + line + "}. It must consist of a ID=VALUE pair.");
			return; // continue on errors
		}
		final String key = line.substring(0, split);
		final String value = FSUtil.decodeNewline(line.substring(split + 1));
		// the leading $ is optional
		final Integer id;
		try {
			id = Integer.valueOf((key.charAt(0) == '$') ? key.substring(1) : key); // NOCS
		} catch (final NumberFormatException ex) {
			this.logger.error("Error reading mapping file, id must be integer", ex);
			return; // continue on errors
		}
		final String prevVal = stringRegistry.put(id, value);
		if (prevVal != null) {
			this.logger.error("Found addional entry for id='" + id + "', old value was '" + prevVal + "' new value is '" + value + "'");
		}
	}

	/**
	 * @since 1.10
	 */
	public File findMappingFile(final File dirPath) {
		File mappingFile = new File(dirPath, FSUtil.MAP_FILENAME);
		if (!mappingFile.exists()) {
			// No mapping file found. Check whether we find a legacy tpmon.map file!
			mappingFile = new File(dirPath, FSUtil.LEGACY_MAP_FILENAME);
			if (mappingFile.exists()) {
				this.logger.info("Directory '" + dirPath + "' contains no file '" + FSUtil.MAP_FILENAME + "'. Found '" + FSUtil.LEGACY_MAP_FILENAME
						+ "' ... switching to legacy mode");
			} else {
				// no {kieker|tpmon}.map exists. This is valid for very old monitoring logs. Hence, only dump a log.warn
				this.logger.warn("No mapping file in directory '" + dirPath.getAbsolutePath() + "'");
				return null;
			}
		}

		return mappingFile;
	}

	/**
	 * @return <code>null</code> if a file prefix for the given <code>mappingFile</code> is not registered.
	 * @since 1.10
	 */
	public String getFilePrefixFromMappingFile(final File mappingFile) {
		return MappingFileParser.filePrefixRegistry.get(mappingFile.getName());
	}
}