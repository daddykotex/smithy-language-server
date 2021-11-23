/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.smithy.lsp.ext;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import software.amazon.smithy.utils.ListUtils;
import software.amazon.smithy.utils.MapUtils;

public class SmithyProjectTest {

    final Path DUMMY_PATH = Paths.get("smithy-build.json");

    @Test
    public void walkingImports() throws Exception {
        List<String> imports = Arrays.asList("bla", "foo");
        Map<String, String> files = MapUtils.ofEntries(MapUtils.entry("test.smithy", "namespace testRoot"),
                MapUtils.entry("bar/test.smithy", "namespace testBar"),
                MapUtils.entry("foo/test.smithy", "namespace testFoo"),
                MapUtils.entry("bla/test.smithy", "namespace testBla"));

        try (Harness hs = Harness.create(SmithyBuildExtensions.builder().imports(imports).build(), files)) {
            File inFoo = hs.file("foo/test.smithy");
            File inBla = hs.file("bla/test.smithy");

            List<File> smithyFiles = hs.getProject().getSmithyFiles();

            assertEquals(ListUtils.of(inBla, inFoo), smithyFiles);
        }

    }
}
