package org.it4y.demo;

import org.it4y.net.protocols.IP.ICMP.ICMPPacket;
import org.it4y.net.protocols.IP.*;
import org.it4y.net.protocols.IP.UDP.UDPPacket;
import org.it4y.net.tuntap.TunDevice;
import org.it4y.util.Hexdump;

import java.nio.ByteBuffer;

/**
 * Created by luc on 12/27/13.
 */
public class TunTapInterfaceListener extends TestRunner {
    private String dev = null;
    private int mtu = 0;
    private ByteBuffer bbuffer;
    private TunDevice tundev;
    private static long last = 0;
    private static long cnt = 0;
    private static long bits = 0;
    private boolean debug = false;
    private long pktcnt = 0;

    public TunTapInterfaceListener(String name, int mtu) {
        super("tuntapListener-" + name);
        this.dev = name;
        this.mtu = mtu;
        tundev = new TunDevice(name);
        bbuffer = ByteBuffer.allocateDirect(mtu);
    }

    public void hexDumpIn(ByteBuffer buffer, int size) {
        if (debug) {
            System.out.println(System.currentTimeMillis() + " (" + size + ") >" + Hexdump.bytesToHex(buffer, size));
        }
    }

    public void hexDumpOut(ByteBuffer buffer, int size) {
        if (debug) {
            System.out.println(System.currentTimeMillis() + " (" + size + ") <" + Hexdump.bytesToHex(buffer, size));
        }
    }

    public void run() {
        try {
            tundev.open();
        } catch (Exception e) {
            System.out.println("ooeps..." + e.getMessage());
            return;
        }
        running = true;
        while (running) {
            try {
                //we must clear else we get issues
                bbuffer.clear();
                int size = tundev.readByteBuffer(bbuffer); //this will block until a packet is available
                pktcnt++;
                hexDumpIn(bbuffer, size);
                IpPacket ip = IPFactory.processRawPacket(bbuffer, size);
                if (ip != null) {
                    bits = bits + size;
                    cnt++;
                    ByteBuffer data = ip.getPayLoad();
                    ByteBuffer header = ip.getHeader();
                    //System.out.println(ip.getClass().getCanonicalName()+":"+ip.getRawSize()+" "+header.position()+" "+header.limit()+" "+header.capacity()+" - "+data.position()+" "+data.limit()+" "+data.capacity());
                    if (ip.getProtocol() == ip.ICMP) {
                        if (((ICMPPacket) ip).isEchoRequest()) {
                            ((ICMPPacket) ip).convertToEchoReply();
                            //write raw packet back to network
                            tundev.writeByteBuffer(ip.getRawPacket(), ip.getRawSize());
                            //System.out.println(ip.toString());
                            hexDumpOut(ip.getRawPacket(), ip.getRawSize());
                        }
                    } else if (ip.getProtocol() == ip.UDP) {
                        //we must reset Buffer before manipulating it !!
                        System.out.println("IP: " + ip.toString());
                        ip.resetBuffer();
                        //echo packet back to owner
                        ((UDPPacket) ip).swapSourceDestination();
                        ((UDPPacket) ip).updateChecksum();
                        ((UDPPacket) ip).swapSourceDestinationPort();
                        tundev.writeByteBuffer(ip.getRawPacket(), ip.getRawSize());
                        hexDumpOut(ip.getRawPacket(), ip.getRawSize());
                    } else if (ip.getProtocol() == ip.TCP) {
                        //we must reset Buffer before manipulating it !!
                        System.out.println(ip.toString());
                    } else {
                        System.out.println(ip.toString());
                    }
                }
            } catch (Throwable t) {
                //hell , it still java so it will break
                System.out.println(t);
                t.printStackTrace();
            }
        }

    }

    public void dumpSpeed() {
        if (bits > 0) {
            System.out.println("goodput: " + String.format("%.3f mbit/sec", (double) (bits * 8) / (1024 * 1024)) + " bytes: "+bits+" "+ cnt + " pkts: " + pktcnt);
        }
        bits = 0;
        cnt = 0;
        last = System.currentTimeMillis();
    }
}
