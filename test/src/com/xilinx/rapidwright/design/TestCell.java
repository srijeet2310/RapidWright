package com.xilinx.rapidwright.design;

import com.xilinx.rapidwright.device.BELPin;
import com.xilinx.rapidwright.device.Device;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class TestCell {
    @Test
    void testGetSitePinFromLogicalPin() {
        Design design = new Design("top", Device.PYNQ_Z1);
        SiteInst si = design.createSiteInst("SLICE_X1Y0");
        Net net = design.createNet("net");
        SitePinInst A3 = net.createPin("A3", si);
        SitePinInst AX = net.createPin("AX", si);

        Cell ff = design.createAndPlaceCell("ff", Unisim.FDRE, "SLICE_X1Y0/AFF");

        BELPin ffD = ff.getBEL().getPin("D");
        for (SitePinInst spi : Arrays.asList(AX, A3)) {
            Assertions.assertNull(DesignTools.getRoutedSitePin(ff, net, "D"));

            BELPin bp = spi.getBELPin();
            Assertions.assertTrue(si.routeIntraSiteNet(net, bp, ffD));

            Assertions.assertEquals(bp.getName(), DesignTools.getRoutedSitePin(ff, net, "D"));
            Assertions.assertEquals(spi, ff.getSitePinFromLogicalPin("D", null));

            Assertions.assertTrue(si.unrouteIntraSiteNet(bp, ffD));
        }
    }
}
