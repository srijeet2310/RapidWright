/*
 * Copyright (c) 2022, Advanced Micro Devices, Inc.
 * All rights reserved.
 *
 * Author: Chris Lavin, Xilinx Research Labs.
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

package com.xilinx.rapidwright.rwroute;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.device.Node;

public class TestRouterHelper {

	@Test
	public void testProjectOutputPins() {
		Design d = new Design("test", "xcvu19p-fsva3824-1-e");

		String[] testSites = { "SLICE_X0Y1199", "SLICE_X1Y1199" };
		for (String siteName : testSites) {
			SiteInst si = d.createSiteInst(siteName);
			for (String pinName : si.getSitePinNames()) {
				SitePinInst pin = new SitePinInst(pinName, si);
				// Only test output pins to project
				if (!pin.isOutPin() || pin.getName().equals("COUT")) {
					continue;
				}

				Node intNode = RouterHelper.projectOutputPinToINTNode(pin);
				Assertions.assertNotNull(intNode);
			}
		}
		
		SiteInst si = d.createSiteInst(d.getDevice().getSite("BITSLICE_RX_TX_X1Y78"));
		SitePinInst p = new SitePinInst("TX_T_OUT", si);
		Node intNode = RouterHelper.projectOutputPinToINTNode(p);
		Assertions.assertNotNull(intNode);
		Assertions.assertEquals(intNode.toString(), "INT_INTF_L_CMT_X182Y90/LOGIC_OUTS_R19");
	}
}
