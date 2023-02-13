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

package sleeper.clients.admin.deploy;

import com.amazonaws.services.s3.model.ListObjectsV2Result;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import sleeper.configuration.properties.InstanceProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.configuration.properties.UserDefinedInstanceProperty.JARS_BUCKET;

@Tag("SystemTest")
class SystemTestInstanceIT {

    @RegisterExtension
    private static final SystemTestInstance INSTANCE = new SystemTestInstance();

    @Test
    void shouldFindJars() {
        // Given
        InstanceProperties instanceProperties = INSTANCE.loadInstanceProperties();

        // When
        ListObjectsV2Result result = INSTANCE.getS3Client().listObjectsV2(instanceProperties.get(JARS_BUCKET));

        // When / Then
        assertThat(result.getObjectSummaries()).isNotEmpty();
    }
}
