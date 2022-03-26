/*
 *
 * Copyright (c) 2021 Ghent University.
 * All rights reserved.
 *
 * Author: Yun Zhou, Ghent University.
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

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.device.Node;
import com.xilinx.rapidwright.device.PIP;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.SitePin;

/**
 * TODO
 */
public class ECORouter extends PartialRouter {
    public ECORouter(Design design, RWRouteConfig config){
        super(design, config);

        // FIXME
        if (config.isTimingDriven()) {
            timingManager.setTimingRequirementPs(100 * 1000);
        }
    }

    @Override
    protected void routeGlobalClkNets() {
        if (clkNets.isEmpty()) {
            return;
        }

        throw new RuntimeException("ERROR: PartialECORouter does not support clock routing.");
    }

    @Override
    protected void routeStaticNets() {
        if (staticNetAndRoutingTargets.isEmpty()) {
            return;
        }

        throw new RuntimeException("ERROR: PartialECORouter does not support static net routing.");
    }

    @Override
    protected void determineRoutingTargets(){
        super.determineRoutingTargets();

        for (Map.Entry<Net,NetWrapper> e : nets.entrySet()) {
            Net net = e.getKey();
            NetWrapper netWrapper = e.getValue();

            // Create all nodes used by this net and set its previous pointer so that:
            // (a) the routing for each connection can be recovered by
            //      finishRouteConnection()
            // (b) Routable.setChildren() will know to only allow this incoming
            //     arc on these nodes
            for (PIP pip : net.getPIPs()) {
                Node start = (pip.isReversed()) ? pip.getEndNode() : pip.getStartNode();
                Node end = (pip.isReversed()) ? pip.getStartNode() : pip.getEndNode();
                Routable rstart = createAddRoutableNode(null, start, RoutableType.WIRE);
                Routable rend = createAddRoutableNode(null, end, RoutableType.WIRE);
                assert (rend.getPrev() == null);
                rend.setPrev(rstart);
            }

            for (Connection connection : netWrapper.getConnections()) {
                finishRouteConnection(connection);

                // if (connection.getSink().isRouted())
                //     continue;
                //
                // SitePinInst sink = connection.getSink();
                // String sinkPinName = sink.getName();
                // if (!Pattern.matches("[A-H](X|_I)", sinkPinName))
                //     continue;
                //
                // Routable rnode = connection.getSinkRnode();
                // String lut = sinkPinName.substring(0, 1);
                // Site site = sink.getSite();
                // for (int i = 6; i >= 1; i--) {
                //     Node altNode = site.getConnectedNode(lut + i);
                //
                //     // Skip if LUT pin is already being preserved
                //     Net preservedNet = routingGraph.getPreservedNet(altNode);
                //     if (preservedNet != null) {
                //         continue;
                //     }
                //
                //     RoutableType type = RoutableType.WIRE;
                //     Routable altRnode = createAddRoutableNode(null, altNode, type);
                //     // Trigger a setChildren() for LUT routethrus
                //     altRnode.getChildren();
                //     // Create a fake edge from [A-H][1-6] to [A-H](I|_X)
                //     altRnode.addChild(rnode);
                // }
            }
        }
    }

    @Override
    protected void assignNodesToConnections() {
        // for(Map.Entry<Net,NetWrapper> e : nets.entrySet()) {
        //     NetWrapper netWrapper = e.getValue();
        //     for (Connection connection : netWrapper.getConnections()) {
        //         SitePinInst sink = connection.getSink();
        //         List<Routable> rnodes = connection.getRnodes();
        //         String sinkPinName = sink.getName();
        //         if (rnodes.size() >= 2 && Pattern.matches("[A-H](X|_I)", sinkPinName)) {
        //             Routable prevRnode = rnodes.get(1);
        //             Node prevNode = (prevRnode != null) ? prevRnode.getNode() : null;
        //             SitePin prevPin = (prevNode != null) ? prevNode.getSitePin() : null;
        //             if (prevPin != null && Pattern.matches("[A-H][1-6]", prevPin.getPinName())) {
        //                 // Drop the fake LUT -> X/I pin edge
        //                 connection.setRnodes(rnodes.subList(1, rnodes.size()));
        //                 // TODO: Update site routing
        //                 System.out.println(prevPin.getPinName() + " -> " + sinkPinName + " for " + connection.getNetWrapper().getNet());
        //             }
        //         }
        //     }
        // }

        super.assignNodesToConnections();
    }

    public static Design routeDesign(Design design) {
        RWRouteConfig config = new RWRouteConfig(new String[] {
                "--partialRouting",
                "--fixBoundingBox",
                "--nonTimingDriven",
                "--verbose"});
        return routeDesign(design, config, () -> new ECORouter(design, config));
    }

}
