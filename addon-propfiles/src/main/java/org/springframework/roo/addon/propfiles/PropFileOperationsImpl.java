package org.springframework.roo.addon.propfiles;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.springframework.roo.metadata.MetadataService;
import org.springframework.roo.process.manager.FileManager;
import org.springframework.roo.process.manager.MutableFile;
import org.springframework.roo.project.Path;
import org.springframework.roo.project.PathResolver;
import org.springframework.roo.project.ProjectMetadata;
import org.springframework.roo.support.util.Assert;

/**
 * Provides property file configuration operations.
 * 
 * @author Ben Alex
 * @author Stefan Schmidt
 * @since 1.0
 */
@Component 
@Service 
public class PropFileOperationsImpl implements PropFileOperations {
	private static final boolean SORTED = true;
	private static final boolean CHANGE_EXISTING = true;
	@Reference private FileManager fileManager;
	@Reference private PathResolver pathResolver;
	@Reference private MetadataService metadataService;

	public boolean isPropertiesCommandAvailable() {
		return metadataService.get(ProjectMetadata.getProjectIdentifier()) != null;
	}

	public void addPropertyIfNotExists(Path propertyFilePath, String propertyFilename, String key, String value) {
		manageProperty(propertyFilePath, propertyFilename, key, value, !SORTED, !CHANGE_EXISTING);
	}

	public void addPropertyIfNotExists(Path propertyFilePath, String propertyFilename, String key, String value, boolean sorted) {
		manageProperty(propertyFilePath, propertyFilename, key, value, sorted, !CHANGE_EXISTING);
	}

	public void changeProperty(Path propertyFilePath, String propertyFilename, String key, String value) {
		manageProperty(propertyFilePath, propertyFilename, key, value, !SORTED, CHANGE_EXISTING);
	}

	public void changeProperty(Path propertyFilePath, String propertyFilename, String key, String value, boolean sorted) {
		manageProperty(propertyFilePath, propertyFilename, key, value, sorted, CHANGE_EXISTING);
	}

	private void manageProperty(Path propertyFilePath, String propertyFilename, String key, String value, boolean sorted, boolean changeExisting) {
		Assert.notNull(propertyFilePath, "Property file path required");
		Assert.hasText(propertyFilename, "Property filename required");
		Assert.hasText(key, "Key required");
		Assert.hasText(value, "Value required");

		String filePath = pathResolver.getIdentifier(propertyFilePath, propertyFilename);
		MutableFile mutableFile = null;

		Properties props;
		if (sorted) {
			props = new Properties() {
				private static final long serialVersionUID = 1L;

				// override the keys() method to order the keys alphabetically
				@SuppressWarnings({ "unchecked" })
				public synchronized Enumeration keys() {
					final Object[] keys = keySet().toArray();
					Arrays.sort(keys);
					return new Enumeration() {
						int i = 0;

						public boolean hasMoreElements() {
							return i < keys.length;
						}

						public Object nextElement() {
							return keys[i++];
						}
					};
				}
			};
		} else {
			props = new Properties();
		}

		if (fileManager.exists(filePath)) {
			mutableFile = fileManager.updateFile(filePath);
			loadProps(props, mutableFile.getInputStream());
		} else {
			throw new IllegalStateException("Properties file not found");
		}

		String propValue = props.getProperty(key);
		if (propValue == null || (!propValue.equals(value) && changeExisting)) {
			props.setProperty(key, value);
			storeProps(props, mutableFile.getOutputStream(), "Updated at " + new Date());
		}
	}

	public void removeProperty(Path propertyFilePath, String propertyFilename, String key) {
		Assert.notNull(propertyFilePath, "Property file path required");
		Assert.hasText(propertyFilename, "Property filename required");
		Assert.hasText(key, "Key required");

		String filePath = pathResolver.getIdentifier(propertyFilePath, propertyFilename);
		MutableFile mutableFile = null;
		Properties props = new Properties();

		if (fileManager.exists(filePath)) {
			mutableFile = fileManager.updateFile(filePath);
			loadProps(props, mutableFile.getInputStream());
		} else {
			throw new IllegalStateException("Properties file not found");
		}

		props.remove(key);

		storeProps(props, mutableFile.getOutputStream(), "Updated at " + new Date());
	}

	public String getProperty(Path propertyFilePath, String propertyFilename, String key) {
		Assert.notNull(propertyFilePath, "Property file path required");
		Assert.hasText(propertyFilename, "Property filename required");
		Assert.hasText(key, "Key required");

		String filePath = pathResolver.getIdentifier(propertyFilePath, propertyFilename);
		MutableFile mutableFile = null;
		Properties props = new Properties();

		if (fileManager.exists(filePath)) {
			mutableFile = fileManager.updateFile(filePath);
			loadProps(props, mutableFile.getInputStream());
		} else {
			return null;
		}

		return props.getProperty(key);
	}

	public SortedSet<String> getPropertyKeys(Path propertyFilePath, String propertyFilename, boolean includeValues) {
		Assert.notNull(propertyFilePath, "Property file path required");
		Assert.hasText(propertyFilename, "Property filename required");

		String filePath = pathResolver.getIdentifier(propertyFilePath, propertyFilename);
		Properties props = new Properties();

		try {
			if (fileManager.exists(filePath)) {
				loadProps(props, new FileInputStream(filePath));
			} else {
				throw new IllegalStateException("Properties file not found");
			}
		} catch (IOException ioe) {
			throw new IllegalStateException(ioe);
		}

		SortedSet<String> result = new TreeSet<String>();
		for (Object key : props.keySet()) {
			String info;
			if (includeValues) {
				info = key.toString() + " = " + props.getProperty(key.toString());
			} else {
				info = key.toString();
			}
			result.add(info);
		}
		return result;
	}

	public Map<String, String> getProperties(Path propertyFilePath, String propertyFilename) {
		Assert.notNull(propertyFilePath, "Property file path required");
		Assert.hasText(propertyFilename, "Property filename required");

		String filePath = pathResolver.getIdentifier(propertyFilePath, propertyFilename);
		Properties props = new Properties();

		try {
			if (fileManager.exists(filePath)) {
				loadProps(props, new FileInputStream(filePath));
			} else {
				throw new IllegalStateException("Properties file not found");
			}
		} catch (IOException ioe) {
			throw new IllegalStateException(ioe);
		}

		Map<String, String> result = new HashMap<String, String>();
		for (Object key : props.keySet()) {
			result.put(key.toString(), props.getProperty(key.toString()));
		}
		return Collections.unmodifiableMap(result);
	}

	private void loadProps(Properties props, InputStream is) {
		try {
			props.load(is);
		} catch (IOException e) {
			throw new IllegalStateException("Could not load properties", e);
		} finally {
			try {
				is.close();
			} catch (IOException ignore) {
			}
		}
	}

	private void storeProps(Properties props, OutputStream os, String comment) {
		try {
			props.store(os, comment);
		} catch (IOException e) {
			throw new IllegalStateException("Could not store properties", e);
		} finally {
			try {
				os.close();
			} catch (IOException ignore) {
			}
		}
	}
}
