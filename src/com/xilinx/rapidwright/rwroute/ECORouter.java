/*
 *
 * Copyright (c) 2021 Xilinx, Inc.
 * All rights reserved.
 *
 * Author: Eddie Hung, Xilinx Research Labs.
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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.xilinx.rapidwright.design.Cell;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.device.BELClass;
import com.xilinx.rapidwright.device.BELPin;
import com.xilinx.rapidwright.device.IntentCode;
import com.xilinx.rapidwright.device.Node;
import com.xilinx.rapidwright.device.PIP;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.SitePIP;
import com.xilinx.rapidwright.device.SitePin;
import com.xilinx.rapidwright.device.SiteTypeEnum;
import com.xilinx.rapidwright.device.TileTypeEnum;
import com.xilinx.rapidwright.timing.delayestimator.DelayEstimatorBase;
import com.xilinx.rapidwright.timing.delayestimator.InterconnectInfo;
import com.xilinx.rapidwright.util.RuntimeTracker;
import com.xilinx.rapidwright.util.Utils;

/**
 * TODO
 */
public class ECORouter extends PartialRouter {

    protected class RouteNodeGraphECO extends RouteNodeGraphPartial {

        @Override
        protected boolean isExcluded(Node parent, Node child) {
            if (allowLutRoutethru(parent, child)) {
                return false;
            }

            return super.isExcluded(parent, child);
        }

        public RouteNodeGraphECO(RuntimeTracker setChildrenTimer, Design design) {
            super(setChildrenTimer, design);
        }
    }

    protected class RouteNodeGraphECOTimingDriven extends RouteNodeGraphPartialTimingDriven {

        @Override
        protected boolean isExcluded(Node parent, Node child) {
            if (allowLutRoutethru(parent, child)) {
                return false;
            }

            return super.isExcluded(parent, child);
        }

        public RouteNodeGraphECOTimingDriven(RuntimeTracker rnodesTimer, Design design, DelayEstimatorBase delayEstimator, boolean maskNodesCrossRCLK) {
            super(rnodesTimer, design, delayEstimator, maskNodesCrossRCLK);
        }
    }

    protected boolean allowLutRoutethru(Node parent, Node child) {
        if (parent.getIntentCode() != IntentCode.NODE_PINFEED)
            return false;

        if (child.getIntentCode() != IntentCode.NODE_CLE_OUTPUT)
            return false;

        TileTypeEnum parentTileType = parent.getTile().getTileTypeEnum();
        assert(parentTileType == TileTypeEnum.INT);

        TileTypeEnum childTileType = child.getTile().getTileTypeEnum();
        assert(Utils.isCLB(childTileType));

        SitePin sp = parent.getSitePin();
        Site s = sp.getSite();
        SiteTypeEnum siteType = s.getSiteTypeEnum();
        assert(Utils.isSLICE(siteType));
        String pinName = sp.getPinName();
        if (pinName.length() != 2)
            return false;

        char first = pinName.charAt(0);
        assert(first >= 'A' && first <= 'H');

        // Only consider [A-H]_O routethrus to avoid the complication of
        // having both [A-H]_O and [A-H]MUX occurring simultaneously
        // TODO: Accept?_O and/or ?MUX routethru with the same net by
        //       creating a fake ?_O -> ?MUX edge
        // TODO: Use a Set<TileTypeEnum, Set<Integer>> instead of
        //       string matching
        String childWireName = child.getWireName();
        if (!childWireName.endsWith("_O"))
            return false;

        char second = pinName.charAt(1);
        assert(second >= '1' && second <= '6');

        SiteInst si = design.getSiteInstFromSite(s);
        // Nothing placed at site, all routethrus possible
        if (si == null)
            return true;

        // O6 already used by something else
        boolean O6used = si.getNetFromSiteWire(first + "_O") != null;
        if (O6used)
            return false;

        // O5 already used by something else
        boolean O5used = si.getNetFromSiteWire(first + "5LUT_O5") != null;
        if (O5used)
            return false;

        // Routethru allowed
        return true;
    }

    public ECORouter(Design design, RWRouteConfig config, Collection<SitePinInst> pinsToRoute, float timingRequirementNs) {
        super(design, config, pinsToRoute);

        if (timingRequirementNs > 0) {
            assert(config.isTimingDriven());
            timingManager.setTimingRequirementPs(timingRequirementNs * 1000);
        }
    }

    public ECORouter(Design design, RWRouteConfig config, Collection<SitePinInst> pinsToRoute) {
        this(design, config, pinsToRoute, 0);
    }

    @Override
    protected RouteNodeGraph createRouteNodeGraph() {
        if(config.isTimingDriven()) {
            /* An instantiated delay estimator that is used to calculate delay of routing resources */
            DelayEstimatorBase estimator = new DelayEstimatorBase(design.getDevice(), new InterconnectInfo(), config.isUseUTurnNodes(), 0);
            return new RouteNodeGraphECOTimingDriven(rnodesTimer, design, estimator, config.isMaskNodesCrossRCLK());
        } else {
            return new RouteNodeGraphECO(rnodesTimer, design);
        }
    }

    @Override
    protected void routeGlobalClkNets() {
        if (clkNets.isEmpty()) {
            return;
        }

        throw new RuntimeException("ERROR: ECORouter does not support clock routing.");
    }

    @Override
    protected void routeStaticNets() {
        if (staticNetAndRoutingTargets.isEmpty()) {
            return;
        }

        throw new RuntimeException("ERROR: ECORouter does not support static net routing.");
    }

    @Override
    protected void determineRoutingTargets(){
        super.determineRoutingTargets();

        // FIXME: The following code is Sashimi-specific.
        // As a post-processing step once all nets-to-route have been determined,
        // it looks for sinks that are direct inputs ([A-H]_X or [A-H]_I) and,
        // assuming that they target either of the two available FFs, performs
        // some routing graph manipulation to allow them to be reached through
        // the LUT instead
        for (Map.Entry<?,NetWrapper> e : nets.entrySet()) {
            NetWrapper netWrapper = e.getValue();

            for (Connection connection : netWrapper.getConnections()) {
                if (connection.getSink().isRouted())
                    continue;

                // If the sink pin is a direct input, consider allowing it to use the LUT input
                SitePinInst sink = connection.getSink();
                String sinkPinName = sink.getName();
                if (!Pattern.matches("[A-H](X|_I)", sinkPinName))
                    continue;

                // TODO: Check that there is only connectivity to either or both FFs

                RouteNode rnode = connection.getSinkRnode();
                String lut = sinkPinName.substring(0, 1);
                Site site = sink.getSite();
                SiteInst siteInst = sink.getSiteInst();
                String outputPinName = lut + "_O";

                boolean O6used = siteInst.getNetFromSiteWire(outputPinName) != null;
                if (O6used) {
                    // TODO: If O6 unavailable, consider using O5 (would also require
                    //       A6 to be tied to VCC)
                    continue;
                }

                // Check if LUT is used for constant generation by examining output
                // pin (since SiteInst.getNetFromSiteWire() will not show this)
                Node outputPinNode = site.getConnectedNode(outputPinName);
                if (routingGraph.isPreserved(outputPinNode)) {
                    // TODO: Since it is used for constant generation, could move it elsewhere
                    continue;
                }

                RouteNodeType type = RouteNodeType.WIRE;
                RouteNode outputPinRnode = getOrCreateRouteNode(outputPinNode, type);
                // Pre-emptively trigger a setChildren()
                outputPinRnode.getChildren();
                // Create a fake edge from [A-H]_O to target [A-H](I|_X)
                outputPinRnode.addChild(rnode);
            }
        }
    }

    // Adapted from DesignTools.getConnectedCells()
    public static Set<BELPin> getConnectedBELPins(SitePinInst pin){
        HashSet<BELPin> pins = new HashSet<>();
        SiteInst si = pin.getSiteInst();
        if(si == null) return pins;
        for(BELPin p : pin.getBELPin().getSiteConns()){
            if(p.getBEL().getBELClass() == BELClass.RBEL){
                SitePIP pip = si.getUsedSitePIP(p.getBELName());
                if(pip == null) continue;
                if(p.isOutput()){
                    p = pip.getInputPin().getSiteConns().get(0);
                    Cell c = si.getCell(p.getBELName());
                    if(c != null) pins.add(p);
                }else{
                    for(BELPin snk : pip.getOutputPin().getSiteConns()){
                        Cell c = si.getCell(snk.getBELName());
                        if(c != null) pins.add(snk);
                    }
                }
            }else{
                Cell c = si.getCell(p.getBELName());
                if(c != null && c.getLogicalPinMapping(p.getName()) != null) {
                    pins.add(p);
                }
            }
        }
        return pins;
    }

    @Override
    protected void assignNodesToConnections() {
        // FIXME: The following code is Sashimi-specific.
        // As a post-processing step once all nets have been routed,
        // it looks for sinks that finish at the direct inputs ([A-H]_X or [A-H]_I),
        // and are preceded by the LUT output. If this occurs, the fake edge that
        // we inserted during determineRoutingTargets() was taken thus we need to
        // remove this and update the intra-site routing
        for(Connection connection : indirectConnections) {
            SitePinInst sink = connection.getSink();
            List<RouteNode> rnodes = connection.getRnodes();
            String sinkPinName = sink.getName();
            if (rnodes.size() >= 3 && Pattern.matches("[A-H](X|_I)", sinkPinName)) {
                if (Pattern.matches(".+[A-H]_O", rnodes.get(1).getNode().toString())) {
                    RouteNode lutRnode = rnodes.get(2);
                    Node lutNode = lutRnode.getNode();
                    SitePin lutPin = lutNode.getSitePin();

                    assert(Pattern.matches("[A-H][1-6]", lutPin.getPinName()));

                    // Drop the fake LUT input -> LUT output -> X/I pin edges
                    connection.setRnodes(rnodes.subList(2, rnodes.size()));

                    // Fix the intra-site routing
                    SiteInst si = sink.getSiteInst();
                    Net net = connection.getNetWrapper().getNet();
                    for (BELPin sinkBELPin : getConnectedBELPins(sink)) {
                        boolean r = si.unrouteIntraSiteNet(sink.getBELPin(), sinkBELPin);
                        assert(r);
                        r = si.routeIntraSiteNet(net, lutPin.getBELPin(), sinkBELPin);
                        assert(r);
                        assert(design.getModifiedSiteInsts().contains(si));
                    }

                    // System.out.println(lutPin.getPinName() + " -> " + sinkPinName + " for " + connection.getNetWrapper().getNet());
                }
            }
        }

        super.assignNodesToConnections();
    }

    @Override
    protected boolean handleUnroutableConnection(Connection connection) {
        if(config.isEnlargeBoundingBox()) {
            connection.enlargeBoundingBox(config.getExtensionXIncrement(), config.getExtensionYIncrement());
        }
        if (routeIteration > 1) {
            // TODO: Is this the best condition for unpreserving,
            //  since expanding the bounding box typically means more
            //  rnodes?
            if (rnodesCreatedThisIteration == 0) {
                unpreserveNetsAndReleaseResources(connection);
                return true;
            }
        }
        return super.handleUnroutableConnection(connection);
    }

    @Override
    protected Collection<Net> pickNetsToUnpreserve(Connection connection) {
        Set<Net> unpreserveNets = new HashSet<>();

        // Find those preserved nets that are using downhill nodes of the source pin node
        for(Node node : connection.getSourceRnode().getNode().getAllDownhillNodes()) {
            Net toRoute = routingGraph.getPreservedNet(node);
            if(toRoute == null) continue;
            if(toRoute.isClockNet() || toRoute.isStaticNet()) continue;
            unpreserveNets.add(toRoute);
        }

        unpreserveNets.removeIf((net) -> {
            NetWrapper netWrapper = nets.get(net);
            if (netWrapper == null)
                return false;
            if (partiallyPreservedNets.contains(netWrapper))
                return false;
            // Net already seen and is fully unpreserved
            return true;
        });

        return unpreserveNets;
    }

    // @Override
    // protected boolean handleCongestedConnection(Connection connection) {
    //     super.handleCongestedConnection(connection);
    //
    //     if (routeIteration > 1) {
    //         if (rnodesCreatedThisIteration == 0) {
    //             // NetWrapper netWrapper = connection.getNetWrapper();
    //             // if (netWrapper.getPartiallyPreserved()) {
    //             //     Net net = netWrapper.getNet();
    //             //     System.out.println("INFO: Unpreserving rest of '" + net + "' due to congestion");
    //             //     unpreserveNet(net);
    //             //     return true;
    //             // }
    //             //
    //             // return false;
    //
    //             Set<Tile> overUsedTiles = new HashSet<>();
    //             for(RouteNode rn : connection.getRnodes()){
    //                 if(rn.isOverUsed()) {
    //                     overUsedTiles.add(rn.getNode().getTile());
    //                 }
    //             }
    //
    //             Set<Net> unpreserveNets = new HashSet<>();
    //             for (Tile tile : overUsedTiles) {
    //                 for (int wire = 0; wire < tile.getWireCount(); wire++) {
    //                     Node node = Node.getNode(tile, wire);
    //                     Net net = routingGraph.getPreservedNet(node);
    //                     if (net == null)
    //                         continue;
    //                     if (net.isClockNet() || net.isStaticNet())
    //                         continue;
    //                     NetWrapper netWrapper = nets.get(net);
    //                     if (netWrapper != null && !netWrapper.getPartiallyPreserved())
    //                         continue;
    //
    //                     unpreserveNets.add(net);
    //                 }
    //             }
    //
    //             if (!unpreserveNets.isEmpty()) {
    //                 System.out.println("INFO: Unpreserving " + unpreserveNets.size() + " nets in vicinity of congestion");
    //                 for (Net net : unpreserveNets) {
    //                     System.out.println("\t" + net);
    //                     unpreserveNet(net);
    //                 }
    //                 return true;
    //             }
    //         }
    //     }
    //     return false;
    // }

    @Override
    protected void setPIPsOfNets(){
        for(Map.Entry<Net,NetWrapper> e : nets.entrySet()){
            NetWrapper netWrapper = e.getValue();
            Net net = netWrapper.getNet();
            Set<PIP> oldPIPs = new HashSet<>(net.getPIPs());
            Set<PIP> newPIPs = new HashSet<>();
            for(Connection connection:netWrapper.getConnections()){
                newPIPs.addAll(RouterHelper.getConnectionPIPs(connection));
            }

            // Skip if new and old PIPs are completely identical
            // (meaning net was unpreserved but never re-routed any differently)
            if (oldPIPs.equals(newPIPs))
                continue;

            net.setPIPs(newPIPs);

            // oldPIPs.removeAll(newPIPs);
            // if (!oldPIPs.isEmpty()) {
            //     System.out.println("PIP delta for '" + net + "':");
            //     for (PIP pip : oldPIPs) {
            //         System.out.println("\t- " + pip);
            //     }
            // }
        }

        // Disabled for runtime reasons
        // TODO: What's the value of checking PIPs? Surely checking nodes more useful?
        // checkPIPsUsage();
    }

    public static Design routeDesignNonTimingDriven(Design design, Collection<SitePinInst> pinsToRoute) {
        RWRouteConfig config = new RWRouteConfig(new String[] {
                "--enlargeBoundingBox", // Necessary to ensure that we can reach Laguna columns
                // use U-turn nodes and no masking of nodes cross RCLK
                // Pros: maximum routability
                // Con: might result in delay optimism and a slight increase in runtime
                "--useUTurnNodes",
                "--nonTimingDriven",
                "--verbose"});
        return routeDesign(design, new ECORouter(design, config, pinsToRoute));
    }

    public static Design routeDesignTimingDriven(Design design, Collection<SitePinInst> pinsToRoute, float timingRequirementNs) {
        RWRouteConfig config = new RWRouteConfig(new String[] {
                "--enlargeBoundingBox", // Necessary to ensure that we can reach Laguna columns
                // use U-turn nodes and no masking of nodes cross RCLK
                // Pros: maximum routability
                // Con: might result in delay optimism and a slight increase in runtime
                "--useUTurnNodes",
                "--verbose"});
        return routeDesign(design, new ECORouter(design, config, pinsToRoute, timingRequirementNs));
    }

}
