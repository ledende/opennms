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

package org.opennms.netmgt.remotepollerng;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opennms.core.utils.InetAddressUtils;
import org.opennms.core.utils.LocationUtils;
import org.opennms.netmgt.collection.api.CollectionAgentFactory;
import org.opennms.netmgt.collection.api.CollectionResource;
import org.opennms.netmgt.collection.api.PersisterFactory;
import org.opennms.netmgt.collection.api.ServiceParameters;
import org.opennms.netmgt.collection.dto.CollectionAgentDTO;
import org.opennms.netmgt.collection.support.builder.AbstractResource;
import org.opennms.netmgt.collection.support.builder.CollectionSetBuilder;
import org.opennms.netmgt.collection.support.builder.Resource;
import org.opennms.netmgt.config.PollerConfig;
import org.opennms.netmgt.config.poller.Package;
import org.opennms.netmgt.config.poller.Parameter;
import org.opennms.netmgt.config.poller.Service;
import org.opennms.netmgt.daemon.SpringServiceDaemon;
import org.opennms.netmgt.dao.api.LocationSpecificStatusDao;
import org.opennms.netmgt.dao.api.MonitoredServiceDao;
import org.opennms.netmgt.dao.api.MonitoringLocationDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.dao.support.DistributedStatusResourceType;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.events.api.annotations.EventHandler;
import org.opennms.netmgt.events.api.annotations.EventListener;
import org.opennms.netmgt.events.api.model.IEvent;
import org.opennms.netmgt.model.OnmsLocationSpecificStatus;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.ResourcePath;
import org.opennms.netmgt.model.ServiceSelector;
import org.opennms.netmgt.model.events.EventBuilder;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.opennms.netmgt.poller.LocationAwarePollerClient;
import org.opennms.netmgt.poller.PollStatus;
import org.opennms.netmgt.poller.ServiceMonitor;
import org.opennms.netmgt.poller.ServiceMonitorLocator;
import org.opennms.netmgt.poller.ServiceMonitorRegistry;
import org.opennms.netmgt.poller.support.DefaultServiceMonitorRegistry;
import org.opennms.netmgt.rrd.RrdRepository;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.listeners.SchedulerListenerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

/*
 * TODO:
 *  * Support dynamically scheduling/rescheduling services
 */
@EventListener(name=RemotePollerd.NAME, logPrefix=RemotePollerd.LOG_PREFIX)
public class RemotePollerd implements SpringServiceDaemon {
    private static final Logger LOG = LoggerFactory.getLogger(RemotePollerd.class);

    public static final String NAME = "RemotePollerNG";

    public static final String LOG_PREFIX = "remotepollerd";

    private static final ServiceMonitorRegistry serviceMonitorRegistry = new DefaultServiceMonitorRegistry();

    private final SessionUtils sessionUtils;
    private final MonitoringLocationDao monitoringLocationDao;
    private final PollerConfig pollerConfig;
    private final MonitoredServiceDao monSvcDao;
    private final LocationAwarePollerClient locationAwarePollerClient;
    private final LocationSpecificStatusDao locationSpecificStatusDao;
    private final CollectionAgentFactory collectionAgentFactory;
    private final PersisterFactory persisterFactory;
    private final EventForwarder eventForwarder;

    private Scheduler scheduler;

    public RemotePollerd(final SessionUtils sessionUtils,
                         final MonitoringLocationDao monitoringLocationDao,
                         final PollerConfig pollerConfig,
                         final MonitoredServiceDao monSvcDao,
                         final LocationAwarePollerClient locationAwarePollerClient,
                         final LocationSpecificStatusDao locationSpecificStatusDao,
                         final CollectionAgentFactory collectionAgentFactory,
                         final PersisterFactory persisterFactory,
                         final EventForwarder eventForwarder) {
        this.sessionUtils = Objects.requireNonNull(sessionUtils);
        this.monitoringLocationDao = Objects.requireNonNull(monitoringLocationDao);
        this.pollerConfig = Objects.requireNonNull(pollerConfig);
        this.monSvcDao = Objects.requireNonNull(monSvcDao);
        this.locationAwarePollerClient = Objects.requireNonNull(locationAwarePollerClient);
        this.locationSpecificStatusDao = Objects.requireNonNull(locationSpecificStatusDao);
        this.collectionAgentFactory = Objects.requireNonNull(collectionAgentFactory);
        this.persisterFactory = Objects.requireNonNull(persisterFactory);
        this.eventForwarder = Objects.requireNonNull(eventForwarder);
    }

    @Override
    public void start() throws Exception {
        this.scheduler = new StdSchedulerFactory().getScheduler();
        this.scheduler.start();
        this.scheduler.getListenerManager().addSchedulerListener(new SchedulerListenerSupport() {
            @Override
            public void schedulerError(String msg, SchedulerException cause) {
                LOG.error("Unexpected error during poll: {}", msg, cause);
            }
        });

        this.scheduleAllServices();
    }

    @Override
    public void destroy() throws Exception {
        if (this.scheduler != null) {
            this.scheduler.shutdown();
            this.scheduler = null;
        }
    }

    @EventHandler(uei = EventConstants.RELOAD_DAEMON_CONFIG_UEI)
    public void handleReloadEvent(IEvent e) {
//        DaemonTools.handleReloadEvent(e, RemotePollerd.NAME, (event) -> handleConfigurationChanged());
    }

    /**
     * One or more polling packages may be assigned to monitoring locations via the UI
     * From there we retrieve the matching packages stored in etc/poller-configuration.xml
     * Using the package definition, we retrieve all matching ifservices
     */
    public void scheduleAllServices() {
        final Map<String, List<RemotePolledService>> servicesByPackage = new HashMap<>();

        LOG.info("Scheduling all services...");
        sessionUtils.withReadOnlyTransaction(() -> {
            for (OnmsMonitoringLocation loc : monitoringLocationDao.findAll()) {
                final List<String> pollingPackageNames = loc.getPollingPackageNames();
                LOG.debug("Location '{}' has polling packages: {}", loc.getLocationName(), pollingPackageNames);
                for (String pollingPackageName : pollingPackageNames) {
                    final List<RemotePolledService> servicesForPackage = servicesByPackage.computeIfAbsent(pollingPackageName, (pkgName) -> {
                        final Package pkg = pollerConfig.getPackage(pollingPackageName);
                        if (pkg == null) {
                            LOG.warn("Polling package '{}' is associated with location '{}', but the package was not found." +
                                    " Using an empty set of services.", pollingPackageName, loc.getLocationName());
                            return Collections.emptyList();
                        }
                        return getServicesForPackage(pkg);
                    });

                    for (RemotePolledService polledService : servicesForPackage) {
                        try {
                            scheduleService(loc.getLocationName(), polledService);
                        } catch (SchedulerException e) {
                            LOG.warn("Failed to schedule {}.", polledService, e);
                        }
                    }
                }

            }
            return null;
        });
    }

    private static String getJobIdentity(RemotePolledService polledService) {
        return String.format("job-%s-%s", polledService.getPkg().getName(), polledService.getMonSvc().getId());
    }

    private static String getTriggerIdentity(RemotePolledService polledService) {
        return String.format("trigger-%s-%s", polledService.getPkg().getName(), polledService.getMonSvc().getId());
    }

    private void scheduleService(String locationName, RemotePolledService polledService) throws SchedulerException {
        JobDetail job = JobBuilder
                .newJob(RemotePollJob.class)
                .withIdentity(getJobIdentity(polledService), locationName)
                .build();

        job.getJobDataMap().put(RemotePollJob.LOCATION_NAME, locationName);
        job.getJobDataMap().put(RemotePollJob.POLLED_SERVICE, polledService);
        job.getJobDataMap().put(RemotePollJob.REMOTE_POLLER_BACKEND, this);

        Trigger trigger = TriggerBuilder
                .newTrigger()
                .withIdentity(getTriggerIdentity(polledService), locationName)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(polledService.getService().getInterval())
                        .repeatForever())
                .build();

        LOG.debug("Scheduling service named {} at location {} with interval {}ms", polledService.getService().getName(),
                locationName, polledService.getService().getInterval());
        scheduler.scheduleJob(job, trigger);
    }

    public LocationAwarePollerClient getLocationAwarePollerClient() {
        return locationAwarePollerClient;
    }

    private List<RemotePolledService> getServicesForPackage(Package pkg) {
        final ServiceSelector selector = pollerConfig.getServiceSelectorForPackage(pkg);
        final Collection<OnmsMonitoredService> services = monSvcDao.findMatchingServices(selector);
        LOG.debug("Found {} services in polling package {}", services.size(), pkg.getName());
        final List<RemotePolledService> polledServices = new ArrayList<>(services.size());
        for (final OnmsMonitoredService monSvc : services) {
            final Service serviceConfig = pollerConfig.getServiceInPackage(monSvc.getServiceName(), pkg);

            // Now locate the associated monitor
            // TODO: We don't need to repeat this for every package
            ServiceMonitor serviceMonitor = null;
            for (final ServiceMonitorLocator locator : pollerConfig.getServiceMonitorLocators()) {
                if (serviceConfig.getName().equals(locator.getServiceName())) {
                    serviceMonitor  = locator.getServiceMonitor(serviceMonitorRegistry);
                }
            }
            if (serviceMonitor == null) {
                LOG.warn("No monitor found for service: {}. Skipping.", serviceConfig.getName());
                continue;
            }

            LOG.debug("Found service {} in package {}", serviceConfig.getName(), pkg.getName());
            polledServices.add(new RemotePolledService(monSvc, pkg, serviceConfig, serviceMonitor));
        }
        return polledServices;
    }

    protected void reportResult(final String locationName, final RemotePolledService polledService, final PollStatus pollResult) {
        final OnmsMonitoringLocation location = this.monitoringLocationDao.get(locationName);

        final OnmsLocationSpecificStatus status = new OnmsLocationSpecificStatus();
        status.setLocation(location);
        status.setMonitoredService(polledService.getMonSvc());
        status.setPollResult(pollResult);

        this.locationSpecificStatusDao.saveStatusChange(status);

        try {
            if (pollResult.getResponseTime() != null) {
                saveResponseTimeData(locationName, polledService.getMonSvc(), pollResult, polledService.getPkg());
            }
        } catch (final Exception e) {
            LOG.error("Unable to save response time data for location {}, monitored service ID {}.", locationSpecificStatusDao, polledService.getMonSvc().getId(), e);
        }

        try {
            sendRegainedOrLostServiceEvent(locationName, polledService.getMonSvc(), pollResult);
        } catch (final Exception e) {
            LOG.error("Unable to save result for location {}, monitored service ID {}.", locationSpecificStatusDao, polledService.getMonSvc().getId(), e);
        }
    }

    private static EventBuilder createEventBuilder(final String locationName, final String uei) {
        final EventBuilder eventBuilder = new EventBuilder(uei, "PollerBackEnd")
                .addParam(EventConstants.PARM_LOCATION, locationName);
        return eventBuilder;
    }

    private void sendRegainedOrLostServiceEvent(final String locationName, final OnmsMonitoredService monSvc, final PollStatus pollResult) {
        final String uei = pollResult.isAvailable() ? EventConstants.REMOTE_NODE_REGAINED_SERVICE_UEI : EventConstants.REMOTE_NODE_LOST_SERVICE_UEI;

        final EventBuilder builder = createEventBuilder(locationName, uei)
                .setMonitoredService(monSvc);

        if (!pollResult.isAvailable() && pollResult.getReason() != null) {
            builder.addParam(EventConstants.PARM_LOSTSERVICE_REASON, pollResult.getReason());
        }

        eventForwarder.sendNow(builder.getEvent());
    }

    public void saveResponseTimeData(final String locationName, final OnmsMonitoredService monSvc, final PollStatus pollStatus, final Package pkg) {
        final String svcName = monSvc.getServiceName();
        final Service svc = this.pollerConfig.getServiceInPackage(svcName, pkg);

        final String residentLocationName = monSvc.getIpInterface().getNode().getLocation().getLocationName();

        String dsName = getServiceParameter(svc, "ds-name");
        if (dsName == null) {
            dsName = PollStatus.PROPERTY_RESPONSE_TIME;
        }

        String rrdBaseName = getServiceParameter(svc, "rrd-base-name");
        if (rrdBaseName == null) {
            rrdBaseName = dsName;
        }

        final String rrdRepository = getServiceParameter(svc, "rrd-repository");
        if (rrdRepository == null) {
            return;
        }

        // TODO: Apply thresholding

        final RrdRepository repository = new RrdRepository();
        repository.setStep(this.pollerConfig.getStep(pkg));
        repository.setHeartBeat(repository.getStep() * 2);
        repository.setRraList(this.pollerConfig.getRRAList(pkg));
        repository.setRrdBaseDir(new File(rrdRepository));

        // Prefer ds-name over "response-time" for primary response-time value
        final Map<String, Number> properties = Maps.newHashMap(pollStatus.getProperties());
        if (!properties.containsKey(dsName) && properties.containsKey(PollStatus.PROPERTY_RESPONSE_TIME)) {
            properties.put(dsName, properties.get(PollStatus.PROPERTY_RESPONSE_TIME));
            properties.remove(PollStatus.PROPERTY_RESPONSE_TIME);
        }

        // Build collection agent
        final CollectionAgentDTO agent = new CollectionAgentDTO();
        agent.setAddress(monSvc.getIpAddress());
        agent.setForeignId(monSvc.getForeignId());
        agent.setForeignSource(monSvc.getForeignSource());
        agent.setNodeId(monSvc.getNodeId());
        agent.setNodeLabel(monSvc.getIpInterface().getNode().getLabel());
        agent.setLocationName(locationName);
        agent.setStorageResourcePath(ResourcePath.get(LocationUtils.isDefaultLocationName(residentLocationName)
                                                      ? ResourcePath.get()
                                                      : ResourcePath.get(ResourcePath.sanitize(residentLocationName)),
                                                      InetAddressUtils.str(monSvc.getIpAddress())));
        agent.setStoreByForeignSource(false);

        // Create collection set from response times as gauges and persist
        final CollectionSetBuilder collectionSetBuilder = new CollectionSetBuilder(agent);
        final RemoteLatencyResource resource = new RemoteLatencyResource(locationName, InetAddressUtils.str(monSvc.getIpAddress()), svcName);
        for (final Map.Entry<String, Number> e: properties.entrySet()) {
            final String key = PollStatus.PROPERTY_RESPONSE_TIME.equals(e.getKey())
                               ? dsName
                               : e.getKey();

            collectionSetBuilder.withGauge(resource, rrdBaseName, key, e.getValue());
        }

        collectionSetBuilder.build()
                            .visit(this.persisterFactory.createPersister(new ServiceParameters(Collections.emptyMap()),
                                                                         repository,
                                                                         false,
                                                                         true,
                                                                         true));
    }

    private String getServiceParameter(final Service svc, final String key) {
        for(final Parameter parm : pollerConfig.parameters(svc)) {
            if (key.equals(parm.getKey())) {
                if (parm.getValue() != null) {
                    return parm.getValue();
                } else if (parm.getAnyObject() != null) {
                    return parm.getAnyObject().toString();
                }
            }
        }
        return null;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }

    public static class RemoteLatencyResource extends AbstractResource {

        private final String location;
        private final String address;
        private final String service;

        public RemoteLatencyResource(final String location,
                                     final String address,
                                     final String service) {
            this.location = Objects.requireNonNull(location);
            this.address = Objects.requireNonNull(address);
            this.service = Objects.requireNonNull(service);
        }

        @Override
        public Resource getParent() {
            return null;
        }

        @Override
        public String getTypeName() {
            return DistributedStatusResourceType.TYPE_NAME;
        }

        @Override
        public String getInstance() {
            return String.format("%s[%s]@%s", this.address, this.service, this.location);
        }

        @Override
        public String getUnmodifiedInstance() {
            return this.getInstance();
        }

        @Override
        public String getLabel(final CollectionResource resource) {
            return this.service;
        }

        @Override
        public ResourcePath getPath(final CollectionResource resource) {
            return ResourcePath.get("remote", this.location);
        }
    }
}
