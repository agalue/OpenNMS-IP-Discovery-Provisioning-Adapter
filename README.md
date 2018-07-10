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

## Future Enhancements

* Add a configuration file to selectively apply the policy on certain requisitions and/or nodes.
