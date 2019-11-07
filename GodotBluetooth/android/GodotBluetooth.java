package org.godotengine.godot;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.content.DialogInterface;
import android.util.Log;
import android.widget.Toast;
import android.content.Intent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;


import android.bluetooth.BluetoothServerSocket;

/**
 * Created by Rodrigo Favarete, Mad Forest Games' Lead Game Developer, on September 8, 2017
 */

public class GodotBluetooth extends Godot.SingletonBase 
{   
    protected Activity activity = null; 

    private boolean initialized = false;
    private boolean pairedDevicesListed = false;
    boolean connected = false;
    boolean bluetoothRequired = true;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int MESSAGE_READ = 2;
    private int instanceId = 0;

    Object[] pairedDevicesAvailable;
    AcceptThread aThread;
    ConnectedThread cThreadClient;
    ConnectedThread cThreadServer;
    Handler localHandler;

    StringBuilder receivedData = new StringBuilder();
    private static String macAdress;
    String remoteBluetoothName;
    String[] externalDevicesDialogAux;
    private static final String TAG = "godotbluetooth";

    BluetoothAdapter localBluetooth;
    BluetoothDevice remoteBluetooth;
    BluetoothSocket socket;
    private boolean isServer = true;
    UUID bluetoothUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /* Methods
     * ********************************************************************** */

    /**
     * Initialize the Module
     */

    public void init(final int newInstanceId, final boolean newBluetoothRequired) {
        if (!initialized) {
            activity.runOnUiThread(new Runnable() {
                @Override 
                public void run() {
                    localBluetooth = BluetoothAdapter.getDefaultAdapter();
                    if(localBluetooth == null) {
                        Log.d(TAG, "ERROR: Bluetooth Adapter not found!");
                        activity.finish();
                    }
                    else if (!localBluetooth.isEnabled()){
                        Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        activity.startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);
                        Log.d(TAG, "Asked For BLUETOOTH");
                        
                    }
                    instanceId = newInstanceId;
                    bluetoothRequired = newBluetoothRequired;
                    initialized = true;
                    aThread = new AcceptThread();
                    aThread.start();
                    localHandler = new Handler(){
                        @Override
                        public void handleMessage(Message msg) {

                            if(msg.what == MESSAGE_READ){
                                String newData = (String) msg.obj;
                                //receivedData.append(newData);
                                Log.e(TAG, "_on_data_received");
                                //int endElement = receivedData.indexOf("}");
                                
                                //String completeData = receivedData.substring(0, endElement);
                                //int dataSize = completeData.length();

                                //String finalizedData = receivedData.substring(1, dataSize);
                                GodotLib.calldeferred(instanceId, "_on_data_received", new Object[]{ newData });

                                Log.d(TAG, "_on_data_received and send to Godot");


                                //receivedData.delete(0, receivedData.length());
                                
                            }
                        }
                    };
                }
            });
        }
    }

    /**
     * Gets a list of all external devices that are already paired with the local device
     */

    public void getPairedDevices(final boolean nativeDialog) {
        if (initialized) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(connected) {
                            try {
                                socket.close();
                                connected = false;
                                pairedDevicesListed = false;
                                Log.d(TAG, "Asked For BLUETOOTH");
                                GodotLib.calldeferred(instanceId, "_on_disconnected", new Object[]{});
                            }
                            catch (IOException e) {
                                Log.d(TAG, "ERROR: \n" + e);
                            }
                    }
                    else{
                        if (nativeDialog){
                            nativeLayoutDialogBox();
                        }
                        else {
                            listPairedDevices();
                        }
                    }
                }
            });
        }

        else {
        
            Log.d(TAG, "ERROR: Module Wasn't Initialized!");
        }
    }

    /**
     * Native dialog box to show paired external devices
     */

    private void nativeLayoutDialogBox() {
        String localDeviceName = localBluetooth.getName();
        String localDeviceAddress = localBluetooth.getAddress();

        Set<BluetoothDevice> pairedDevices = localBluetooth.getBondedDevices();

        if(pairedDevices.size() > 0) {
            pairedDevicesAvailable = (Object []) pairedDevices.toArray();

            List<String> externalDeviceInfo = new ArrayList<String>();

            for (BluetoothDevice device : pairedDevices) {
                String externalDeviceName = device.getName();
                String externalDeviceAddress = device.getAddress();

                externalDeviceInfo.add(externalDeviceName + "\n" + externalDeviceAddress);
            }
            externalDevicesDialogAux = new String[externalDeviceInfo.size()];
            externalDevicesDialogAux = externalDeviceInfo.toArray(new String[externalDeviceInfo.size()]);;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("Choose a Device To Connect!");
            builder.setItems(externalDevicesDialogAux, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    connect(which);
                }
            });
            pairedDevicesListed = true;
            AlertDialog dialog = builder.create();
            dialog.show();
    }

    /**
     * Organizes and sends to Godot all external paired devices
     */

    private void listPairedDevices() {

        String localDeviceName = localBluetooth.getName();
        String localDeviceAddress = localBluetooth.getAddress();

        Set<BluetoothDevice> pairedDevices = localBluetooth.getBondedDevices();

        if(pairedDevices.size() > 0) {
            pairedDevicesAvailable = (Object []) pairedDevices.toArray();
            int externalDeviceID = 0;

            for (BluetoothDevice device : pairedDevices) {
                String externalDeviceName = device.getName();
                String externalDeviceAddress = device.getAddress();

                GodotLib.calldeferred(instanceId, "_on_single_device_found", new Object[]{ externalDeviceName, externalDeviceAddress, externalDeviceID });
                Log.d(TAG, "_on_single_device_found");
                externalDeviceID += 1;
            }

            pairedDevicesListed = true;
        }
    }

    /**
     * Prepares to connect to another device, identified by the 'newExternalDeviceID'
     */

    public void connect(final int newExternalDeviceID){
        if (initialized && pairedDevicesListed) {
            activity.runOnUiThread(new Runnable() {
                @Override
                    public void run() {
                        if(!connected){
                            BluetoothDevice device = (BluetoothDevice) pairedDevicesAvailable[newExternalDeviceID];

                            macAdress = device.getAddress();
                            remoteBluetoothName = device.getName();
                            
                            createSocket(macAdress);
                        }
                        else{
                            try {
                                socket.close();
                                connected = false;
                                pairedDevicesListed = false;
                                
                                Log.d(TAG, "Bluetooth Disconnected!");
                            }
                            catch (IOException e) {
                                Log.d(TAG,  "ERROR: \n" + e);
                            }
                        }
                    }
            });
        }
        else {
            Log.d(TAG, "ERROR: Module Wasn't Initialized!");
        }
    }

    /**
     * Creates the Socket to communicate with another device and establishes the connection
     */

    private void createSocket (String MAC) {

        remoteBluetooth = localBluetooth.getRemoteDevice(MAC);

        try {
            socket = remoteBluetooth.createRfcommSocketToServiceRecord(bluetoothUUID);
            if(!socket.isConnected())
            {
                socket.connect();
            }
            else
            {
                Log.d(TAG, "Already connected ...");
            }
            connected = true;
            pairedDevicesListed = true;
            cThreadClient = new ConnectedThread(socket);
            cThreadClient.start();
            GodotLib.calldeferred(instanceId, "_on_connected", new Object[]{ remoteBluetoothName, macAdress });
            isServer = false;
            Log.d(TAG, "Connected With " + remoteBluetoothName);
            }

        catch (IOException e) {
            pairedDevicesListed = false;
            connected = false;
            GodotLib.calldeferred(instanceId, "_on_connected_error", new Object[]{ });
            Log.d(TAG, "ERROR: Cannot connect to " + MAC + " Exception: " + e);
        }
    }

    /**
     * Calls the method that converts the desired data from String to Bytes and sends it to the connected device
     */

    public void sendData(final String dataToSend){

        if (initialized) {
            activity.runOnUiThread(new Runnable() {
                @Override
                    public void run() {
                        if(connected) {
                            if (isServer)
                            {
                                cThreadServer.sendData(dataToSend);
                            }
                            else
                            {
                                cThreadClient.sendData(dataToSend);
                            }
                        }
                        else {
                        Log.d(TAG, "Bluetooth not connected! sending data");
                        }
                    }
            });
        }
    }

    /**
     * Calls the method that sends data as bytes to the connected device
     */

    public void sendDataBytes(final byte[] dataBytesToSend){

        if (initialized) {
            activity.runOnUiThread(new Runnable() {
                @Override
                    public void run() {
                        if(connected) {
                          if (isServer)
                            {
                                cThreadServer.sendDataBytes(dataBytesToSend);
                            }
                            else
                            {
                                cThreadClient.sendDataBytes(dataBytesToSend);
                            }
                        }
                        else {
                            Log.d(TAG, "Bluetooth not connected! not able to send data bytes");
                        }
                    }
            });
        }
    }
    
    private class AcceptThread extends Thread 
    {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = localBluetooth.listenUsingRfcommWithServiceRecord("DisD", bluetoothUUID);
                Log.d(TAG, "Socket's listen() method success");
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                    Log.d(TAG, "Socket's accept() method success");
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    
                    GodotLib.calldeferred(instanceId, "_on_received_connection", new Object[]{});
                    
                    Log.d(TAG, "Socket's received connection");
                    connected = true;
                    cThreadServer = new ConnectedThread(socket);
                    cThreadServer.start();
                    break;
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    /**
     * Class responsible for communication between connected devices
     */

    private class ConnectedThread extends Thread {

        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket newSocket) {

            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = newSocket.getInputStream();
                tmpOut = newSocket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {

            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    String externalData = new String(buffer, 0, bytes);
                    localHandler.obtainMessage(MESSAGE_READ, bytes, -1, externalData).sendToTarget();
                } 

                catch (IOException e) {
                    Log.e(TAG, "localhandler error");
                    break;
                }
            }
        }

        public void sendData(String dataToSend) {

            byte[] dataBuffer = dataToSend.getBytes();

            try {
                mmOutStream.write(dataBuffer);
            } 
            catch (IOException e) { }
        }

        public void sendDataBytes(byte[] bytes) {

            try {
                mmOutStream.write(bytes);
            } 
            catch (IOException e) { }
        }
    }

    /**
     * Internal callbacks
     */

    @Override
    protected void onMainActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                
                if(resultCode == Activity.RESULT_OK) {
                    Log.d(TAG,  "Bluetooth Activated!");
                }
                else {
                    if(bluetoothRequired){
                        Log.d(TAG, "Bluetooth wasn't activated, application closed!");
                        activity.finish();
                    }
                    else{
                        Log.d(TAG, "Bluetooth wasn't activated!");
                    }
                }

                break;
                
            default:
                Log.d(TAG, "ERROR: Unknown situation!");
        }
    }

    /* Definitions
     * ********************************************************************** */

    /**
     * Initilization of the Singleton
     */

    static public Godot.SingletonBase initialize(Activity p_activity)
    {
        return new GodotBluetooth(p_activity);
    }

    /**
     * Constructor
     */

    public GodotBluetooth (Activity activity) 
    {
        registerClass("GodotBluetooth", new String[]
        {
            "init", 
            "getPairedDevices",
            "connect",
            "sendData",
            "sendDataBytes",
        });

        this.activity = activity;
    }
}
