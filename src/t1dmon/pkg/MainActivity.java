package t1dmon.pkg;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.Character;

import org.apache.http.util.ByteArrayBuffer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import t1dmon.pkg.R;
import android.widget.Toast;
import android.content.SharedPreferences;

import java.nio.charset.Charset;

public class MainActivity extends Activity {
	PendingIntent permissionIntent;
    Button btnCheck;
    TextView textInfo;
    UsbDevice device;
    UsbManager manager;
    private static final String ACTION_USB_PERMISSION = "t1dmon.pkg.usbhost.USB_PERMISSION";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnCheck = (Button) findViewById(R.id.check);
        textInfo = (TextView) findViewById(R.id.info);
        textInfo.setMovementMethod(new ScrollingMovementMethod());
        btnCheck.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v){
                textInfo.setText("");
            	try{
            		checkUSB();
            	}
            	catch (Exception e){
            		//TODO: exit
            		Toast.makeText(MainActivity.this,"Exception:"+e.getMessage(),Toast.LENGTH_SHORT).show();
            	}
            }
        });
 
    }
 
    private void checkUSB() throws Exception{
    	boolean EOTfound=false;
    	boolean ENQfound=false;
    	int frameIndex=0,patternPos=0,keyNPosEnd=0,BGPos=0;
    	Meter meter=null;
    	String time="",keyN="",BG="",storedNKey="";
    	
		SharedPreferences settings=getSharedPreferences("t1dmon.pkg",MODE_PRIVATE);
		if(settings!=null){
			storedNKey=settings.getString("N_key", "");
		}
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION),0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
        HashMap<String,UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            device=deviceIterator.next();
            manager.requestPermission(device, permissionIntent);
            while(manager.hasPermission(device)==false);
            if(device.getVendorId()==6777&&device.getProductId()==25104){
            	break;
            }
        }
    	String info="Result:";
        meter=new Meter(device,manager,this);
    	meter.openConnection();
    	meter.initConnection();
    	ByteArrayBuffer framesD2H=new ByteArrayBuffer(0);
        if(meter.sendCommandBytes(new byte[]{'X'})==false) throw new Exception("1. X send failed");
        if(meter.requestReplyBytes(Meter.EOT)==false) throw new Exception("2. No EOT received");
        framesD2H.append(meter.getD2HBuffer(),0,meter.getD2HBufferLength());
        while(ENQfound==false&&frameIndex<8){
        	ENQfound=meter.requestReplyBytes(Meter.ENQ);
        	framesD2H.append(meter.getD2HBuffer(),0,meter.getD2HBufferLength());
        	++frameIndex;
        }
		if(ENQfound==false||framesD2H.isEmpty()==true) throw new Exception("No ENQ or frame found when receiving header");
		//TODO: calculate checksum in framesD2H and compare with the checksum sent by the meter
		if(meter.sendCommandBytes(new byte[]{0,0,0,1,Meter.NAK})==false) throw new Exception("3. NAK send failed");
		if(meter.requestReplyBytes(Meter.EOT)==false) throw new Exception("4. No EOT received");
        framesD2H.append(meter.getD2HBuffer(),0,meter.getD2HBufferLength());
    	if(meter.sendCommandBytes(new byte[]{0,0,0,1,Meter.ENQ})==false) throw new Exception("5. ENQ send failed");
    	if(meter.requestReplyBytes(Meter.ACK)==false) throw new Exception("6. no ACK received");
    	framesD2H.append(meter.getD2HBuffer(),0,meter.getD2HBufferLength());
    	if(meter.sendCommandBytes(new byte[]{0,0,0,2,'R','|'})==false) throw new Exception("7. R| send failed");
    	if(meter.requestReplyBytes(Meter.ACK)==false) throw new Exception("8. no ACK received");
    	framesD2H.append(meter.getD2HBuffer(),0,meter.getD2HBufferLength());
    	if(meter.sendCommandBytes(new byte[]{0,0,0,2,'N','|'})==false) throw new Exception("9. N| send failed");
    	if(meter.requestReplyBytes(Meter.ACK)==false) throw new Exception("10. no ACK received");
    	framesD2H.append(meter.getD2HBuffer(),0,meter.getD2HBufferLength());
    	if(storedNKey.isEmpty()==true){
    		ByteArrayBuffer keyNCommandBytes=new ByteArrayBuffer(0);
    		byte[] keyNBytes=storedNKey.getBytes("US-ASCII");
    		keyNCommandBytes.append(new byte[]{0,0,0,2},0,4);
    		keyNCommandBytes.append(keyNBytes,0,keyNBytes.length);
    		keyNCommandBytes.append(new byte[]{'|'},0,1);
        	if(meter.sendCommandBytes(keyNCommandBytes.buffer())==false) throw new Exception("11a. 0| send failed");
    	}
    	else{
        	if(meter.sendCommandBytes(new byte[]{0,0,0,2,'|'})==false) throw new Exception("11b. 0| send failed");
    	}
    	if(meter.requestReplyBytes(Meter.ACK)==false) throw new Exception("12. no ACK received");
    	framesD2H.append(meter.getD2HBuffer(),0,meter.getD2HBufferLength());
    	if(meter.sendCommandBytes(new byte[]{0,0,0,2,Meter.CR,Meter.EOT})==false) throw new Exception("13. CR,EOT send failed");
    	EOTfound=false;
        while(EOTfound==false){
        	EOTfound=meter.requestReplyBytes(Meter.EOT);
        	byte[] reply=Arrays.copyOfRange(meter.getD2HBuffer(),4,meter.getD2HBufferLength()-4);//Cut off the first 4 bytes, usually the first three being ABC and then a fourth varying one
        	framesD2H.append(reply,0,reply.length);
        }
        meter.closeConnection();
    	if(framesD2H.isEmpty()==false){
    		patternPos=rFindFirstOf(framesD2H.buffer(),new byte[]{'|','N',Meter.CR,Meter.ETX},framesD2H.length()-1);
    		if(patternPos<0) throw new Exception("Termination code N not found");
    		keyNPosEnd=patternPos-1;
    		patternPos=rFindFirstOf(framesD2H.buffer(),new byte[]{'|'},keyNPosEnd);
    		if(patternPos<0) throw new Exception("No field delimiter found for N command key");
    		keyN=new String(Arrays.copyOfRange(framesD2H.buffer(),patternPos+1,keyNPosEnd),"UTF-8");
    		patternPos=rFindFirstOf(framesD2H.buffer(),new byte[]{'G','l','u','c','o','s','e'},patternPos-1);
    		if(patternPos<0) throw new Exception("No Glucose reading found");
    		BGPos=patternPos;
    		BG=new String(Arrays.copyOfRange(framesD2H.buffer(),patternPos+8,patternPos+10),"UTF-8");//patternPos of "Glucose" plus its length and that of the delimiter '|', yields the position of the BG value
    		patternPos=fFindFirstOf(framesD2H.buffer(),new byte[]{Meter.CR},patternPos);//Look for end of frame
    		if(patternPos<0) throw new Exception("No CR found for end position of time");
    		time=new String(Arrays.copyOfRange(framesD2H.buffer(),patternPos-14,patternPos-1),"UTF-8");
    		info+=" time: "+time+", BG: "+BG+", N key: "+keyN+";";
    		if(settings!=null){
	    		SharedPreferences.Editor prefEditor=settings.edit();
	    		prefEditor.putString("N_key",keyN);
	    		prefEditor.apply();
    		}

    		File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    	    File file = new File(path, "bcnldata.txt");        
	        FileOutputStream stream = new FileOutputStream(file, true);             
    		for(int j=0;j<framesD2H.length();++j){
				String chr=String.format("%c",framesD2H.byteAt(j));
				if(Character.isISOControl(chr.charAt(0))){
					String out=" "+String.format("%x",framesD2H.byteAt(j));
					//info+="0x"+String.format("%x",framesD2H.byteAt(j))+"|";//In case of large data, app gets frozen
		    		stream.write(out.getBytes());
				}
				else{
					String out=" "+chr;
					//info+="|"+chr;//In case of large data, app gets frozen
		    		stream.write(out.getBytes());
				}
				if((j+1)/16*16==j+1) stream.write("\n".getBytes());
			}
	        stream.close();
//			Toast.makeText(this,"length="+framesD2H.length(),Toast.LENGTH_LONG).show();
    	}
		textInfo.setText(info);
    }

    private int fFindFirstOf(byte[] array, byte[] pattern, int offset){
    	int position=-1,match=0;

    	for(int i=0;i<offset;++i){
    		if(array[i]==pattern[match]){
    			position=i;
    			match+=1;
    			if(match==pattern.length) break; 
    		}
    		else{
    			match=0;
    			position=-1;
    		}
    	}
    	return position;
    }

    private int rFindFirstOf(byte[] array, byte[] pattern, int offset){
    	int position=-1,match=0;

    	for(int i=offset;i>=0;--i){
    		if(array[i]==pattern[pattern.length-match-1]){
    			position=i;
    			match+=1;
    			if(match==pattern.length) break; 
    		}
    		else{
    			match=0;
    			position=-1;
    		}
    	}
    	return position;
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
 
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)){
                        if (device != null) {
                        	//No logic here, let it return and check at caller side the result for usbmanager.haspermission() 
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