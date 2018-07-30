[![CircleCI](https://circleci.com/gh/agalue/OpenNMS-IP-Discovery-Provisioning-Adapter.svg?style=svg)](https://circleci.com/gh/agalue/OpenNMS-IP-Discovery-Provisioning-Adapter)

# OpenNMS-IP-Discovery-Provisioning-Adapter
Optional Provisioning Adapter to manage auto-discovery of IP interfaces as unmanaged

## Motivation

It is very common to find OpenNMS users and operators that want to discover and persist all the IP interfaces on the database, but only actively monitor the monitored services either explicitly declared on the requisition or discovered through detectors.

Currently there is no way to do that with Provisiond, and that's why this adapter has been created.

The only requirement to use this adapter it is adding an IP policy to avoid persist discovered IP interfaces, so only what's explicitly declared on the requisition will be persisted on the database. Then, this adapter will take care of adding the additional IP interfaces discovered through SNMP based on the IP-MIB and persist them as unmanaged interfaces, so the Poller will ignore them.

## Installation

* Edit the `pom.xml` file and make sure that the `opennms.version` property matches the version of OpenNMS you're running. For Meridian, you can use the closest Horizon version as the API for the Provisioning Adapters has not been changed since its conception.

* Compile and generate the JAR for the provisioning adapter

```
mvn clean install
```

* Copy the JAR (for example `opennms-ip-discovery-provisioning-adapter-22.0.1.jar`) into the `$OPENNMS_HOME/lib` directory.

* Open the OpenNMS UI and add to either the default foreign source definition and/or the foreign source definition for each requisition, an IP policy to avoid persist discovered interfaces, for example:

```xml
<policy name="No Discovered IPs" class="org.opennms.netmgt.provision.persist.policies.MatchingIpInterfacePolicy">
  <parameter key="action" value="DO_NOT_PERSIST"/>
  <parameter key="matchBehavior" value="NO_PARAMETERS"/>
</policy>
```

* Restart OpenNMS

* Synchronize each requisition or wait until the next scheduled operation runs.

## Analysis

To understand how this adapter works, let's assume the following scenario:

> It is required to discover and persist all the IP interfaces from all the nodes, for inventory and reporting purposes; but the poller should focus only on the IP interfaces explicitly defined on the requisition. A given node might have additional IP interfaces besides the primary on which polling is important. In terms of services, some can be forced on the requisitions, and other can be detected.

On an ideal world, adding a policy like the following should be enough to persue the above objective:

<policy name="Unmanage Discovered Interfaces" class="org.opennms.netmgt.provision.persist.policies.MatchingIpInterfacePolicy">
  <parameter key="action" value="UNMANAGE"/>
  <parameter key="matchBehavior" value="NO_PARAMETERS"/>
</policy>

Unfortunately, the non-primary interfaces on the requisition will suffer the consequences.

To simplify the tests, let's assume that a requisition is created with services declared on it (no detectors), and the foreign source definition contains the above policy:

```
[root@horizon ~]# curl -u admin:admin http://localhost:8980/opennms/rest/requisitions/Test 2>/dev/null | xmllint --format -
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<model-import xmlns="http://xmlns.opennms.org/xsd/config/model-import" date-stamp="2018-07-30T14:44:32.678-04:00" foreign-source="Test" last-import="2018-07-30T14:45:19.891-04:00">
  <node foreign-id="node1" node-label="node1">
    <interface ip-addr="172.20.1.1" status="1" snmp-primary="P">
      <monitored-service service-name="ICMP"/>
      <monitored-service service-name="SNMP"/>
    </interface>
    <interface ip-addr="172.20.2.1" status="1" snmp-primary="N">
      <monitored-service service-name="ICMP"/>
    </interface>
  </node>
</model-import>

[root@horizon ~]# curl -u admin:admin http://localhost:8980/opennms/rest/foreignSources/Test 2>/dev/null | xmllint --format -
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<foreign-source xmlns="http://xmlns.opennms.org/xsd/config/foreign-source" name="Test" date-stamp="2018-07-30T14:44:34.669-04:00">
  <scan-interval>1d</scan-interval>
  <detectors/>
  <policies>
    <policy name="Unmanage Discovered Interfaces" class="org.opennms.netmgt.provision.persist.policies.MatchingIpInterfacePolicy">
      <parameter key="action" value="UNMANAGE"/>
      <parameter key="matchBehavior" value="NO_PARAMETERS"/>
    </policy>
  </policies>
</foreign-source>
```

Here are the results:

```
opennms=> select nodeid,nodelabel,foreignsource,foreignid from node where nodelabel='node1';
 nodeid | nodelabel | foreignsource | foreignid 
--------+-----------+---------------+-----------
      2 | node1     | Test          | node1
(1 row)

opennms=> select eventuei,eventtime,ipaddr,servicename from events e left join service s on s.serviceid=e.serviceid where nodeid=2 order by eventid;
                              eventuei                               |         eventtime          |    ipaddr    | servicename 
---------------------------------------------------------------------+----------------------------+--------------+-------------
 uei.opennms.org/nodes/nodeAdded                                     | 2018-07-30 14:45:20.198-04 |              | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:20.199-04 | 172.20.1.1   | 
 uei.opennms.org/nodes/nodeGainedService                             | 2018-07-30 14:45:20.207-04 | 172.20.2.1   | ICMP
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:20.203-04 | 172.20.2.1   | 
 uei.opennms.org/nodes/nodeGainedService                             | 2018-07-30 14:45:20.203-04 | 172.20.1.1   | SNMP
 uei.opennms.org/nodes/nodeGainedService                             | 2018-07-30 14:45:20.203-04 | 172.20.1.1   | ICMP
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:22.928-04 | 10.0.0.1     | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:22.945-04 | 10.0.0.16    | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:22.95-04  | 10.0.0.6     | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:23.102-04 | 66.57.83.126 | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:23.162-04 | 70.63.156.34 | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:23.18-04  | 128.0.0.1    | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:23.199-04 | 128.0.0.4    | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:23.223-04 | 128.0.0.6    | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:23.76-04  | 128.0.1.16   | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:23.808-04 | 172.20.12.1  | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:23.819-04 | 172.20.11.1  | 
 uei.opennms.org/nodes/serviceDeleted                                | 2018-07-30 14:45:23.78-04  | 172.20.2.1   | ICMP
 uei.opennms.org/nodes/serviceDeleted                                | 2018-07-30 14:45:23.763-04 | 172.20.1.1   | ICMP
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:23.838-04 | 172.20.14.1  | 
 uei.opennms.org/nodes/serviceDeleted                                | 2018-07-30 14:45:23.763-04 | 172.20.1.1   | SNMP
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 14:45:23.831-04 | 172.20.13.1  | 
 uei.opennms.org/nodes/reinitializePrimarySnmpInterface              | 2018-07-30 14:45:23.907-04 | 172.20.1.1   | 
 uei.opennms.org/internal/provisiond/nodeScanCompleted               | 2018-07-30 14:45:23.915-04 |              | 
(26 rows)

opennms=> select ipaddr,ismanaged,issnmpprimary from ipinterface where nodeid=2;
    ipaddr    | ismanaged | issnmpprimary 
--------------+-----------+---------------
 10.0.0.1     | U         | N
 10.0.0.16    | U         | N
 10.0.0.6     | U         | N
 128.0.0.1    | U         | N
 128.0.0.4    | U         | N
 128.0.0.6    | U         | N
 128.0.1.16   | U         | N
 172.20.1.1   | U         | P
 172.20.11.1  | U         | N
 172.20.12.1  | U         | N
 172.20.13.1  | U         | N
 172.20.14.1  | U         | N
 172.20.2.1   | U         | N
 6x.xx.xx.xx  | U         | N
 7x.xx.xx.xx  | U         | N
(15 rows)

opennms=> select servicename,ipaddr,status from ifservices s left join ipinterface p on s.ipinterfaceid = p.id left join service ss on ss.serviceid=s.serviceid where nodeid=2;
 servicename | ipaddr | status 
-------------+--------+--------
(0 rows)
```

> NOTE: public IPs were masked for security reasons.

Notice that the service were deleted, and there are no monitored services (because there are no detectors). For this reason, the solution is wrong.

Let's now try with declaring the IPs on the requisition, but let services to be detected.

```
[root@horizon ~]# curl -u admin:admin http://localhost:8980/opennms/rest/requisitions/Test 2>/dev/null | xmllint --format -
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<model-import xmlns="http://xmlns.opennms.org/xsd/config/model-import" date-stamp="2018-07-30T14:51:18.442-04:00" foreign-source="Test" last-import="2018-07-30T14:52:01.640-04:00">
  <node foreign-id="node1" node-label="node1">
    <interface ip-addr="172.20.1.1" status="1" snmp-primary="P"/>
    <interface ip-addr="172.20.2.1" status="1" snmp-primary="N"/>
  </node>
</model-import>

[root@horizon ~]# curl -u admin:admin http://localhost:8980/opennms/rest/foreignSources/Test 2>/dev/null | xmllint --format -
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<foreign-source xmlns="http://xmlns.opennms.org/xsd/config/foreign-source" name="Test" date-stamp="2018-07-30T14:51:20.188-04:00">
  <scan-interval>1d</scan-interval>
  <detectors>
    <detector name="ICMP" class="org.opennms.netmgt.provision.detector.icmp.IcmpDetector"/>
    <detector name="SNMP" class="org.opennms.netmgt.provision.detector.snmp.SnmpDetector"/>
    <detector name="SSH" class="org.opennms.netmgt.provision.detector.ssh.SshDetector"/>
  </detectors>
  <policies>
    <policy name="Unmanage Discovered Interfaces" class="org.opennms.netmgt.provision.persist.policies.MatchingIpInterfacePolicy">
      <parameter key="action" value="UNMANAGE"/>
      <parameter key="matchBehavior" value="NO_PARAMETERS"/>
    </policy>
  </policies>
</foreign-source>
```

Here are the results:

```
opennms=> select nodeid,nodelabel,foreignsource,foreignid from node where nodelabel='node1';
 nodeid | nodelabel | foreignsource | foreignid 
--------+-----------+---------------+-----------
      3 | node1     | Test          | node1
(1 row)

opennms=> select eventuei,eventtime,ipaddr,servicename from events e left join service s on s.serviceid=e.serviceid where nodeid=3 order by eventid;
                        eventuei                        |         eventtime          |    ipaddr    | servicename 
--------------------------------------------------------+----------------------------+--------------+-------------
 uei.opennms.org/nodes/nodeAdded                        | 2018-07-30 14:52:01.775-04 |              | 
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:01.778-04 | 172.20.2.1   | 
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:01.775-04 | 172.20.1.1   | 
 uei.opennms.org/nodes/nodeGainedService                | 2018-07-30 14:52:01.886-04 | 172.20.1.1   | ICMP
 uei.opennms.org/nodes/nodeGainedService                | 2018-07-30 14:52:01.893-04 | 172.20.1.1   | SNMP
 uei.opennms.org/nodes/nodeGainedService                | 2018-07-30 14:52:02.035-04 | 172.20.1.1   | SSH
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:12.671-04 | 10.0.0.1     | 
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:12.676-04 | 10.0.0.6     | 
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:12.686-04 | 10.0.0.16    | 
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:12.694-04 | 66.57.83.126 | 
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:12.715-04 | 70.63.156.34 | 
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:12.726-04 | 128.0.0.1    | 
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:12.729-04 | 128.0.0.4    | 
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:12.754-04 | 128.0.0.6    | 
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:12.794-04 | 128.0.1.16   | 
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:12.828-04 | 172.20.12.1  | 
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:12.819-04 | 172.20.11.1  | 
 uei.opennms.org/nodes/serviceDeleted                   | 2018-07-30 14:52:12.798-04 | 172.20.1.1   | ICMP
 uei.opennms.org/nodes/serviceDeleted                   | 2018-07-30 14:52:12.798-04 | 172.20.1.1   | SSH
 uei.opennms.org/nodes/serviceDeleted                   | 2018-07-30 14:52:12.798-04 | 172.20.1.1   | SNMP
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:12.864-04 | 172.20.13.1  | 
 uei.opennms.org/nodes/nodeGainedInterface              | 2018-07-30 14:52:12.886-04 | 172.20.14.1  | 
 uei.opennms.org/nodes/nodeGainedService                | 2018-07-30 14:52:12.913-04 | 172.20.1.1   | ICMP
 uei.opennms.org/nodes/nodeGainedService                | 2018-07-30 14:52:12.954-04 | 172.20.1.1   | SNMP
 uei.opennms.org/nodes/nodeGainedService                | 2018-07-30 14:52:13.086-04 | 172.20.1.1   | SSH
 uei.opennms.org/nodes/reinitializePrimarySnmpInterface | 2018-07-30 14:52:17.736-04 | 172.20.1.1   | 
 uei.opennms.org/internal/provisiond/nodeScanCompleted  | 2018-07-30 14:52:17.736-04 |              | 
(27 rows)

opennms=> select ipaddr,ismanaged,issnmpprimary from ipinterface where nodeid=3;
    ipaddr    | ismanaged | issnmpprimary 
--------------+-----------+---------------
 10.0.0.1     | U         | N
 10.0.0.6     | U         | N
 10.0.0.16    | U         | N
 6x.xx.xx.xx  | U         | N
 7x.xx.xx.xx  | U         | N
 128.0.0.1    | U         | N
 128.0.0.4    | U         | N
 128.0.0.6    | U         | N
 128.0.1.16   | U         | N
 172.20.11.1  | U         | N
 172.20.12.1  | U         | N
 172.20.13.1  | U         | N
 172.20.14.1  | U         | N
 172.20.1.1   | M         | P
 172.20.2.1   | M         | N
(15 rows)

opennms=> select servicename,ipaddr,status from ifservices s left join ipinterface p on s.ipinterfaceid = p.id left join service ss on ss.serviceid=s.serviceid where nodeid=3;
 servicename |   ipaddr   | status 
-------------+------------+--------
 ICMP        | 172.20.1.1 | A
 SNMP        | 172.20.1.1 | A
 SSH         | 172.20.1.1 | A
(3 rows)
```

We can see progress, services were discovered on the primary interface, but nothing on the secondary. Notice that the services were deleted and then re-added.

An interesting question would be, is there any difference if the `snmp-primary` flag is configured as `S` (secondary) instead of `N` (not-used) ? Unfortunately, there is no difference. I invite you to try it yourself.

This is why this adapter was created, to control the addition of the discovered IPs without interfeering with the workflow of adding/removing services.

With this adapter installed, you can use any of the above use case, and you'll get the desired results: monitored services only on the IPs declated on the requisition.

Let's review the first scenario again (same requisition), but remember that we now need a single IP policy to avoid discovery and persist IP addresses (as the handler will do that part for you).

For th first use case:

```
[root@horizon ~]# curl -u admin:admin http://localhost:8980/opennms/rest/requisitions/Test 2>/dev/null | xmllint --format -
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<model-import xmlns="http://xmlns.opennms.org/xsd/config/model-import" date-stamp="2018-07-30T15:07:21.018-04:00" foreign-source="Test" last-import="2018-07-30T15:07:26.025-04:00">
  <node foreign-id="node1" node-label="node1">
    <interface ip-addr="172.20.1.1" status="1" snmp-primary="P">
      <monitored-service service-name="ICMP"/>
      <monitored-service service-name="SNMP"/>
    </interface>
    <interface ip-addr="172.20.2.1" status="1" snmp-primary="N">
      <monitored-service service-name="ICMP"/>
    </interface>
  </node>
</model-import>

[root@horizon ~]# curl -u admin:admin http://localhost:8980/opennms/rest/foreignSources/Test 2>/dev/null | xmllint --format -
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<foreign-source xmlns="http://xmlns.opennms.org/xsd/config/foreign-source" name="Test" date-stamp="2018-07-30T15:06:06.332-04:00">
  <scan-interval>1d</scan-interval>
  <detectors/>
  <policies>
    <policy name="Do Not Persist Discovered IPs" class="org.opennms.netmgt.provision.persist.policies.MatchingIpInterfacePolicy">
      <parameter key="action" value="DO_NOT_PERSIST"/>
      <parameter key="matchBehavior" value="NO_PARAMETERS"/>
    </policy>
  </policies>
</foreign-source>
```

The results are:

```
opennms=> select nodeid,nodelabel,foreignsource,foreignid from node where nodelabel='node1';
 nodeid | nodelabel | foreignsource | foreignid 
--------+-----------+---------------+-----------
      5 | node1     | Test          | node1
(1 row)

opennms=> select eventuei,eventtime,ipaddr,servicename from events e left join service s on s.serviceid=e.serviceid where nodeid=5 order by eventid;
                              eventuei                               |         eventtime          |    ipaddr    | servicename 
---------------------------------------------------------------------+----------------------------+--------------+-------------
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:26.466-04 | 172.20.2.1   | 
 uei.opennms.org/nodes/nodeAdded                                     | 2018-07-30 15:07:26.46-04  |              | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:26.462-04 | 172.20.1.1   | 
 uei.opennms.org/nodes/nodeGainedService                             | 2018-07-30 15:07:26.466-04 | 172.20.1.1   | ICMP
 uei.opennms.org/nodes/nodeGainedService                             | 2018-07-30 15:07:26.466-04 | 172.20.1.1   | SNMP
 uei.opennms.org/nodes/nodeGainedService                             | 2018-07-30 15:07:26.471-04 | 172.20.2.1   | ICMP
 uei.opennms.org/nodes/reinitializePrimarySnmpInterface              | 2018-07-30 15:07:29.811-04 | 172.20.1.1   | 
 uei.opennms.org/internal/provisiond/nodeScanCompleted               | 2018-07-30 15:07:29.813-04 |              | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:31.059-04 | 128.0.0.1    | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:31.059-04 | 128.0.0.4    | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:31.059-04 | 10.0.0.1     | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:31.059-04 | 10.0.0.6     | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:31.059-04 | 128.0.0.6    | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:31.059-04 | 66.57.83.126 | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:31.059-04 | 10.0.0.16    | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:31.059-04 | 172.20.13.1  | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:31.059-04 | 128.0.1.16   | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:31.059-04 | 172.20.11.1  | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:31.059-04 | 172.20.14.1  | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:31.059-04 | 172.20.12.1  | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:07:31.059-04 | 70.63.156.34 | 
 uei.opennms.org/internal/discovery/ipAdapterSuccessful              | 2018-07-30 15:07:31.06-04  | 172.20.1.1   | 
(25 rows)

opennms=> select ipaddr,ismanaged,issnmpprimary from ipinterface where nodeid=5;
    ipaddr    | ismanaged | issnmpprimary 
--------------+-----------+---------------
 172.20.2.1   | M         | N
 172.20.1.1   | M         | P
 6x.xx.xx.xx  | U         | N
 10.0.0.6     | U         | N
 128.0.0.6    | U         | N
 10.0.0.1     | U         | N
 10.0.0.16    | U         | N
 128.0.0.4    | U         | N
 172.20.13.1  | U         | N
 128.0.0.1    | U         | N
 172.20.11.1  | U         | N
 7x.xx.xx.xx  | U         | N
 128.0.1.16   | U         | N
 172.20.12.1  | U         | N
 172.20.14.1  | U         | N
(15 rows)

opennms=> select servicename,ipaddr,status from ifservices s left join ipinterface p on s.ipinterfaceid = p.id left join service ss on ss.serviceid=s.serviceid where nodeid=5;
 servicename |   ipaddr   | status 
-------------+------------+--------
 ICMP        | 172.20.2.1 | A
 ICMP        | 172.20.1.1 | A
 SNMP        | 172.20.1.1 | A
(3 rows)
```

As you can see, there are no more intermediate events, and now we're seeing the services added to the second IP address.

For the second use case:

```
[root@horizon ~]# curl -u admin:admin http://localhost:8980/opennms/rest/requisitions/Test 2>/dev/null | xmllint --format -
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<model-import xmlns="http://xmlns.opennms.org/xsd/config/model-import" date-stamp="2018-07-30T15:11:04.591-04:00" foreign-source="Test" last-import="2018-07-30T15:11:11.625-04:00">
  <node foreign-id="node1" node-label="node1">
    <interface ip-addr="172.20.1.1" status="1" snmp-primary="P"/>
    <interface ip-addr="172.20.2.1" status="1" snmp-primary="N"/>
  </node>
</model-import>

[root@horizon ~]# curl -u admin:admin http://localhost:8980/opennms/rest/foreignSources/Test 2>/dev/null | xmllint --format -
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<foreign-source xmlns="http://xmlns.opennms.org/xsd/config/foreign-source" name="Test" date-stamp="2018-07-30T15:10:07.360-04:00">
  <scan-interval>1d</scan-interval>
  <detectors>
    <detector name="ICMP" class="org.opennms.netmgt.provision.detector.icmp.IcmpDetector"/>
    <detector name="SNMP" class="org.opennms.netmgt.provision.detector.snmp.SnmpDetector"/>
    <detector name="SSH" class="org.opennms.netmgt.provision.detector.ssh.SshDetector"/>
  </detectors>
  <policies>
    <policy name="Do Not Persist Discovered IPs" class="org.opennms.netmgt.provision.persist.policies.MatchingIpInterfacePolicy">
      <parameter key="action" value="DO_NOT_PERSIST"/>
      <parameter key="matchBehavior" value="NO_PARAMETERS"/>
    </policy>
  </policies>
</foreign-source>
```

The results are:

```
opennms=> select nodeid,nodelabel,foreignsource,foreignid from node where nodelabel='node1';
 nodeid | nodelabel | foreignsource | foreignid 
--------+-----------+---------------+-----------
      6 | node1     | Test          | node1
(1 row)

opennms=> select eventuei,eventtime,ipaddr,servicename from events e left join service s on s.serviceid=e.serviceid where nodeid=6 order by eventid;
                              eventuei                               |         eventtime          |    ipaddr    | servicename 
---------------------------------------------------------------------+----------------------------+--------------+-------------
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:11.836-04 | 172.20.2.1   | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:11.832-04 | 172.20.1.1   | 
 uei.opennms.org/nodes/nodeAdded                                     | 2018-07-30 15:11:11.832-04 |              | 
 uei.opennms.org/nodes/nodeGainedService                             | 2018-07-30 15:11:11.972-04 | 172.20.1.1   | ICMP
 uei.opennms.org/nodes/nodeGainedService                             | 2018-07-30 15:11:11.98-04  | 172.20.1.1   | SNMP
 uei.opennms.org/nodes/nodeGainedService                             | 2018-07-30 15:11:12.133-04 | 172.20.1.1   | SSH
 uei.opennms.org/internal/provisiond/nodeScanCompleted               | 2018-07-30 15:11:27.896-04 |              | 
 uei.opennms.org/nodes/reinitializePrimarySnmpInterface              | 2018-07-30 15:11:27.895-04 | 172.20.1.1   | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:29.103-04 | 10.0.0.6     | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:29.103-04 | 128.0.0.6    | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:29.103-04 | 10.0.0.1     | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:29.103-04 | 128.0.0.4    | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:29.103-04 | 128.0.0.1    | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:29.103-04 | 66.57.83.126 | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:29.103-04 | 10.0.0.16    | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:29.103-04 | 128.0.1.16   | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:29.103-04 | 70.63.156.34 | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:29.103-04 | 172.20.12.1  | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:29.103-04 | 172.20.14.1  | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:29.103-04 | 172.20.13.1  | 
 uei.opennms.org/nodes/nodeGainedInterface                           | 2018-07-30 15:11:29.103-04 | 172.20.11.1  | 
 uei.opennms.org/internal/discovery/ipAdapterSuccessful              | 2018-07-30 15:11:29.103-04 | 172.20.1.1   | 
(23 rows)

opennms=> select ipaddr,ismanaged,issnmpprimary from ipinterface where nodeid=6;
    ipaddr    | ismanaged | issnmpprimary 
--------------+-----------+---------------
 172.20.2.1   | M         | N
 172.20.1.1   | M         | P
 128.0.1.16   | U         | N
 7x.xx.xx.xx  | U         | N
 172.20.11.1  | U         | N
 10.0.0.6     | U         | N
 10.0.0.16    | U         | N
 128.0.0.4    | U         | N
 10.0.0.1     | U         | N
 128.0.0.6    | U         | N
 128.0.0.1    | U         | N
 6x.xx.xx.xx  | U         | N
 172.20.12.1  | U         | N
 172.20.14.1  | U         | N
 172.20.13.1  | U         | N
(15 rows)

opennms=> select servicename,ipaddr,status from ifservices s left join ipinterface p on s.ipinterfaceid = p.id left join service ss on ss.serviceid=s.serviceid where nodeid=6;
 servicename |   ipaddr   | status 
-------------+------------+--------
 ICMP        | 172.20.1.1 | A
 SNMP        | 172.20.1.1 | A
 SSH         | 172.20.1.1 | A
(3 rows)
```

As a side note:

```
[root@horizon ~]# ping 172.20.2.1
PING 172.20.2.1 (172.20.2.1) 56(84) bytes of data.
From 172.20.12.1 icmp_seq=1 Destination Net Unreachable
From 172.20.12.1 icmp_seq=2 Destination Net Unreachable
^C
--- 172.20.2.1 ping statistics ---
2 packets transmitted, 0 received, +2 errors, 100% packet loss, time 1004ms
```

As you can see, it is expected that nothing is discovered on `172.20.2.1`, but when using a reachable IP it will work as expected. In fact, I've replaced that IP the 2 public IPs, and ICMP was discovered. I invite you to try it out.

The key here is that this simple optional provider offers a functionality that it could be hard to implement within OpenNMS.

## Future Enhancements

* Add a configuration file to selectively apply the policy on certain requisitions and/or nodes.
