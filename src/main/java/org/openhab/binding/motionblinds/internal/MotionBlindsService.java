/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.motionblinds.internal;

import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.motionblinds.multicast.MotionblindsMulticastManager;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.events.Event;
import org.openhab.core.events.EventSubscriber;
import org.openhab.core.items.events.ItemStateEvent;
import org.openhab.core.net.NetworkAddressService;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MotionBlindsService} is responsible for creating multicast sender and listener
 * handlers.
 *
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
@Component(service = { MotionBlindsService.class,
        EventSubscriber.class }, configurationPid = "org.openhab.motionblinds", property = Constants.SERVICE_PID
                + "=org.openhab.motionblinds")
public class MotionBlindsService implements EventSubscriber {
    private final Logger logger = LoggerFactory.getLogger(MotionBlindsService.class);
    MotionblindsMulticastManager mcm;
    private @Nullable ScheduledFuture<?> refreshPollingJob;
    protected final ScheduledExecutorService scheduler = ThreadPoolManager
            .getScheduledPool(ThreadPoolManager.THREAD_POOL_NAME_COMMON);

    @Activate
    public MotionBlindsService(@Reference NetworkAddressService networkAddressService, ComponentContext context) {
        logger.error("MOTION STARTED");
        mcm = new MotionblindsMulticastManager(networkAddressService);
        mcm.start();
        ScheduledFuture<?> refreshPollingJob = this.refreshPollingJob;
        if (refreshPollingJob == null || refreshPollingJob.isCancelled()) {
            this.refreshPollingJob = scheduler.scheduleWithFixedDelay(this::refresh, 5, 5, TimeUnit.SECONDS);
        }
    }

    private void refresh() {
        mcm.send("{\"\":\"\"}");
    }

    @Override
    public Set<String> getSubscribedEventTypes() {
        return Set.of(ItemStateEvent.TYPE);
    }

    @Override
    public void receive(Event event) {
    }

    @Deactivate
    protected void deactivate() {
        logger.error("DEACTIVATE");
        mcm.stop();
        ScheduledFuture<?> refreshPollingJob = this.refreshPollingJob;
        if (refreshPollingJob != null && !refreshPollingJob.isCancelled()) {
            refreshPollingJob.cancel(true);
            this.refreshPollingJob = null;
        }
    }
}
