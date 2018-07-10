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

package org.opennms.netmgt.provision.service;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.PrimaryType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class IpDiscoveryTableTracker.
 * 
 * @author <a href="mailto:agalue@opennms.org">Alejandro Galue</a>
 */
public class IpDiscoveryTable2Tracker extends IPInterfaceTableTracker implements IpTableTracker{

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(IpDiscoveryTable2Tracker.class);

    /** The discovered IP interfaces. */
    private Set<OnmsIpInterface> discoveredIps = new HashSet<>();

    /* (non-Javadoc)
     * @see org.opennms.netmgt.provision.service.IPInterfaceTableTracker#processIPInterfaceRow(org.opennms.netmgt.provision.service.IPInterfaceTableTracker.IPInterfaceRow)
     */
    @Override
    public void processIPInterfaceRow(IPInterfaceRow row) {
        final String ipAddress = row.getIpAddress();
        LOG.info("Processing IPInterface table row with ipAddr {}", ipAddress);

        if (!IpTableTracker.isValid(ipAddress)) {
            LOG.debug("{} is Invalid, Skipping.", ipAddress);
            return;
        }

        final OnmsIpInterface iface = row.createInterfaceFromRow();
        if (iface != null) {
            iface.setIpLastCapsdPoll(new Date());
            iface.setIsManaged("U");
            iface.setIsSnmpPrimary(PrimaryType.NOT_ELIGIBLE);
            discoveredIps.add(iface);
        }
    }

    /* (non-Javadoc)
     * @see org.opennms.netmgt.provision.service.IpTableTracker#getDiscoveredIps()
     */
    public Set<OnmsIpInterface> getDiscoveredIps() {
        return discoveredIps;
    }

}
