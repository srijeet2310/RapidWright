/*
 * Copyright (c) 2022, Advanced Micro Devices, Inc.
 * All rights reserved.
 *
 * Author: Eddie Hung, Advanced Micro Devices, Inc.
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

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.support.RapidWrightDCP;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class TestEDIFHierPortInst {
    @ParameterizedTest
    @CsvSource({
            // Cell pin placed onto a D6LUT/O6 -- its net does exit the site
            "processor/address_loop[8].output_data.pc_vector_mux_lut/LUT6/O,D_O",
            // Cell pin placed onto a D5LUT/O5 -- its net does exit the site
            "processor/address_loop[8].output_data.pc_vector_mux_lut/LUT5/O,DMUX",

            // Cell pin placed onto a E6LUT/O6 -- its net does not exit the site
            "processor/stack_loop[4].upper_stack.stack_pointer_lut/LUT6/O,null",

            // Cell pin placed onto a D5LUT/O5 -- its net does not exit the site and
            // nothing is using DMUX
            "processor/stack_loop[3].upper_stack.stack_pointer_lut/LUT5/O,null",
            // Cell pin placed onto a E5LUT/O5 -- its net does not exit the site but
            // another net is using EMUX
            "processor/stack_loop[4].upper_stack.stack_pointer_lut/LUT5/O,null",

    })
    void testGetRoutedSitePinInst(String hierPortInstName, String expected) {
        Design d = RapidWrightDCP.loadDCP("picoblaze_ooc_X10Y235.dcp");
        EDIFNetlist netlist = d.getNetlist();
        EDIFHierPortInst ehpi = netlist.getHierPortInstFromName(hierPortInstName);
        SitePinInst spi = ehpi.getRoutedSitePinInst(d);
        Assertions.assertEquals(expected, spi == null ? "null" : spi.getName());
    }
}
