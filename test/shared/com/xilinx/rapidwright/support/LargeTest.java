/*
 * Copyright (c) 2022, Xilinx, Inc.
 * Copyright (c) 2022, Advanced Micro Devices, Inc.
 * All rights reserved.
 *
 * Author: Jakob Wenzel, Xilinx Research Labs.
 *
 * This file is part of RapidWright.
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
 *
 */

package com.xilinx.rapidwright.support;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Tag;

/**
 * Marker Annotation to indicate that a test is resource heavy.
 */
@Retention(RetentionPolicy.RUNTIME)
@Tag(LargeTest.LARGE_TEST)
public @interface LargeTest {
    String LARGE_TEST = "LARGE_TEST";
    int DEFAULT_MAX_MEMORY_GB = 5;

    int max_memory_gb() default LargeTest.DEFAULT_MAX_MEMORY_GB;
}
