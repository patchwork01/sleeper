/*
 * Copyright 2022-2023 Crown Copyright
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
package sleeper.configuration.properties;

import com.amazonaws.services.s3.AmazonS3;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sleeper.configuration.properties.format.SleeperPropertiesPrettyPrinter;
import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.instance.SleeperProperty;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;
import static sleeper.configuration.properties.PropertiesUtils.loadProperties;

/**
 * Abstract class which backs both {@link InstanceProperties} and
 * {@link sleeper.configuration.properties.table.TableProperties}.
 */
public abstract class SleeperProperties<T extends SleeperProperty> implements SleeperPropertyValues<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SleeperProperties.class);
    private final Properties properties;

    protected SleeperProperties() {
        this(new Properties());
    }

    protected SleeperProperties(Properties properties) {
        this.properties = properties;
    }

    protected void init() {
        validate();
    }

    public final void validate() {
        SleeperPropertiesValidationReporter reporter = new SleeperPropertiesValidationReporter();
        validate(reporter);
        reporter.throwIfFailed();
    }

    public void validate(SleeperPropertiesValidationReporter reporter) {
        getPropertiesIndex().getUserDefined().forEach(property ->
                property.validate(get(property), reporter));
    }

    public abstract SleeperPropertyIndex<T> getPropertiesIndex();

    protected abstract SleeperPropertiesPrettyPrinter<T> getPrettyPrinter(PrintWriter writer);

    public void saveUsingPrettyPrinter(PrintWriter writer) {
        this.getPrettyPrinter(writer).print(this);
    }

    public String get(T property) {
        String value = properties.getProperty(property.getPropertyName(), property.getDefaultValue());
        if ("".equals(value)) {
            return property.getDefaultValue();
        }
        return value;
    }

    public void setNumber(T property, Number number) {
        if (number != null) {
            set(property, number.toString());
        }
    }

    public void set(T property, String value) {
        if (value != null) {
            properties.setProperty(property.getPropertyName(), value);
        }
    }

    public void unset(T property) {
        properties.remove(property.getPropertyName());
    }

    public boolean isAnyPropertySetStartingWith(String propertyNameStart) {
        return properties.stringPropertyNames().stream().anyMatch(name -> name.startsWith(propertyNameStart));
    }

    public boolean isSet(T property) {
        return properties.containsKey(property.getPropertyName()) &&
                !"".equals(properties.getProperty(property.getPropertyName()));
    }

    public Properties getProperties() {
        return properties;
    }

    public void save(OutputStream outputStream) {
        try {
            properties.store(outputStream, "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void save(File file) {
        try {
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file));
            save(outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void save(Path file) {
        try {
            OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(file));
            save(outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String saveAsString() {
        StringWriter stringWriter = new StringWriter();
        try {
            properties.store(stringWriter, "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return stringWriter.toString();
    }

    public void resetAndValidate(Properties newProperties) {
        properties.clear();
        properties.putAll(newProperties);
        init();
    }

    protected void saveToS3(AmazonS3 s3Client, String bucket, String key) {
        LOGGER.debug("Uploading config to bucket {}", bucket);
        s3Client.putObject(bucket, key, saveAsString());
    }

    protected void loadFromS3(AmazonS3 s3Client, String bucket, String key) {
        String propertiesString = s3Client.getObjectAsString(bucket, key);
        resetAndValidate(loadProperties(propertiesString));
    }

    public Stream<Map.Entry<String, String>> getUnknownProperties() {
        return properties.stringPropertyNames().stream()
                .filter(not(this::isKnownProperty))
                .map(name -> Map.entry(name, properties.getProperty(name)));
    }

    private boolean isKnownProperty(String propertyName) {
        return getPropertiesIndex().getByName(propertyName).isPresent();
    }

    public Map<String, String> toMap() {
        return PropertiesUtils.toMap(properties);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SleeperProperties<?> that = (SleeperProperties<?>) o;

        return new EqualsBuilder().append(properties, that.properties).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(properties).toHashCode();
    }

    @Override
    public String toString() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        getPrettyPrinter(new PrintWriter(stream, false, StandardCharsets.UTF_8)).print(this);
        return stream.toString(StandardCharsets.UTF_8);
    }
}
