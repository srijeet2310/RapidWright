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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.device.Node;
import com.xilinx.rapidwright.device.PIP;
import com.xilinx.rapidwright.timing.ClkRouteTiming;
import com.xilinx.rapidwright.timing.TimingManager;
import com.xilinx.rapidwright.timing.delayestimator.DelayEstimatorBase;
import com.xilinx.rapidwright.timing.delayestimator.InterconnectInfo;
import com.xilinx.rapidwright.util.RuntimeTracker;

/**
 * A class extending {@link RWRoute} for partial routing.
 * In partial routing mode, nets that are already fully- or partially- routed
 * will be preserved and only the unrouted connections (as specified by the
 * pinsToRoute parameter in the constructor) are tackled.
 * Enabling soft preserve allows preserved routing that may be the cause of any
 * unroutable connections to be ripped up and re-routed.
 */
public class PartialRouter extends RWRoute{

	final protected boolean softPreserve;

	protected Set<NetWrapper> partiallyPreservedNets;

	protected Map<Net, List<SitePinInst>> netToPins;

	protected class RouteNodeGraphPartial extends RouteNodeGraph {

		public RouteNodeGraphPartial(RuntimeTracker setChildrenTimer, Design design) {
			super(setChildrenTimer, design);
		}

		@Override
		protected boolean mustInclude(Node parent, Node child) {
			return isPartOfExistingRoute(parent, child);
		}
	}

	protected class RouteNodeGraphPartialTimingDriven extends RouteNodeGraphTimingDriven {
		public RouteNodeGraphPartialTimingDriven(RuntimeTracker rnodesTimer, Design design, DelayEstimatorBase delayEstimator, boolean maskNodesCrossRCLK) {
			super(rnodesTimer, design, delayEstimator, maskNodesCrossRCLK);
		}

		@Override
		protected boolean mustInclude(Node parent, Node child) {
			return isPartOfExistingRoute(parent, child);
		}
	}

	public PartialRouter(Design design, RWRouteConfig config, Collection<SitePinInst> pinsToRoute, boolean softPreserve){
		super(design, config);
		this.softPreserve = softPreserve;
		partiallyPreservedNets = new HashSet<>();
		netToPins = pinsToRoute.stream()
				.filter((spi) -> !spi.isOutPin())
				.collect(Collectors.groupingBy(SitePinInst::getNet));
	}

	public PartialRouter(Design design, RWRouteConfig config, Collection<SitePinInst> pinsToRoute){
		this(design, config, pinsToRoute, false);
	}

	/**
	 * Checks whether this arc is part of an existing route.
	 * For Nets containing at least one Connection to be routed, all fully routed
	 * Connections and their associated Nodes (if any) are preserved. Any such
	 * Nodes can (and are encouraged) to be used as part of routing such incomplete
	 * Connections. In these cases, the RouteNode.prev member is used to restrict
	 * incoming arcs to just the RouteNode already used by the Net; this method
	 * detects this case and allows the preserved state to be masked.
	 * Note that this method must only be called once for each end Node, since
	 * RouteNode.prev (which is also used to track its "visited" state) is erased
	 * upon masking.
	 * @param start Start Node of arc.
	 * @param end End Node of arc.
	 * @return True if arc is part of an existing route.
	 */
	private boolean isPartOfExistingRoute(Node start, Node end) {
		if (!routingGraph.isPreserved(end))
			return false;

		// If preserved, check if end node has been created already
		RouteNode endRnode = routingGraph.getNode(end);
		if (endRnode == null)
			return false;

		// If so, get its prev pointer
		RouteNode prev = endRnode.getPrev();
		// Presence means that the only arc allowed to enter this end node
		// is if it came from prev
		if (prev != null) {
			assert((prev.getNode() == start) == prev.getNode().equals(start));
			if (prev.getNode() == start) {
				endRnode.setPrev(null);
				return true;
			}
		}

		return false;
	}

	@Override
	protected RouteNodeGraph createRouteNodeGraph() {
		if(config.isTimingDriven()) {
			/* An instantiated delay estimator that is used to calculate delay of routing resources */
			DelayEstimatorBase estimator = new DelayEstimatorBase(design.getDevice(), new InterconnectInfo(), config.isUseUTurnNodes(), 0);
			return new RouteNodeGraphPartialTimingDriven(rnodesTimer, design, estimator, config.isMaskNodesCrossRCLK());
		} else {
			return new RouteNodeGraphPartial(rnodesTimer, design);
		}
	}

	@Override
	protected TimingManager createTimingManager(ClkRouteTiming clkTiming, Collection<Net> timingNets) {
		final boolean isPartialRouting = true;
		return new TimingManager(design, routerTimer, config, clkTiming, timingNets, isPartialRouting);
	}

	@Override
	protected int getNumIndirectConnectionPins() {
		int totalSitePins = 0;
        for(Connection connection : indirectConnections) {
			totalSitePins += (connection.getSink().isRouted()) ? 0 : 1;
        }
        return totalSitePins;
	}

	@Override
	protected void determineRoutingTargets() {
		super.determineRoutingTargets();

		// Go through all nets to be routed
		for (Map.Entry<Net, NetWrapper> e : nets.entrySet()) {
			Net net = e.getKey();

			// Create all nodes used by this net and set its previous pointer so that:
			// (a) the routing for each connection can be recovered by
			//      finishRouteConnection()
			// (b) RouteNode.setChildren() will know to only allow this incoming
			//     arc on these nodes
			for (PIP pip : net.getPIPs()) {
				Node start = (pip.isReversed()) ? pip.getEndNode() : pip.getStartNode();
				Node end = (pip.isReversed()) ? pip.getStartNode() : pip.getEndNode();
				RouteNode rstart = getOrCreateRouteNode(start, RouteNodeType.WIRE);
				RouteNode rend = getOrCreateRouteNode(end, RouteNodeType.WIRE);
				assert (rend.getPrev() == null);
				rend.setPrev(rstart);
			}

			NetWrapper netWrapper = e.getValue();
			for (Connection connection : netWrapper.getConnections()) {
				if (connection.getSink().isRouted()) {
					finishRouteConnection(connection);
				}
			}
		}
	}

	@Override
	protected void addGlobalClkRoutingTargets(Net clk) {
		if(!clk.hasPIPs()) {
			super.addGlobalClkRoutingTargets(clk);
		}else {
			preserveNet(clk);
			increaseNumPreservedClks();
		}
	}
	
	@Override
	protected void addStaticNetRoutingTargets(Net staticNet){
		List<SitePinInst> sinks = staticNet.getSinkPins();
		if(sinks.size() > 0) {
			if(!staticNet.hasPIPs()) {
				List<Node> sinkNodes = new ArrayList<>(sinks.size());
				sinks.forEach((p) -> sinkNodes.add(p.getConnectedNode()));
				addPreservedNodes(sinkNodes, staticNet);
				addStaticNetRoutingTargets(staticNet, sinks);
			}else {
				preserveNet(staticNet);
				increaseNumPreservedStaticNets();
			}	
			
		}else {// internally routed (sinks.size = 0)
			preserveNet(staticNet);
			increaseNumNotNeedingRouting();
		}
	}
	
	@Override
	protected void addNetConnectionToRoutingTargets(Net net) {
		List<SitePinInst> sinkPins = net.getSinkPins();
		List<SitePinInst> pinsToRoute = netToPins.get(net);
		final boolean partiallyPreserved = (pinsToRoute != null && pinsToRoute.size() < sinkPins.size());
		if (pinsToRoute != null) {
			assert(!pinsToRoute.isEmpty());

			if (partiallyPreserved) {
				// Mark all pins as being routed, then unmark those that need routing
				sinkPins.forEach((spi) -> spi.setRouted(true));
			}
			pinsToRoute.forEach((spi) -> spi.setRouted(false));
		}

		if (net.hasPIPs()) {
			// NOTE: SitePinInst.isRouted() must be finalized before this method is
			//       called as it may operate asynchronously
			preserveNet(net);
			increaseNumPreservedWireNets();
		}

		if (pinsToRoute == null) {
			return;
		}

		NetWrapper netWrapper = createNetWrapperAndConnections(net);
		if (partiallyPreserved) {
			partiallyPreservedNets.add(netWrapper);
		}
	}

	/**
	 * Return preserved nets that are using resources immediately downhill of the source and
	 * immediately uphill of the sink of the connection.
	 * @param connection The connection in question.
	 * @return Collection of nets.
	 */
	protected Collection<Net> pickNetsToUnpreserve(Connection connection) {
		Set<Net> unpreserveNets = new HashSet<>();

		// Find those reserved signals that are using uphill nodes of the target pin node
		for(Node node : connection.getSinkRnode().getNode().getAllUphillNodes()) {
			Net toRoute = routingGraph.getPreservedNet(node);
			if(toRoute == null) continue;
			if(toRoute.isClockNet() || toRoute.isStaticNet()) continue;
			unpreserveNets.add(toRoute);
		}

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

	/**
	 * Unpreserves nets to release routing resource to resolve congestion that blocks the
	 * routablity of a connection.
	 * The {@link #pickNetsToUnpreserve} method is called to get which nets are to be
	 * unpreserved and its resources released for consideration by others.
	 * @param connection The connection in question.
	 * @return The number of unrouted nets.
	 */
	protected int unpreserveNetsAndReleaseResources(Connection connection) {
		Collection<Net> unpreserveNets = pickNetsToUnpreserve(connection);
		if (unpreserveNets.isEmpty()) {
			return 0;
		}

		System.out.println("INFO: Unpreserving " + unpreserveNets.size() + " nets due to unroutable connection");
		for (Net net : unpreserveNets) {
			System.out.println("\t" + net);
			unpreserveNet(net);
		}

		return unpreserveNets.size();
	}

	protected void unpreserveNet(Net net) {
		Set<RouteNode> rnodes = new HashSet<>();
		NetWrapper netWrapper = nets.get(net);
		if (netWrapper != null) {
			// Net already exists -- any unrouted connection will cause the
			// net to exist, but already routed connections may still be preserved

			boolean removed = partiallyPreservedNets.remove(netWrapper);
			assert(removed);

			// Collect all nodes used by this net
			for (PIP pip : net.getPIPs()) {
				Node start = (pip.isReversed()) ? pip.getEndNode() : pip.getStartNode();
				Node end = (pip.isReversed()) ? pip.getStartNode() : pip.getEndNode();

				// Since net already exists, all the nodes it uses must already
				// have been created
				RouteNode rstart = routingGraph.getNode(start);
				assert (rstart != null);
				boolean rstartAdded = rnodes.add(rstart);
				boolean startPreserved = routingGraph.unpreserve(start);
				assert(rstartAdded == startPreserved);

				RouteNode rend = routingGraph.getNode(end);
				assert (rend != null);
				boolean rendAdded = rnodes.add(rend);
				boolean endPreserved = routingGraph.unpreserve(end);
				assert(rendAdded == endPreserved);

				// Also set the prev pointer according to the PIP
				rend.setPrev(rstart);
			}
		} else {
			// Net needs to be created
			netWrapper = createNetWrapperAndConnections(net);

			// Collect all nodes used by this net
			for (PIP pip : net.getPIPs()) {
				Node start = (pip.isReversed()) ? pip.getEndNode() : pip.getStartNode();
				Node end = (pip.isReversed()) ? pip.getStartNode() : pip.getEndNode();
				boolean startPreserved = routingGraph.unpreserve(start);
				boolean endPreserved = routingGraph.unpreserve(end);

				RouteNode rstart = getOrCreateRouteNode(start, RouteNodeType.WIRE);
				RouteNode rend = getOrCreateRouteNode(end, RouteNodeType.WIRE);
				boolean rstartAdded = rnodes.add(rstart);
				boolean rendAdded = rnodes.add(rend);
				assert(rstartAdded == startPreserved);
				assert(rendAdded == endPreserved);

				// Also set the prev pointer according to the PIP
				assert (rend.getPrev() == null);
				rend.setPrev(rstart);
			}

			// Use the prev pointers to update the routing for each connection
			for (Connection netnewConnection : netWrapper.getConnections()) {
				assert(netnewConnection.getSink().isRouted());
				finishRouteConnection(netnewConnection);
			}

			// Update the timing graph
			if(config.isTimingDriven()) {
				timingManager.getTimingGraph().addNetDelayEdges(net);
				timingManager.setTimingEdgesOfConnections(netWrapper.getConnections());
				for (Connection netnewConnection : netWrapper.getConnections()) {
					netnewConnection.updateRouteDelay();
				}
			}
		}

		for (RouteNode rnode : rnodes) {
			Node toBuild = rnode.getNode();
			// Check already unpreserved above
			assert(!routingGraph.isPreserved(toBuild));

			// Each rnode should be added as a child to all of its parents
			// that already exist, unless it was already present
			for(Node uphill : toBuild.getAllUphillNodes()) {
				// Without this routethru check, there will be Invalid Programming for Site error shown in Vivado.
				// Do not use those nodes, because we do not know if the routethru is available or not
				if(routethruHelper.isRouteThru(uphill, toBuild)) continue;
				RouteNode parent = routingGraph.getNode(uphill);
				if (parent == null)
					continue;

				// Parent has never been expanded, let it expand naturally
				if (!parent.everExpanded())
					continue;

				// Parent has been expanded, and if it is the prev node,
				// then it must already be its child
				if (rnode.getPrev() == parent) {
					assert(parent.containsChild(rnode));
					continue;
				}

				// Otherwise there's no reason for it to exist as a child
				assert(!parent.containsChild(rnode));
				parent.addChild(rnode);
			}

			// Clear the prev pointer (as it is also used to track
			// whether a node has been visited during expansion)
			rnode.setPrev(null);
		}

		numPreservedWire--;
		numPreservedRoutableNets--;
	}

	@Override
	protected boolean handleUnroutableConnection(Connection connection) {
		boolean hasAltOutput = super.handleUnroutableConnection(connection);
		if (hasAltOutput)
			return true;
		if (softPreserve) {
			if (routeIteration == 2) {
				unpreserveNetsAndReleaseResources(connection);
				return true;
			}
		}
		return false;
	}

	private static Design routeDesign(Design design, RWRouteConfig config, Collection<SitePinInst> pinsToRoute) {
		if(config.isMaskNodesCrossRCLK()) {
			System.out.println("WARNING: Masking nodes across RCLK for partial routing could result in routability problems.");
		}

		return routeDesign(design, new PartialRouter(design, config, pinsToRoute));
	}

	/**
	 * Routes a design in the partial non-timing-driven routing mode.
	 * @param design The {@link Design} instance to be routed.
	 * @param pinsToRoute Collection of {@link SitePinInst}-s to be routed.
	 */
	public static Design routeDesignPartialNonTimingDriven(Design design, Collection<SitePinInst> pinsToRoute) {
		return routeDesign(design, new RWRouteConfig(new String[] {
				"--fixBoundingBox",
				// use U-turn nodes and no masking of nodes cross RCLK
				// Pros: maximum routability
				// Con: might result in delay optimism and a slight increase in runtime
				"--useUTurnNodes",
				"--nonTimingDriven",
				"--verbose"}),
				pinsToRoute);
	}

	/**
	 * Routes a design in the partial timing-driven routing mode.
	 * @param design The {@link Design} instance to be routed.
	 * @param pinsToRoute Collection of {@link SitePinInst}-s to be routed.
	 */
	public static Design routeDesignPartialTimingDriven(Design design, Collection<SitePinInst> pinsToRoute) {
		return routeDesign(design, new RWRouteConfig(new String[] {
				"--fixBoundingBox",
				// use U-turn nodes and no masking of nodes cross RCLK
				// Pros: maximum routability
				// Con: might result in delay optimism and a slight increase in runtime
				"--useUTurnNodes",
				"--verbose"}),
				pinsToRoute);
	}
}
