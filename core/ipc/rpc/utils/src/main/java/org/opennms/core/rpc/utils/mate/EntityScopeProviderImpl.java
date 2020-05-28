/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
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

package org.opennms.core.rpc.utils.mate;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.dao.api.IpInterfaceDao;
import org.opennms.netmgt.dao.api.MonitoredServiceDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.model.OnmsAssetRecord;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsMetaData;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.base.Strings;

public class EntityScopeProviderImpl implements EntityScopeProvider {

    @Autowired
    private NodeDao nodeDao;

    @Autowired
    private IpInterfaceDao ipInterfaceDao;

    @Autowired
    private MonitoredServiceDao monitoredServiceDao;

    @Autowired
    private SessionUtils sessionUtils;

    @Override
    public Scope getScopeForNode(final Integer nodeId) {
        if (nodeId == null) {
            return EmptyScope.EMPTY;
        }

        final Scope metaDataScope = this.sessionUtils.withReadOnlyTransaction(() -> {
            final OnmsNode node = nodeDao.get(nodeId);
            if (node == null) {
                return EmptyScope.EMPTY;
            }

            return new FallbackScope(transform(node.getMetaData()),
                    new ObjectScope<>(node)
                            .map("node", "label", (n) -> Optional.ofNullable(n.getLabel()))
                            .map("node", "foreign-source", (n) -> Optional.ofNullable(n.getForeignSource()))
                            .map("node", "foreign-id", (n) -> Optional.ofNullable(n.getForeignId()))
                            .map("node", "netbios-domain", (n) -> Optional.ofNullable(n.getNetBiosDomain()))
                            .map("node", "netbios-name", (n) -> Optional.ofNullable(n.getNetBiosName()))
                            .map("node", "os", (n) -> Optional.ofNullable(n.getOperatingSystem()))
                            .map("node", "sys-name", (n) -> Optional.ofNullable(n.getSysName()))
                            .map("node", "sys-location", (n) -> Optional.ofNullable(n.getSysLocation()))
                            .map("node", "sys-contact", (n) -> Optional.ofNullable(n.getSysContact()))
                            .map("node", "sys-description", (n) -> Optional.ofNullable(n.getSysDescription()))
                            .map("node", "sys-object-id", (n) -> Optional.ofNullable(n.getSysObjectId()))
                            .map("node", "location", (n) -> Optional.ofNullable(n.getLocation().getLocationName()))
                            .map("node", "area", (n) -> Optional.ofNullable(n.getLocation().getMonitoringArea())));
        });
        return metaDataScope;
    }

    @Override
    public Scope getScopeForAssets(final Integer nodeId) {
        if (nodeId == null) {
            return EmptyScope.EMPTY;
        }

        final Scope metaDataScope = this.sessionUtils.withReadOnlyTransaction(() -> {
            final OnmsNode node = nodeDao.get(nodeId);
            if (node == null) {
                return EmptyScope.EMPTY;
            }

            return new FallbackScope(transform(node.getMetaData()),
                    new ObjectScope<>(node)
                            .map("asset", "category" , (n) -> Optional.ofNullable(node.getAssetRecord()).map(OnmsAssetRecord::getCategory))
                            .map("asset", "vendor" , (n) -> Optional.ofNullable(node.getAssetRecord()).map(OnmsAssetRecord::getVendor))
                            .map("asset", "manufacturer" , (n) -> Optional.ofNullable(node.getAssetRecord()).map(OnmsAssetRecord::getManufacturer))
                            .map("asset", "vendor" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getVendor))
                            .map("asset", "model-number" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getModelNumber))
                            .map("asset", "serial-number" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getSerialNumber))
                            .map("asset", "description" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getDescription))
                            .map("asset", "circuit-id" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getCircuitId))
                            .map("asset", "asset-number" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getAssetNumber))
                            .map("asset", "operating-system" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getOperatingSystem))
                            .map("asset", "rack" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getRack))
                            .map("asset", "slot" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getSlot))
                            .map("asset", "port" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getPort))
                            .map("asset", "region" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getRegion))
                            .map("asset", "division" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getDivision))
                            .map("asset", "department" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getDepartment))
                            .map("asset", "building" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getBuilding))
                            .map("asset", "floor" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getFloor))
                            .map("asset", "room" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getRoom))
                            .map("asset", "vendor-phone" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getVendorPhone))
                            .map("asset", "vendor-fax" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getVendorFax))
                            .map("asset", "vendor-asset-number" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getVendorAssetNumber))
                            .map("asset", "username" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getUsername))
                            .map("asset", "password" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getPassword))
                            .map("asset", "enable" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getEnable))
                            .map("asset", "connection" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getConnection))
                            .map("asset", "autoenable" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getAutoenable))
                            .map("asset", "last-modified-by" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getLastModifiedBy))
                            .map("asset", "last-modified-date", (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getLastModifiedDate).map(Date::toString))
                            .map("asset", "date-installed" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getDateInstalled))
                            .map("asset", "lease" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getLease))
                            .map("asset", "lease-expires" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getLeaseExpires))
                            .map("asset", "support-phone" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getSupportPhone))
                            .map("asset", "maintcontract" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getMaintcontract))
                            .map("asset", "maint-contract-expiration" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getMaintContractExpiration))
                            .map("asset", "display-category" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getDisplayCategory))
                            .map("asset", "notify-category" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getNotifyCategory))
                            .map("asset", "poller-category" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getPollerCategory))
                            .map("asset", "threshold-category" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getThresholdCategory))
                            .map("asset", "comment" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getComment))
                            .map("asset", "cpu" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getCpu))
                            .map("asset", "ram" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getRam))
                            .map("asset", "storagectrl" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getStoragectrl))
                            .map("asset", "hdd1" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getHdd1))
                            .map("asset", "hdd2" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getHdd2))
                            .map("asset", "hdd3" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getHdd3))
                            .map("asset", "hdd4" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getHdd4))
                            .map("asset", "hdd5" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getHdd5))
                            .map("asset", "hdd6" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getHdd6))
                            .map("asset", "numpowersupplies" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getNumpowersupplies))
                            .map("asset", "inputpower" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getInputpower))
                            .map("asset", "additionalhardware" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getAdditionalhardware))
                            .map("asset", "admin" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getAdmin))
                            .map("asset", "snmpcommunity" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getSnmpcommunity))
                            .map("asset", "rackunitheight" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getRackunitheight))
                            .map("asset", "managed-object-type" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getManagedObjectType))
                            .map("asset", "managed-object-instance" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getManagedObjectInstance))
                            .map("asset", "geolocation", (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getGeolocation).map(Object::toString))
                            .map("asset", "vmware-managed-object-id" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getVmwareManagedObjectId))
                            .map("asset", "vmware-managed-entity-type" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getVmwareManagedEntityType))
                            .map("asset", "vmware-management-server" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getVmwareManagementServer))
                            .map("asset", "vmware-topology-info" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getVmwareTopologyInfo))
                            .map("asset", "vmware-state" , (n) -> Optional.ofNullable(n.getAssetRecord()).map(OnmsAssetRecord::getVmwareState)));
        });
        return metaDataScope;
    }

    @Override
    public Scope getScopeForInterface(final Integer nodeId, final String ipAddress) {
        if (nodeId == null || Strings.isNullOrEmpty(ipAddress)) {
            return EmptyScope.EMPTY;
        }

        final Scope metaDataScope = this.sessionUtils.withReadOnlyTransaction(() -> {
            final OnmsIpInterface ipInterface = this.ipInterfaceDao.findByNodeIdAndIpAddress(nodeId, ipAddress);
            if (ipInterface == null) {
                return EmptyScope.EMPTY;
            }

            return new FallbackScope(transform(ipInterface.getMetaData()),
                    new ObjectScope<>(ipInterface)
                            .map("interface", "hostname", (i) -> Optional.ofNullable(i.getIpHostName()))
                            .map("interface", "address", (i) -> Optional.ofNullable(i.getIpAddress()).map(InetAddressUtils::toIpAddrString))
                            .map("interface", "netmask", (i) -> Optional.ofNullable(i.getNetMask()).map(InetAddressUtils::toIpAddrString))
                            .map("interface", "if-index", (i) -> Optional.ofNullable(i.getIfIndex()).map(Object::toString))
                            .map("interface", "if-alias", (i) -> Optional.ofNullable(i.getSnmpInterface()).map(OnmsSnmpInterface::getIfAlias))
                            .map("interface", "if-description", (i) -> Optional.ofNullable(i.getSnmpInterface()).map(OnmsSnmpInterface::getIfDescr))
                            .map("interface", "phy-addr", (i) -> Optional.ofNullable(i.getSnmpInterface()).map(OnmsSnmpInterface::getPhysAddr))
            );
        });

        return metaDataScope;
    }

    @Override
    public Scope getScopeForService(final Integer nodeId, final InetAddress ipAddress, final String serviceName) {
        if (nodeId == null || ipAddress == null || Strings.isNullOrEmpty(serviceName)) {
            return EmptyScope.EMPTY;
        }

        final Scope metaDataScope = this.sessionUtils.withReadOnlyTransaction(() -> {
            final OnmsMonitoredService monitoredService = this.monitoredServiceDao.get(nodeId, ipAddress, serviceName);
            if (monitoredService == null) {
                return EmptyScope.EMPTY;
            }

            return new FallbackScope(transform(monitoredService.getMetaData()),
                    new ObjectScope<>(monitoredService)
                            .map("service", "name", (s) -> Optional.of(s.getServiceName()))
            );
        });

        return metaDataScope;
    }

    private static MapScope transform(final Collection<OnmsMetaData> metaData) {
        final Map<ContextKey, String> map = metaData.stream()
                .collect(Collectors.toMap(e -> new ContextKey(e.getContext(), e.getKey()), e -> e.getValue()));
        return new MapScope(map);
    }

    public void setNodeDao(NodeDao nodeDao) {
        this.nodeDao = Objects.requireNonNull(nodeDao);
    }

    public void setIpInterfaceDao(IpInterfaceDao ipInterfaceDao) {
        this.ipInterfaceDao = Objects.requireNonNull(ipInterfaceDao);
    }

    public void setMonitoredServiceDao(MonitoredServiceDao monitoredServiceDao) {
        this.monitoredServiceDao = Objects.requireNonNull(monitoredServiceDao);
    }

    public void setSessionUtils(SessionUtils sessionUtils) {
        this.sessionUtils = Objects.requireNonNull(sessionUtils);
    }
}
