/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2008-2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.provision;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.opennms.netmgt.config.api.SnmpAgentConfigFactory;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.opennms.netmgt.model.events.EventBuilder;
import org.opennms.netmgt.provision.service.IpTableTracker;
import org.opennms.netmgt.snmp.SnmpAgentConfig;
import org.opennms.netmgt.snmp.TableTracker;
import org.opennms.netmgt.snmp.proxy.LocationAwareSnmpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

/**
 * An IP discovery provisioning adapter for integration with OpenNMS Provisioning daemon API.
 * <p>The purpose of this adapter is to persist all the discovered IPs (i.e. IPs not explicitly declared on the requisition) as unmanaged.</p>
 *
 * @author <a href="mailto:agalue@opennms.org">Alejandro Galue</a>
 */
public class IpDiscoveryProvisioningAdapter extends SimplerQueuedProvisioningAdapter implements InitializingBean {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(IpDiscoveryProvisioningAdapter.class);

    /** The Node DAO. */
    private NodeDao m_nodeDao;    

    /** The Event Forwarder. */
    private EventForwarder m_eventForwarder;

    /** The location aware SNMP client. */
    private LocationAwareSnmpClient m_locationAwareSnmpClient;

    /** The SNMP agent configuration factory. */
    private SnmpAgentConfigFactory m_snmpPeerFactory;

    /** The Constant PREFIX. */
    public static final String PREFIX = "Provisiond.";

    /** The Constant NAME. */
    private static final String NAME = IpDiscoveryProvisioningAdapter.class.getSimpleName();    

    /**
     * Instantiates a new IP discovery provisioning adapter.
     */
    public IpDiscoveryProvisioningAdapter() {
        super(NAME);
    }

    /* (non-Javadoc)
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(m_nodeDao, "NodeDao is required.");
        Assert.notNull(m_template, "TransactionTemplate is required.");
        Assert.notNull(m_eventForwarder, "EventForwarder is required.");
        Assert.notNull(m_snmpPeerFactory, "SnmpPeerFactory is required.");
        Assert.notNull(m_locationAwareSnmpClient, "LocationAwareSnmpClient is required.");
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.provision.SimplerQueuedProvisioningAdapter#doUpdateNode(int)
     */
    @Override
    public void doUpdateNode(final int nodeId) throws ProvisioningAdapterException {
        // TODO Verify if a system property exist and has a value of true, prior executing this.
        // TODO Get the ForeignSourceRepository to get the explicit list of IPs from the requisition.
        LOG.debug("doUpdate: updating nodeid: {}", nodeId);
        discoverIpInterfaces(nodeId);
    }

    /**
     * Discover IP interfaces.
     *
     * @param nodeId the node ID
     */
    private void discoverIpInterfaces(int nodeId) {
        final OnmsNode node = m_nodeDao.get(nodeId);
        if (node == null) {
            throw new ProvisioningAdapterException("Failed to return node for given nodeId: " + nodeId);
        }

        final OnmsIpInterface intf = node.getPrimaryInterface();
        if (intf == null) {
            throw new ProvisioningAdapterException("Can't find the SNMP Primary IP address for nodeId: " + nodeId);            
        }
        final InetAddress ipAddress = intf.getIpAddress();

        EventBuilder ebldr = null;
        try {
            final String locationName = node.getLocation() == null ? null : node.getLocation().getLocationName();
            final SnmpAgentConfig agentConfig = m_snmpPeerFactory.getAgentConfig(ipAddress, locationName);
            LOG.debug("doUpdate: snmp agent config: {}", agentConfig);

            Set<OnmsIpInterface> managedInterfaces = node.getIpInterfaces().stream()
                    .filter(i -> i.isManaged())
                    .collect(Collectors.toSet());

            TableTracker tracker = IpTableTracker.getIpAddressTableTracker();
            executeTracker(node, agentConfig, tracker);
            IpTableTracker ipTracker = (IpTableTracker) tracker;
            LOG.info("doUpdate: Found {} interfaces on node {} using IP-MIB::ipAddressTable", ipTracker.getDiscoveredIps().size(), node.getLabel());
            if (ipTracker.getDiscoveredIps().isEmpty()) {
                tracker = IpTableTracker.getIpAddrTableTracker();
                executeTracker(node, agentConfig, tracker);
                ipTracker = (IpTableTracker) tracker;
                LOG.info("doUpdate: Found {} interfaces on node {} using IP-MIB::ipAddrTable", ipTracker.getDiscoveredIps().size(), node.getLabel());
            }

            // Removing managed interfaces from the discover list
            final Set<OnmsIpInterface> discoveredInterfaces = ipTracker.getDiscoveredIps();
            for (Iterator<OnmsIpInterface> it = discoveredInterfaces.iterator(); it.hasNext();) {
                OnmsIpInterface ip = it.next();
                for (OnmsIpInterface i : managedInterfaces) {
                    if (i.getIpAddress().equals(ip.getIpAddress())) it.remove();
                }
            }
            
            // Removing ifIndex for discovered IPs without physical interface
            discoveredInterfaces.forEach(ip -> {
                final OnmsSnmpInterface iface = node.getSnmpInterfaceWithIfIndex(ip.getIfIndex());
                if (iface == null) {
                    LOG.warn("Cannot find SNMP interface on node {} for ifIndex {}.", node.getLabel(), ip.getIfIndex());
                    ip.setIfIndex(null);
                }
            });

            // Merge IP interfaces and save node
            final OnmsNode scannedNode = new OnmsNode();
            scannedNode.getIpInterfaces().addAll(managedInterfaces);
            scannedNode.getIpInterfaces().addAll(discoveredInterfaces);            
            node.mergeIpInterfaces(scannedNode, m_eventForwarder, true);
            m_nodeDao.update(node);

            ebldr = new EventBuilder("uei.opennms.org/internal/discovery/ipAdapterSuccessful", PREFIX + NAME);
        } catch (Throwable e) {
            LOG.error("Error handling node added event.", e);
            ebldr = new EventBuilder("uei.opennms.org/internal/discovery/ipAdapterFailed", PREFIX + NAME);
            ebldr.addParam(EventConstants.PARM_REASON, e.getMessage());
        }
        if (ebldr != null) {
            ebldr.setNodeid(nodeId);
            ebldr.setInterface(ipAddress);
            m_eventForwarder.sendNow(ebldr.getEvent());
        }        
    }

    /**
     * Execute tracker.
     *
     * @param node the node
     * @param agentConfig the SNMP agent configuration
     * @param tracker the table tracker
     * @throws InterruptedException the interrupted exception
     * @throws ExecutionException the execution exception
     */
    private void executeTracker(final OnmsNode node,
            final SnmpAgentConfig agentConfig,
            final TableTracker tracker)
                    throws InterruptedException, ExecutionException {
        m_locationAwareSnmpClient.walk(agentConfig, tracker)
        .withDescription(tracker.getClass().getSimpleName() + '_' + node.getLabel())
        .withLocation(node.getLocation() == null ? null : node.getLocation().getLocationName())
        .execute()
        .get();
    }

    /**
     * Sets the node DAO.
     *
     * @param dao the new node DAO
     */
    public void setNodeDao(NodeDao dao) {
        m_nodeDao = dao;
    }

    /**
     * Sets the event forwarder.
     *
     * @param eventForwarder the new event forwarder
     */
    public void setEventForwarder(EventForwarder eventForwarder) {
        m_eventForwarder = eventForwarder;
    }

    /**
     * Sets the location aware SNMP client.
     *
     * @param locationAwareSnmpClient the new location aware SNMP client
     */
    public void setLocationAwareSnmpClient(LocationAwareSnmpClient locationAwareSnmpClient) {
        this.m_locationAwareSnmpClient = locationAwareSnmpClient;
    }

    /**
     * Sets the SNMP agent configuration factory.
     *
     * @param snmpPeerFactory the new SNMP agent configuration factory
     */
    public void setSnmpPeerFactory(SnmpAgentConfigFactory snmpPeerFactory) {
        this.m_snmpPeerFactory = snmpPeerFactory;
    }

}
