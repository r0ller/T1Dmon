package com.bitroller.aapssketch;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.widget.Toast;
import android.content.Context;

import java.lang.Exception;
import java.nio.ByteBuffer;
import java.util.Arrays;

//import org.apache.http.util.ByteArrayBuffer;

public class Meter{
    public static final byte STX=2;
    public static final byte ETX=3;
    public static final byte EOT=4;
    public static final byte ENQ=5;
    public static final byte ACK=6;
    public static final byte LF=10;
    public static final byte CR=13;
    public static final byte NAK=21;
    public static final byte ETB=23;
    public static final byte CAN=24;

    UsbDevice device=null;
    UsbManager manager=null;
    UsbInterface intf=null;
    UsbEndpoint endpointH2D=null, endpointD2H=null;
    UsbDeviceConnection connection=null;
    int bufferMaxLengthD2H=0,bufferMaxLengthH2D=0;
    ByteBuffer bufferD2H,bufferH2D;
    UsbRequest requestD2H,requestH2D;
    Context context;

    public Meter(UsbDevice device,UsbManager manager,Context context) throws Exception{
        int ifNr=device.getInterfaceCount();

        if(device.getVendorId()==6777&&device.getProductId()==25104&&manager!=null){
            this.manager=manager;
            this.device=device;
            this.context=context;
            for(int f=0;f<ifNr;++f){
                intf=device.getInterface(f);
                int epNr=intf.getEndpointCount();
                for(int e=0;e<epNr;++e){
                    UsbEndpoint endpoint = intf.getEndpoint(e);
                    if(endpoint.getType()==UsbConstants.USB_ENDPOINT_XFER_INT){
                        int endpointDirection=endpoint.getDirection();
                        switch(endpointDirection){
                            case UsbConstants.USB_DIR_IN://device to host
                                endpointD2H=endpoint;
                                break;
                            case UsbConstants.USB_DIR_OUT://host to device
                                endpointH2D=endpoint;
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        }
        if(endpointH2D==null||endpointD2H==null){
            throw new Exception("Couldn't get interface endpoints");
        }
    }

    public boolean openConnection(){
        connection=manager.openDevice(device);
        if(connection!=null&&connection.claimInterface(intf,true)==true) return true;
        else return false;
    }

    public void closeRequest(){
        requestD2H.close();
        requestH2D.close();
    }

    public void closeConnection(){
        closeRequest();
        connection.releaseInterface(intf);
        connection.close();
    }

    public void initConnection() throws Exception{

        bufferMaxLengthD2H=endpointD2H.getMaxPacketSize();
        bufferD2H=ByteBuffer.allocate(bufferMaxLengthD2H);
        requestD2H=new UsbRequest();
        requestD2H.initialize(connection,endpointD2H);
        bufferMaxLengthH2D=endpointH2D.getMaxPacketSize();
        bufferH2D=ByteBuffer.allocate(bufferMaxLengthH2D);
        requestH2D=new UsbRequest();
        requestH2D.initialize(connection,endpointH2D);
    }

    public boolean sendCommandBytes(byte[] Command){

        bufferH2D.clear();
        for(int i=0;i<Command.length;++i){
            bufferH2D.put(Command[i]);
        }
        if(requestH2D.queue(bufferH2D,Command.length)==true&&connection.requestWait()==requestH2D) return true;
        else return false;
    }

    public boolean requestReplyBytes(byte expectedReplyByte){
        boolean expectedReplyFound=false;
        byte replyByte;

        bufferD2H.clear();
        if(requestD2H.queue(bufferD2H,bufferMaxLengthD2H)==true){
            if(connection.requestWait()==requestD2H){
                if(bufferD2H.limit()>0){
                    for(int i=0;i<bufferD2H.limit();++i){
                        replyByte=bufferD2H.get(i);
                        if(replyByte==expectedReplyByte){
                            expectedReplyFound=true;
                            break;
                        }
                    }
                }
            }
        }
        return expectedReplyFound;
    }

    public int getD2HBufferLength(){
        return bufferD2H.limit();
    }

    public byte[] getD2HBuffer(){
        return bufferD2H.array();
    }
}