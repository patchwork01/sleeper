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

package sleeper.systemtest.drivers.python;

import sleeper.clients.util.ClientUtils;
import sleeper.clients.util.CommandPipeline;
import sleeper.clients.util.CommandPipelineRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import static sleeper.clients.util.Command.command;
import static sleeper.clients.util.CommandPipeline.pipeline;

public class PythonRunner {
    private final CommandPipelineRunner pipelineRunner = ClientUtils::runCommandLogOutput;
    private final Path pythonDir;

    public PythonRunner(Path pythonDir) {
        this.pythonDir = pythonDir;
    }

    public void run(String... arguments) throws IOException, InterruptedException {
        pipelineRunner.runOrThrow(pythonPipeline(pythonDir, arguments));
    }

    private static CommandPipeline pythonPipeline(Path pythonDir, String... arguments) {
        return pipeline(command(Stream.concat(
                        Stream.of(pythonDir.resolve("env/bin/python").toString()),
                        Stream.of(arguments))
                .toArray(String[]::new)));
    }
}
