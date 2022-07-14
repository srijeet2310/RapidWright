package com.xilinx.rapidwright.design;

import com.xilinx.rapidwright.device.BELPin;
import com.xilinx.rapidwright.device.Device;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

public class TestCell {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testGetSitePinFromLogicalPin(boolean createAX) {
        Design design = new Design("top", Device.PYNQ_Z1);
        SiteInst si = design.createSiteInst("SLICE_X1Y0");
        Net net = design.createNet("net");
        SitePinInst A3 = net.createPin("A3", si);
        SitePinInst AX = (createAX) ? net.createPin("AX", si) : null;

        Cell ff = design.createAndPlaceCell("ff", Unisim.FDRE, "SLICE_X1Y0/AFF");

        BELPin ffD = ff.getBEL().getPin("D");
        for (SitePinInst spi : Arrays.asList(AX, A3)) {
            if (spi == null)
                continue;

            Assertions.assertNull(DesignTools.getRoutedSitePin(ff, net, "D"));
            Assertions.assertNull(ff.getSitePinFromLogicalPin("D", null));

            BELPin bp = spi.getBELPin();
            Assertions.assertTrue(si.routeIntraSiteNet(net, bp, ffD));

            Assertions.assertEquals(bp.getName(), DesignTools.getRoutedSitePin(ff, net, "D"));
            Assertions.assertEquals(spi, ff.getSitePinFromLogicalPin("D", null));

            Assertions.assertTrue(si.unrouteIntraSiteNet(bp, ffD));
        }
    }
}
