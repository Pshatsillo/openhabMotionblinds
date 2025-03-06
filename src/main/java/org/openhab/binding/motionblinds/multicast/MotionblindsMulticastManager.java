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
package org.openhab.binding.motionblinds.multicast;

import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.motionblinds.dto.CurtainMotor;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.net.NetworkAddressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The {@link MotionblindsMulticastManager} is responsible for multicast service
 * handlers.
 *
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
public class MotionblindsMulticastManager {
    private final Logger logger = LoggerFactory.getLogger(MotionblindsMulticastManager.class);
    private static final int targetPort = 32100;
    private BlockingQueue<String> receiveQueue = new LinkedBlockingQueue<>(100);
    private BlockingQueue<Pack> sendQueue = new LinkedBlockingQueue<>(50);
    private static final String multicastGroup = "238.0.0.18";
    private static final int bindPort = 32101;
    // @Nullable
    // private InetAddress broadcastAddress;
    private ArrayList<Future<?>> tasks = new ArrayList<>();
    @Nullable
    private ExecutorService threadBool;
    private boolean isStart = false;
    private final Object monitor = new Object();
    @Nullable
    private MulticastSocket multicastSocket;
    CurtainMotor curtainMotor = new CurtainMotor();
    public static List<NetworkInterface> interfacesAddresses = new ArrayList<>();
    private final NetworkAddressService networkAddressService;
    private boolean stop = false;
    private @Nullable ScheduledFuture<?> multicastStartJob;
    private @Nullable ScheduledFuture<?> multicastSendJob;
    private @Nullable ScheduledFuture<?> multicastReceiveJob;
    private @Nullable ScheduledFuture<?> multicastConsumJob;
    protected final ScheduledExecutorService scheduler = ThreadPoolManager
            .getScheduledPool(ThreadPoolManager.THREAD_POOL_NAME_COMMON);

    public MotionblindsMulticastManager(NetworkAddressService networkAddressService) {
        this.networkAddressService = networkAddressService;
    }

    public void start() {
        getNetworkInterface();
        ScheduledFuture<?> multicastStartJob = this.multicastStartJob;
        if (multicastStartJob == null || multicastStartJob.isCancelled()) {
            this.multicastStartJob = scheduler.schedule(() -> {
                try {
                    final String primaryIpv4HostAddress = this.networkAddressService.getPrimaryIpv4HostAddress();
                    if (primaryIpv4HostAddress != null) {
                        NetworkInterface netIF = NetworkInterface
                                .getByInetAddress(InetAddress.getByName(primaryIpv4HostAddress));
                        InetSocketAddress address = new InetSocketAddress(bindPort);
                        this.multicastSocket = new MulticastSocket(address);
                        final MulticastSocket msock = this.multicastSocket;
                        if (msock != null) {
                            msock.setBroadcast(true);
                            msock.setTimeToLive(5);
                            msock.setNetworkInterface(netIF);
                            InetSocketAddress bcAddress = new InetSocketAddress(multicastGroup, bindPort);
                            msock.joinGroup(bcAddress, netIF);
                            msock.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, false);
                            this.isStart = true;
                            logger.debug("multicast start ok ! interface = {}, bind port = {}, group = {}", netIF,
                                    bindPort, multicastGroup);
                        }
                    } else {
                        logger.error("Cannot get primaryIpv4HostAddress");
                    }
                } catch (Exception e) {
                    logger.error("Error starting multicast {}", e.getLocalizedMessage());
                    isStart = false;
                }
            }, 5, TimeUnit.MILLISECONDS);
            this.receive();
            this.send();
        }
    }

    public void send() {
        ScheduledFuture<?> multicastSendJob = this.multicastSendJob;
        if (multicastSendJob == null || multicastSendJob.isCancelled()) {
            this.multicastSendJob = scheduler.schedule(() -> {
                InetSocketAddress inetSocketAddress = new InetSocketAddress(multicastGroup, targetPort);
                while (multicastSocket != null) {
                    try {
                        Pack pack = sendQueue.take();
                        byte[] bytes = pack.jsonData.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length, inetSocketAddress);
                        int i = pack.sendCount;
                        final MulticastSocket msock = multicastSocket;
                        if (msock != null) {
                            for (int i2 = 0; i2 < i; i2++) {
                                msock.send(datagramPacket);
                                Thread.sleep(500);
                            }
                        } else {
                            logger.error("MulticastSocket is null");
                        }
                        if (!pack.jsonData.isEmpty()) {
                            logger.trace("sending request: {}", pack.jsonData);
                        } else {
                            logger.error("Empty request");
                        }
                    } catch (Exception e) {
                        logger.error("Cannot send data, restarting multicast");
                        if (isStart) {
                            if (!stop) {
                                try {
                                    Thread.sleep(1000L);
                                } catch (Exception unused) {
                                }
                                isStart = false;
                                start();
                            }
                        }
                        return;
                    }
                }
            }, 1, TimeUnit.SECONDS);
        }
    }

    public void receive() {
        ScheduledFuture<?> multicastReceiveJob = this.multicastReceiveJob;
        if (multicastReceiveJob == null || multicastReceiveJob.isCancelled()) {
            this.multicastReceiveJob = scheduler.schedule(() -> {
                byte[] bArr = new byte[102400];
                DatagramPacket datagramPacket = new DatagramPacket(bArr, 102400);
                while (multicastSocket != null) {
                    try {
                        final MulticastSocket msock = multicastSocket;
                        if (msock != null) {
                            msock.receive(datagramPacket);
                            int length = datagramPacket.getLength();
                            byte[] bArr2 = new byte[length];
                            System.arraycopy(bArr, 0, bArr2, 0, length);
                            String str = new String(bArr2, 0, length, StandardCharsets.UTF_8);
                            receiveQueue.put(str);
                        }
                    } catch (Exception e) {
                        logger.error("receive data error {}", e.getLocalizedMessage());
                        if (isStart) {
                            try {
                                Thread.sleep(1000L);
                            } catch (Exception ignored) {
                            }
                            isStart = false;
                            start();
                        }
                        return;
                    }
                }
            }, 1, TimeUnit.SECONDS);
        }
        consumData();
    }

    private void consumData() {
        ScheduledFuture<?> multicastConsumJob = this.multicastConsumJob;
        if (multicastConsumJob == null || multicastConsumJob.isCancelled()) {
            this.multicastConsumJob = scheduler.schedule(() -> {
                while (isStart) {
                    try {
                        String data = receiveQueue.take();
                        if (!data.isEmpty()) {
                            dealWithData(JsonParser.parseString(data).getAsJsonObject());
                        }
                    } catch (Exception e) {
                        logger.error("receiveQueue data error {}", e.getLocalizedMessage());
                    }
                }
            }, 1, TimeUnit.SECONDS);
        }
    }

    public void dealWithData(JsonObject data) {
        // data = new JSONObject(dta);
        String msgType = data.get("msgType").getAsString();
        if ("Heartbeat".equals(msgType)) {
            curtainMotor.heartBeat(data);
        } else {
            // System.out.println("dealWithData: " + data);
        }
    }

    public void getNetworkInterface() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                @Nullable
                NetworkInterface nextElement = networkInterfaces.nextElement();
                try {
                    if (nextElement.isUp() && !nextElement.isLoopback()) {
                        for (InterfaceAddress ifaceAddr : nextElement.getInterfaceAddresses()) {
                            if (ifaceAddr.getAddress() instanceof Inet4Address) {
                                interfacesAddresses.add(nextElement);
                            }
                        }
                    }
                } catch (SocketException e) {
                    logger.debug("Exception while getting information for network interface '{}': '{}'",
                            nextElement.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error getting interfaces {}", e.getLocalizedMessage());
        }
    }

    public void stop() {
        final MulticastSocket msock = this.multicastSocket;
        if (msock != null) {
            msock.close();
        }
        this.stop = true;
    }

    public class Pack {
        public String jsonData;
        public int sendCount;

        public Pack(String str) {
            this.sendCount = 3;
            this.jsonData = str;
        }

        public Pack(int i, String str) {
            // int unused = 3;
            this.jsonData = str;
            this.sendCount = i;
        }
    }

    public void send(String str) {
        send(str, 3);
    }

    public void send(String str, int i) {
        this.sendQueue.offer(new Pack(i, str));
    }
}
