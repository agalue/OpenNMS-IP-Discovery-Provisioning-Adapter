/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2009-2018 The OpenNMS Group, Inc.
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

import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.opennms.core.spring.BeanUtils;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.test.OnmsTestRunner;
import org.opennms.core.test.snmp.annotations.JUnitSnmpAgent;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.config.api.SnmpAgentConfigFactory;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.model.NetworkBuilder;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.provision.SimpleQueuedProvisioningAdapter.AdapterOperation;
import org.opennms.netmgt.provision.SimpleQueuedProvisioningAdapter.AdapterOperationSchedule;
import org.opennms.netmgt.provision.SimpleQueuedProvisioningAdapter.AdapterOperationType;
import org.opennms.netmgt.provision.service.PhysicalInterfaceTracker;
import org.opennms.netmgt.snmp.SnmpAgentConfig;
import org.opennms.netmgt.snmp.proxy.LocationAwareSnmpClient;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * The Test Class for IpDiscoveryProvisioningAdapter.
 * 
 * @author <a href="mailto:agalue@opennms.org">Alejandro Galue</a>
 */
@RunWith(OnmsTestRunner.class)
@ContextConfiguration(locations= {
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-mockDao.xml",
        "classpath:/META-INF/opennms/applicationContext-proxy-snmp.xml",
        "classpath:/META-INF/opennms/mockEventIpcManager.xml",
        "classpath:/META-INF/opennms/eventForwarder.xml",
        "classpath:/META-INF/opennms/provisiond-extensions.xml"
})
@JUnitSnmpAgent(host="172.20.1.1", resource="snmpTestData.properties")
public class IpDiscoveryProvisioningAdapterTest implements InitializingBean {

    /** The provisioning adapter. */
    @Autowired
    private IpDiscoveryProvisioningAdapter m_adapter;

    /** The Node DAO. */
    @Autowired
    private NodeDao m_nodeDao;

    /** The location aware SNMP client. */
    @Autowired
    private LocationAwareSnmpClient m_locationAwareSnmpClient;

    /** The SNMP agent configuration factory. */
    @Autowired
    private SnmpAgentConfigFactory m_snmpPeerFactory;

    /** The adapter operation. */
    private AdapterOperation m_addOperation;

    static {
        System.setProperty("opennms.home", "src/test/resources/onms-home");
    }

    /* (non-Javadoc)
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        BeanUtils.assertAutowiring(this);
    }

    /**
     * Sets up the test.
     *
     * @throws Exception the exception
     */
    @Before
    public void setUp() throws Exception {
        MockLogAppender.setupLogging(true);

        NetworkBuilder nb = new NetworkBuilder();
        nb.addNode("test01").setForeignSource("junit").setForeignId("1").setLocation("Default");
        nb.addInterface("172.20.1.1").setIsSnmpPrimary("P").setIsManaged("M");

        m_nodeDao.save(nb.getCurrentNode());
        m_nodeDao.flush();
        m_adapter.afterPropertiesSet();

        AdapterOperationSchedule ops = new AdapterOperationSchedule(0, 1, 1, TimeUnit.SECONDS);
        int nodeId = m_nodeDao.findByForeignId("junit", "1").getId();
        m_addOperation = m_adapter.new AdapterOperation(nodeId, AdapterOperationType.UPDATE, ops);
    }

    /**
     * Tear down.
     *
     * @throws Exception the exception
     */
    @After
    public void tearDown() throws Exception{
        MockLogAppender.assertNoWarningsOrGreater();
    }

    /**
     * Test add node.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAddNode() throws Exception {
        // Create test node with 1 IP interface
        OnmsNode currentNode = m_nodeDao.findByForeignId("junit", "1");
        Assert.assertNotNull(currentNode);
        Assert.assertEquals(1, currentNode.getIpInterfaces().size());

        // Adding SNMP interfaces
        final SnmpAgentConfig agentConfig = m_snmpPeerFactory.getAgentConfig(InetAddressUtils.addr("172.20.1.1"), null);
        final PhysicalInterfaceTracker tracker = new PhysicalInterfaceTracker();
        m_locationAwareSnmpClient.walk(agentConfig, tracker)
        .withDescription(tracker.getClass().getSimpleName())
        .execute()
        .get();
        currentNode.setSnmpInterfaces(tracker.getDiscoveredInterfaces());

        // Execute the adapter
        m_adapter.processPendingOperationForNode(m_addOperation);

        // Validate IP interfaces
        OnmsNode updatedNode = m_nodeDao.findByForeignId("junit", "1");
        Assert.assertNotNull(updatedNode);
        Assert.assertEquals(15, updatedNode.getIpInterfaces().size());
    }

}
