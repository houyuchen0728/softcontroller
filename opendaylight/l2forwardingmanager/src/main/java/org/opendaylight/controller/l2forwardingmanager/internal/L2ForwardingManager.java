/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.l2forwardingmanager.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.osgi.framework.console.CommandProvider;
import org.opendaylight.controller.flowmanager.IL2flowmanager;
import org.opendaylight.controller.sal.action.Action;
import org.opendaylight.controller.sal.action.Output;
import org.opendaylight.controller.sal.core.ConstructionException;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchField;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.packet.BitBufferHelper;
import org.opendaylight.controller.sal.packet.Ethernet;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.opendaylight.controller.sal.packet.Packet;
import org.opendaylight.controller.sal.packet.PacketResult;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * The class describes SwitchManager which is the central repository of all the
 * inventory data including nodes, node connectors, properties attached, Layer3
 * configurations, Span configurations, node configurations, network device
 * representations viewed by Controller Web applications. One SwitchManager
 * instance per container of the network. All the node/nodeConnector properties
 * are maintained in the default container only.
 */
public class L2ForwardingManager implements IListenDataPacket{
    private static final Logger logger = LoggerFactory
            .getLogger(L2ForwardingManager.class);
    private ISwitchManager switchManager = null;
    private IFlowProgrammerService programmer = null;
    private IDataPacketService dataPacketService = null;
    private Map<Node, Map<Long, NodeConnector>> mac_to_port_per_switch = new HashMap<Node, Map<Long, NodeConnector>>();
    private String function = "switch";

    private IL2flowmanager flowManager = null;

    void setDataPacketService(IDataPacketService s) {
        this.dataPacketService = s;
    }

    void unsetDataPacketService(IDataPacketService s) {
        if (this.dataPacketService == s) {
            this.dataPacketService = null;
        }
    }

    public void setFlowProgrammerService(IFlowProgrammerService s){
        this.programmer = s;
    }

    public void unsetFlowProgrammerService(IFlowProgrammerService s) {
        if (this.programmer == s) {
            this.programmer = null;
        }
    }

    void setSwitchManager(ISwitchManager s) {
        logger.debug("SwitchManager set");
        this.switchManager = s;
    }

    void unsetSwitchManager(ISwitchManager s) {
        if (this.switchManager == s) {
            logger.debug("SwitchManager removed!");
            this.switchManager = null;
        }
    }

    void setL2flowmanager(IL2flowmanager s){
        this.flowManager = s;
    }

    void unsetL2flowmanager(IL2flowmanager s) {
        if (this.flowManager == s) {
            this.switchManager = null;
        }
    }
    private void registerWithOSGIConsole() {
        BundleContext bundleContext = FrameworkUtil.getBundle(this.getClass())
                .getBundleContext();
        bundleContext.registerService(CommandProvider.class.getName(), this,
                null);
    }

    /**
     * Function called by the dependency manager when all the required
     * dependencies are satisfied
     *
     */
    void init() {
        logger.info("Initialized");
    }

    /**
     * Function called by the dependency manager when at least one
     * dependency become unsatisfied or when the component is shutting
     * down because for example bundle is being stopped.
     *
     */
    void destroy() {
    }

    /**
     * Function called by dependency manager after "init ()" is called
     * and after the services provided by the class are registered in
     * the service registry
     *
     */
    void start() {
        logger.info("Started");
        // OSGI console
        registerWithOSGIConsole();
    }

    /**
     * Function called by the dependency manager before the services
     * exported by the component are unregistered, this will be
     * followed by a "destroy ()" calls
     *
     */
    void stop() {
        logger.info("Stopped");
    }

    private void floodPacket(RawPacket inPkt) {
        NodeConnector incoming_connector = inPkt.getIncomingNodeConnector();
        Node incoming_node = incoming_connector.getNode();

        Set<NodeConnector> nodeConnectors =
                this.switchManager.getUpNodeConnectors(incoming_node);

        for (NodeConnector p : nodeConnectors) {
            if (!p.equals(incoming_connector)) {
                try {
                    RawPacket destPkt = new RawPacket(inPkt);
                    destPkt.setOutgoingNodeConnector(p);
                    this.dataPacketService.transmitDataPacket(destPkt);
                } catch (ConstructionException e2) {
                    continue;
                }
            }
        }
    }

    @Override
    public PacketResult receiveDataPacket(RawPacket inPkt) {
        if (inPkt == null) {
            return PacketResult.IGNORED;
        }
        logger.trace("Received a frame of size: {}",
                        inPkt.getPacketData().length);

        Packet formattedPak = this.dataPacketService.decodeDataPacket(inPkt);
        NodeConnector incoming_connector = inPkt.getIncomingNodeConnector();
        Node incoming_node = incoming_connector.getNode();


        if (formattedPak instanceof Ethernet) {
            byte[] srcMAC = ((Ethernet)formattedPak).getSourceMACAddress();
            byte[] dstMAC = ((Ethernet)formattedPak).getDestinationMACAddress();

            // Hub implementation
            if (function.equals("hub")) {
                floodPacket(inPkt);
                return PacketResult.CONSUME;
            }

            // Switch
            else {
                long srcMAC_val = BitBufferHelper.toNumber(srcMAC);
                long dstMAC_val = BitBufferHelper.toNumber(dstMAC);

                Match match = new Match();
                match.setField( new MatchField(MatchType.IN_PORT, incoming_connector) );
                match.setField( new MatchField(MatchType.DL_DST, dstMAC.clone()) );
                match.setField( new MatchField(MatchType.DL_SRC, srcMAC.clone()) );

                // Set up the mapping: switch -> src MAC address -> incoming port
                if (this.mac_to_port_per_switch.get(incoming_node) == null) {
                    this.mac_to_port_per_switch.put(incoming_node, new HashMap<Long, NodeConnector>());
                }
                this.mac_to_port_per_switch.get(incoming_node).put(srcMAC_val, incoming_connector);

                NodeConnector dst_connector = this.mac_to_port_per_switch.get(incoming_node).get(dstMAC_val);

                // Do I know the destination MAC?
                if (dst_connector != null) {

                    List<Action> actions = new ArrayList<Action>();
                    actions.add(new Output(dst_connector));

                    Flow f = new Flow(match, actions);
                    //find the idleTimeout and the hardTimeout
                    if( this.flowManager != null){
                        String nodeId = incoming_node.getType()+"|"+incoming_node.getNodeIDString();
                        f.setHardTimeout(this.flowManager.getHardTimeoutByNodeId(nodeId));
                        f.setIdleTimeout(this.flowManager.getIdleTimeoutByNodeId(nodeId));
                    }
                    // Modify the flow on the network node
                    Status status = programmer.addFlow(incoming_node, f);
                    if (!status.isSuccess()) {
                        logger.warn(
                                "SDN Plugin failed to program the flow: {}. The failure is: {}",
                                f, status.getDescription());
                        return PacketResult.IGNORED;
                    }
                    logger.info("Installed flow {} in node {}",
                            f, incoming_node);
                }
                else
                    floodPacket(inPkt);
            }
        }
        return PacketResult.IGNORED;
    }
}

