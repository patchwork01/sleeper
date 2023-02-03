#!/bin/bash
# Copyright 2022-2023 Crown Copyright
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e

#####################
# Initial variables #
#####################

if [[ -z $1 ]]; then
	echo "Usage: $0 <instance-id>"
	exit 1
fi

INSTANCE_ID=$1

SCRIPTS_DIR=$(cd "$(dirname "$0")" && cd "../" && pwd)
GENERATED_DIR=${SCRIPTS_DIR}/generated

mkdir -p "${GENERATED_DIR}"
pushd "$GENERATED_DIR"

java -cp "${SCRIPTS_DIR}"/jars/clients-*-utility.jar sleeper.status.update.DownloadConfig "${INSTANCE_ID}"

popd
