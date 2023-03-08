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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static sleeper.configuration.properties.SystemDefinedInstanceProperty.CONFIG_BUCKET;
import static sleeper.configuration.properties.UserDefinedInstanceProperty.TAGS;

/**
 * Contains all the properties needed to deploy an instance of Sleeper.
 */
public class InstanceProperties extends SleeperProperties<InstanceProperty> {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceProperties.class);

    public static final String S3_INSTANCE_PROPERTIES_FILE = "config";

    protected Map<String, String> tags = new HashMap<>();

    public InstanceProperties() {
        super();
    }

    public InstanceProperties(Properties properties) {
        super(properties);
    }

    @Override
    protected void init() {
        tags = csvTagsToMap(get(TAGS));
        super.init();
    }

    /**
     * Validates all UserDefinedProperties
     */
    @Override
    public void validate() {
        for (UserDefinedInstanceProperty sleeperProperty : UserDefinedInstanceProperty.getAll()) {
            if (!sleeperProperty.validationPredicate().test(get(sleeperProperty))) {
                throw new IllegalArgumentException("sleeper property: " + sleeperProperty.getPropertyName() + " is invalid");
            }
        }
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tagsMap) {
        tags.clear();
        tags.putAll(tagsMap);
        set(TAGS, tagsToString(tags));
    }

    public void loadTags(Properties tagsProperties) {
        tags.clear();
        tagsProperties.stringPropertyNames().forEach(tagName ->
                tags.put(tagName, tagsProperties.getProperty(tagName)));
        if (!tags.isEmpty()) {
            set(TAGS, tagsToString(tags));
        }
    }

    public Properties getTagsProperties() {
        Properties tagsProperties = new Properties();
        tags.forEach(tagsProperties::setProperty);
        return tagsProperties;
    }

    public String getTagsPropertiesAsString() throws IOException {
        StringWriter stringWriter = new StringWriter();
        Properties tagsProperties = getTagsProperties();
        tagsProperties.store(stringWriter, "");
        return stringWriter.toString();
    }

    public static String getConfigBucketFromInstanceId(String instanceId) {
        return String.join("-", "sleeper", instanceId, "config").toLowerCase(Locale.ROOT);
    }

    public void loadFromS3GivenInstanceId(AmazonS3 s3Client, String instanceId) throws IOException {
        String configBucket = getConfigBucketFromInstanceId(instanceId);
        loadFromS3(s3Client, configBucket);
    }

    public void loadFromS3(AmazonS3 s3Client, String bucket) throws IOException {
        super.loadFromS3(s3Client, bucket, S3_INSTANCE_PROPERTIES_FILE);
    }

    public void saveToS3(AmazonS3 s3Client) throws IOException {
        super.saveToS3(s3Client, get(CONFIG_BUCKET), S3_INSTANCE_PROPERTIES_FILE);
        LOGGER.info("Saved instance properties to bucket {}, key {}", get(CONFIG_BUCKET), S3_INSTANCE_PROPERTIES_FILE);
    }

    @Override
    protected SleeperPropertyIndex<InstanceProperty> getIndex() {
        return InstanceProperty.Index.INSTANCE;
    }

    public static Map<String, String> csvTagsToMap(String csvTags) {
        Map<String, String> tags = new HashMap<>();
        if (null != csvTags && !csvTags.isEmpty()) {
            String[] split = csvTags.split(",");
            for (int i = 0; i < split.length; i += 2) {
                tags.put(split[i], split[i + 1]);
            }
        }
        return tags;
    }

    public static String tagsToString(Map<String, String> tags) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            if (count > 0) {
                builder.append(",");
            }
            builder.append(entry.getKey());
            builder.append(",");
            builder.append(entry.getValue());
            count++;
        }
        return builder.toString();
    }
}
