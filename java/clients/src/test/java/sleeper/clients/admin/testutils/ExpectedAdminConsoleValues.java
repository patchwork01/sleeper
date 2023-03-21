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
package sleeper.clients.admin.testutils;

import static sleeper.console.ConsoleOutput.CLEAR_CONSOLE;

public class ExpectedAdminConsoleValues {

    private ExpectedAdminConsoleValues() {
    }

    public static final String MAIN_SCREEN = "\n" +
            "ADMINISTRATION COMMAND LINE CLIENT\n" +
            "----------------------------------\n" +
            "\n" +
            "Please select from the below options and hit return:\n" +
            "[0] Exit program\n" +
            "[1] View/edit instance configuration\n" +
            "[2] View/edit table configuration\n" +
            "[3] Print Sleeper table names\n" +
            "[4] Run partition status report\n" +
            "[5] Run files status report\n" +
            "[6] Run compaction status report\n" +
            "[7] Run ingest status report\n" +
            "\n" +
            "Input: \n";
    public static final String TABLE_SELECT_SCREEN = "\n" +
            "Which TABLE do you want to select?\n" +
            "\n" +
            "Please enter the TABLE NAME now or use the following options:\n" +
            "[0] Exit program\n" +
            "[1] Return to Main Menu\n" +
            "\n" +
            "Input: \n";
    public static final String PROPERTY_SAVE_CHANGES_SCREEN = "" +
            "Please select from the below options and hit return:\n" +
            "[0] Exit program\n" +
            "[1] Save changes\n" +
            "[2] Return to editor\n" +
            "[3] Discard changes and return to main menu\n" +
            "\n" +
            "Input: \n";
    public static final String PROPERTY_VALIDATION_SCREEN = "" +
            "Please select from the below options and hit return:\n" +
            "[0] Exit program\n" +
            "[1] Return to editor\n" +
            "[2] Discard changes and return to main menu\n" +
            "\n" +
            "Input: \n";

    public static final String NO_INSTANCE_SCREEN = "" +
            "Could not load properties for instance test-instance\n";

    public static final String EXIT_OPTION = "0";
    public static final String RETURN_TO_MAIN_SCREEN_OPTION = "1";
    public static final String INSTANCE_CONFIGURATION_OPTION = "1";
    public static final String TABLE_CONFIGURATION_OPTION = "2";
    public static final String TABLE_NAMES_REPORT_OPTION = "3";
    public static final String PARTITION_STATUS_REPORT_OPTION = "4";
    public static final String FILES_STATUS_REPORT_OPTION = "5";
    public static final String COMPACTION_STATUS_REPORT_OPTION = "6";
    public static final String INGEST_STATUS_REPORT_OPTION = "7";
    public static final String COMPACTION_JOB_STATUS_REPORT_OPTION = "1";
    public static final String COMPACTION_TASK_STATUS_REPORT_OPTION = "2";

    public static final String JOB_QUERY_ALL_OPTION = "1";
    public static final String JOB_QUERY_UNKNOWN_OPTION = "2";
    public static final String JOB_QUERY_DETAILED_OPTION = "3";
    public static final String JOB_QUERY_RANGE_OPTION = "4";

    public static final String TASK_QUERY_ALL_OPTION = "1";
    public static final String TASK_QUERY_UNFINISHED_OPTION = "2";
    public static final String INGEST_JOB_STATUS_REPORT_OPTION = "1";
    public static final String PROMPT_INPUT_NOT_RECOGNISED = "\nInput not recognised please try again\n";

    public static final class SaveChangesScreen {
        public static final String SAVE_CHANGES_OPTION = "1";
        public static final String RETURN_TO_EDITOR_OPTION = "2";
        public static final String DISCARD_CHANGES_OPTION = "3";
    }

    public static final class ValidateChangesScreen {
        public static final String RETURN_TO_EDITOR_OPTION = "1";
        public static final String DISCARD_CHANGES_OPTION = "2";
    }

    public static final String PROMPT_RETURN_TO_MAIN = "" +
            "\n\n----------------------------------\n" +
            "Hit enter to return to main screen\n";

    public static final String PROMPT_SAVE_SUCCESSFUL_RETURN_TO_MAIN = "" +
            "\n\n----------------------------------\n" +
            "Saved successfully, hit enter to return to main screen\n";

    public static final String DISPLAY_MAIN_SCREEN = CLEAR_CONSOLE + MAIN_SCREEN;
}
