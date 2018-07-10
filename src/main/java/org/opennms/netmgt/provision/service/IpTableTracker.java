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

import static org.opennms.core.utils.InetAddressUtils.addr;

import java.net.InetAddress;
import java.util.Set;

import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.snmp.TableTracker;

/**
 * The Interface IpTableTracker.
 * 
 * @author <a href="mailto:agalue@opennms.org">Alejandro Galue</a>
 */
public interface IpTableTracker {

    /**
     * Gets the discovered IP addresses.
     *
     * @return the discovered IP addresses
     */
    public Set<OnmsIpInterface> getDiscoveredIps();

    /**
     * Gets the table tracker for IP-MIB::ipAddressTable
     *
     * @return the IP table tracker
     */
    static TableTracker getIpAddressTableTracker() {
        return new IpDiscoveryTable1Tracker();
    }

    /**
     * Gets the table tracker for IP-MIB::ipAddrTable
     *
     * @return the IP table tracker
     */
    static TableTracker getIpAddrTableTracker() {
        return new IpDiscoveryTable2Tracker();
    }

    /**
     * Checks if a given IP address is valid.
     *
     * @param ipAddress the IP address
     * @return true, if is valid
     */
    static boolean isValid(String ipAddress) {
        final InetAddress address = addr(ipAddress);
        if (address == null) {
            return false;
        } else if (address.isAnyLocalAddress()) {
            return false;
        } else if (address.isLinkLocalAddress()) {
            return false;
        } else if (address.isLoopbackAddress()) {
            return false;
        } else if (address.isMulticastAddress()) {
            return false;
        }
        return true;
    }
}
