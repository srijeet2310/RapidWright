/* 
 * Copyright (c) 2022 Xilinx, Inc. 
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
 
package com.xilinx.rapidwright.edif;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Optimize memory footprint of loaded EDIF by deduplicating equal strings
 */
public class NameUniquifier {

    private final Map<String,String> stringPool;

    private NameUniquifier(Map<String, String> stringPool) {
        this.stringPool = stringPool;
    }

    public static NameUniquifier concurrentUniquifier() {
        return new NameUniquifier(new ConcurrentHashMap<>());
    }

    public static NameUniquifier singleThreadedUniquifier() {
        return new NameUniquifier(new HashMap<>());
    }

    public String uniquifyName(String tmpName) {
        return stringPool.computeIfAbsent(tmpName, Function.identity());
    }

}
