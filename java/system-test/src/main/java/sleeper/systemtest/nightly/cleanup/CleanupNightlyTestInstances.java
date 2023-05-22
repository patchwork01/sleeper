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
package sleeper.systemtest.nightly.cleanup;

import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import sleeper.systemtest.nightly.output.NightlyTestSummaryTable;

public class CleanupNightlyTestInstances {

    private CleanupNightlyTestInstances() {
    }

    public static void main(String[] args) {
        NightlyTestSummaryTable.fromS3(AmazonS3ClientBuilder.defaultClient(), args[0]);
    }
}
