package t1dmon.pkg;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.lang.Character;

import org.apache.http.util.ByteArrayBuffer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import t1dmon.pkg.R;
import android.widget.Toast;

public class MainActivity extends Activity {
	PendingIntent mPermissionIntent;
    Button btnCheck;
    TextView textInfo;
    UsbDevice device;
    UsbManager manager;
    UsbInterface intf;
    UsbEndpoint endpointH2D, endpointD2H;
    private static final String ACTION_USB_PERMISSION = "t1dmon.pkg.usbhost.USB_PERMISSION";
    public static final int STX=2;
    public static final int ETX=3;
    public static final int EOT=4;
    public static final int ENQ=5;
    public static final int ACK=6;
    public static final int LF=10;
    public static final int CR=13;
    public static final int NAK=21;
    public static final int ETB=23;
    public static final int CAN=24;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnCheck = (Button) findViewById(R.id.check);
        textInfo = (TextView) findViewById(R.id.info);
        btnCheck.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                textInfo.setText("");
                checkInfo();
            }
        });
 
    }
 
    private void checkInfo() {
    	boolean EOTfound=false;
    	boolean ENQfound=false;
    	int frameIndex=0;
    	
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION),0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
        HashMap<String , UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            device=deviceIterator.next();
            manager.requestPermission(device, mPermissionIntent);
            while(manager.hasPermission(device)==false);
            if(device.getVendorId()==6777&&device.getProductId()==25104){
            	break;
            }
        }
    	String info="";
    	int ifNr=device.getInterfaceCount();
        info += "\n" + "DeviceID: " + device.getDeviceId() + "\n"
                + "DeviceName: " + device.getDeviceName() + "\n"
                + "DeviceClass: " + device.getDeviceClass() + " - "
                + "DeviceSubClass: " + device.getDeviceSubclass() + "\n"
                + "VendorID: " + device.getVendorId() + "\n"
                + "ProductID: " + device.getProductId() + "\n"
                + "Protocol: " + device.getDeviceProtocol() + "\n"
                + "Nr of interfaces: "+String.valueOf(ifNr) + "\n";
        for(int f=0;f<ifNr;++f){
        	intf = device.getInterface(f);
        	int epNr=intf.getEndpointCount();
            info+= "Nr of if["+String.valueOf(f)+"] endpoints: "+String.valueOf(epNr)+"\n";
            for(int e=0;e<epNr;++e){
            	UsbEndpoint endpoint = intf.getEndpoint(e);
	            int endpointType=endpoint.getType();
	            switch(endpointType){
	            case UsbConstants.USB_ENDPOINT_XFER_BULK:
	            	info+= "Endpoint["+String.valueOf(e)+"] type is BULK\n";
	            	break;
	            case UsbConstants.USB_ENDPOINT_XFER_CONTROL:
	            	info+= "Endpoint["+String.valueOf(e)+"] type is CONTROL\n";
	            	break;
	            case UsbConstants.USB_ENDPOINT_XFER_INT:
	            	info+= "Endpoint["+String.valueOf(e)+"] type is INTERRUPT\n";
	            	break;
	            case UsbConstants.USB_ENDPOINT_XFER_ISOC:
	            	info+= "Endpoint["+String.valueOf(e)+"] type is ISOC\n";
	            	break;
	            default:
	            	info+= "Endpoint["+String.valueOf(e)+"] type is UNKNOWN\n";
	            	break;
	            }
	            int endpointDirection=endpoint.getDirection();
	            switch(endpointDirection){
	            case UsbConstants.USB_DIR_IN://device to host
	            	info+= "Endpoint["+String.valueOf(e)+"] direction is D2H\n";
	            	endpointD2H=endpoint;
	            	break;
	            case UsbConstants.USB_DIR_OUT://host to device
	            	info+= "Endpoint["+String.valueOf(e)+"] direction is H2D\n";
	            	endpointH2D=endpoint;
	            	break;
	            default:
	            	info+= "Endpoint["+String.valueOf(e)+"] direction is UNKNOWN\n";
	            	break;
	            }
	            int endpointAddress=endpoint.getAddress();
	            info+="Endpoint["+String.valueOf(e)+"] address is "+String.valueOf(endpointAddress)+"\n";
            }
        }
        //textInfo.setText(info);
		Toast.makeText(this,"Starting communication",Toast.LENGTH_SHORT).show();
    	UsbDeviceConnection connection = manager.openDevice(device); 
    	ByteArrayBuffer frameD2H=new ByteArrayBuffer(0);
    	if(connection.claimInterface(intf, true)==true){//True is returned if interface was successfully claimed
	        int bufferMaxLengthD2H=endpointD2H.getMaxPacketSize();
	        ByteBuffer bufferD2H = ByteBuffer.allocate(bufferMaxLengthD2H);
	        UsbRequest requestD2H = new UsbRequest();
	        requestD2H.initialize(connection, endpointD2H);
	        int bufferMaxLengthH2D=endpointH2D.getMaxPacketSize();
	        ByteBuffer bufferH2D = ByteBuffer.allocate(bufferMaxLengthH2D);
	        UsbRequest requestH2D = new UsbRequest();
	        bufferH2D.putInt(ENQ);//Put ENQ in the queue to send something other than ACK or NAK
	        requestH2D.initialize(connection, endpointH2D);
	        if(requestH2D.queue(bufferH2D,1)==true){
	        	if(connection.requestWait()==requestH2D){
	        		while(ENQfound==false&&frameIndex<8){
		    	        if(requestD2H.queue(bufferD2H,bufferMaxLengthD2H)==true){
		    	        	if(connection.requestWait()==requestD2H){//Wait for answer
		    	        		if(bufferD2H.limit()>0){
		    	        			for(int j=0;j<bufferD2H.limit();++j){
		    	        				byte ctrlByte=bufferD2H.get(j);
		    	        				if(ctrlByte==EOT) EOTfound=true;
		    	        				else if(ctrlByte==ENQ) ENQfound=true;
		    	        			}
		    	        			if(frameIndex==0&&EOTfound==false) break;
	    	        				frameD2H.append(bufferD2H.array(),0,bufferD2H.limit());
	    	        				bufferD2H.clear();
		    	        		}
		    	        	}
		    			}
		    	        ++frameIndex;
	        		}
    	        	requestD2H.close();
	        	}
	        	requestH2D.close();
			}
    	}
    	connection.releaseInterface(intf);
    	connection.close();
    	if(ENQfound==true&&frameD2H.isEmpty()==false){
			for(int j=0;j<frameD2H.length();++j){
				String chr=String.format("%c",frameD2H.byteAt(j));
				if(Character.isISOControl(chr.charAt(0))) info+="0x"+String.format("%x",frameD2H.byteAt(j))+"|";
				else info+=chr+"|";
			}
			Toast.makeText(this,"ENQ found, frameIndex="+frameIndex,Toast.LENGTH_SHORT).show();
    	}
		textInfo.setText(info);
    }
 
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
 
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)){
                        if (device != null) {

                        }
                    }
                    else{
                        Log.d("ERROR", "permission denied for device " + device);
                    }
                }
            }
        }
    };
}