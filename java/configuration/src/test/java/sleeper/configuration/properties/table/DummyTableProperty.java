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
package sleeper.configuration.properties.table;

import sleeper.configuration.properties.PropertyGroup;
import sleeper.configuration.properties.instance.SleeperProperty;

public class DummyTableProperty implements TableProperty {

    private final SleeperProperty defaultProperty;

    private DummyTableProperty(SleeperProperty defaultProperty) {
        this.defaultProperty = defaultProperty;
    }

    public static DummyTableProperty defaultedFrom(SleeperProperty defaultProperty) {
        return new DummyTableProperty(defaultProperty);
    }

    @Override
    public String getPropertyName() {
        return "made.up";
    }

    @Override
    public String getDefaultValue() {
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public PropertyGroup getPropertyGroup() {
        return TablePropertyGroup.COMPACTION;
    }

    @Override
    public boolean isRunCdkDeployWhenChanged() {
        return false;
    }

    @Override
    public SleeperProperty getDefaultProperty() {
        return defaultProperty;
    }
}
