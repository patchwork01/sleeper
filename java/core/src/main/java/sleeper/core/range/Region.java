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
package sleeper.core.range;

import sleeper.core.key.Key;
import sleeper.core.schema.Schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A Region is a rectangular area in the space of row keys. This is created by
 * specifying a {@link Range} for each row key dimension. (If a range is not
 * specified for a dimension then it is implicitly assumed to cover the entire
 * space in that dimension.)
 */
public class Region {
    private final Map<String, Range> rowKeyFieldNameToRange;

    public Region(List<Range> ranges) {
        this.rowKeyFieldNameToRange = new HashMap<>();
        for (Range range : ranges) {
            if (this.rowKeyFieldNameToRange.containsKey(range.getFieldName())) {
                throw new IllegalArgumentException("Should only provide one range per row key field");
            }
            this.rowKeyFieldNameToRange.put(range.getFieldName(), range);
        }
    }

    public Region(Range range) {
        this(Collections.singletonList(range));
    }

    private Region(Map<String, Range> rowKeyFieldNameToRange) {
        this.rowKeyFieldNameToRange = rowKeyFieldNameToRange;
    }

    public Range getRange(String fieldName) {
        return rowKeyFieldNameToRange.get(fieldName);
    }

    /**
     * Returns the ranges in this region. Note that these are returned in no
     * particular order.
     *
     * @return a List of Ranges in no particular order.
     */
    public List<Range> getRanges() {
        return new ArrayList<>(rowKeyFieldNameToRange.values());
    }

    public boolean isKeyInRegion(Schema schema, Key key) {
        if (null == key || key.isEmpty()) {
            throw new IllegalArgumentException("Key must be non-null and not empty");
        }

        // Key is not in region if any dimension is not in the corresponding range.
        int i = 0;
        for (Object object : key.getKeys()) {
            String rowKeyFieldName = schema.getRowKeyFields().get(i).getName();
            if (rowKeyFieldNameToRange.containsKey(rowKeyFieldName)) {
                Range range = rowKeyFieldNameToRange.get(rowKeyFieldName);
                if (!range.doesRangeContainObject(object)) {
                    return false;
                }
            }
            i++;
        }

        return true;
    }

    public boolean doesRegionOverlap(Region otherRegion) {
        // Two regions overlap if they overlap in all dimensions
        for (Map.Entry<String, Range> entry : rowKeyFieldNameToRange.entrySet()) {
            Range otherRange = otherRegion.rowKeyFieldNameToRange.get(entry.getKey());
            if (null != otherRange) {
                if (!entry.getValue().doesRangeOverlap(otherRange)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Region childWithRange(Range range) {
        Map<String, Range> newRanges = new HashMap<>(rowKeyFieldNameToRange);
        newRanges.put(range.getFieldName(), range);
        return new Region(newRanges);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.rowKeyFieldNameToRange);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Region other = (Region) obj;
        if (!Objects.equals(this.rowKeyFieldNameToRange, other.rowKeyFieldNameToRange)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Region{rowKeyFieldNameToRange=" + rowKeyFieldNameToRange + '}';
    }
}
