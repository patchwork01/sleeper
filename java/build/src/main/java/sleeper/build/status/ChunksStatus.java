/*
 * Copyright 2022 Crown Copyright
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
package sleeper.build.status;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ChunksStatus {

    private final GitHubHead head;
    private final List<ChunkStatus> chunks;

    private ChunksStatus(Builder builder) {
        head = Objects.requireNonNull(builder.head, "head must not be null");
        chunks = Objects.requireNonNull(builder.chunks, "chunks must not be null");
    }

    public boolean isFailCheck() {
        return chunks.stream().anyMatch(chunk -> chunk.isFailCheckWithHead(head));
    }

    public void report(PrintStream out) {
        chunks.forEach(c -> c.report(head, out));
    }

    public List<String> reportLines() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        report(new PrintStream(os));
        return Arrays.asList(os.toString().split(System.lineSeparator()));
    }

    public static ChunksStatus chunksForHead(GitHubHead head, ChunkStatus... chunks) {
        return builder().head(head).chunks(Arrays.asList(chunks)).build();
    }

    public static ChunksStatus chunksForHead(GitHubHead head, List<ChunkStatus> chunks) {
        return builder().head(head).chunks(chunks).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ChunksStatus that = (ChunksStatus) o;
        return chunks.equals(that.chunks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunks);
    }

    @Override
    public String toString() {
        return "ChunksStatus{" +
                "chunks=" + chunks +
                '}';
    }

    public static final class Builder {
        private List<ChunkStatus> chunks;
        private GitHubHead head;

        private Builder() {
        }

        public Builder chunks(List<ChunkStatus> chunks) {
            this.chunks = chunks;
            return this;
        }

        public Builder head(GitHubHead head) {
            this.head = head;
            return this;
        }

        public ChunksStatus build() {
            return new ChunksStatus(this);
        }
    }
}
