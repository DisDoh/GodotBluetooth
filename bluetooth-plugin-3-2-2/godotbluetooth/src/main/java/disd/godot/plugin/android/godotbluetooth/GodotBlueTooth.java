package disd.godot.plugin.android.godotbluetooth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ConcurrentModificationException;

import oscP5.OscMessage;


import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;

import android.bluetooth.BluetoothServerSocket;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

/**
 * Created by Rodrigo Favarete, Mad Forest Games' Lead Game Developer, on September 8, 2017
 */

public class GodotBlueTooth extends GodotPlugin {
    protected Activity activity = null;

    private boolean initialized = false;
    private boolean pairedDevicesListed = false;
    boolean connected = false;
    boolean bluetoothRequired = true;
    private int msgIncr = 0;
    OscMessage sizeArrayToSendBeforeSend;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int MESSAGE_READ = 2;
   // private int instanceId = 0;
   OscMessage msg;
    OscMessage msgTemp;
    Object[] pairedDevicesAvailable;
    AcceptThread aThread;
    ConnectedThread cThreadClient;
    ConnectedThread cThreadServer;
    Handler localHandler;

    StringBuilder receivedData = new StringBuilder();
    private static String macAdress;
    String remoteBluetoothName;
    String remoteBluetoothAddress;
    String[] externalDevicesDialogAux;
    private static final String TAG = "GodotBlueTooth";


	/** The current connections. */
	private HashMap<String, ConnectedThread> currentConnections;
	private HashMap<String, BluetoothSocket> socketConnections;

    BluetoothAdapter localBluetooth;
    BluetoothDevice remoteBluetooth;
    BluetoothSocket socket;
    BluetoothSocket socketServer;
    private boolean isServer = true;
    UUID bluetoothUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    /* Methods
     * ********************************************************************** */



    /**
     * Constructor
     */

    public GodotBlueTooth(Godot godot) {
        super(godot);
        activity = godot;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GodotBlueTooth";
    }

    @NonNull
    @Override
    public List<String> getPluginMethods() {
        return Arrays.asList(
                "init",
                "getPairedDevices",
                "getDeviceName",
                "getDeviceMacAdress",
                "connect",
                "sendMsg",
                "msgSetName",
                "msgAddString",
                "startServerThread",
                "isServer",
                "resetConnection");
    }

    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new ArraySet<>();

        signals.add(new SignalInfo("on_disconnected"));
        signals.add(new SignalInfo("on_disconnected_from_server", String.class));
        signals.add(new SignalInfo("on_data_received_string", Object.class));
        signals.add(new SignalInfo("on_single_device_found", String.class, String.class, String.class));
        signals.add(new SignalInfo("on_disconnected_from_pair"));
        signals.add(new SignalInfo("on_connected", String.class, String.class));
        signals.add(new SignalInfo("on_connected_error"));
        signals.add(new SignalInfo("on_received_connection", String.class));

        return signals;
    }
    /**
     * Initialize the Module
     */

    public void init(final boolean newBluetoothRequired) {
        if (!initialized) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    localBluetooth = BluetoothAdapter.getDefaultAdapter();
                    if(localBluetooth == null) {
                        Log.e(TAG, "ERROR: Bluetooth Adapter not found!");
                        activity.finish();
                    }
                    else if (!localBluetooth.isEnabled()){
                        Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        activity.startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);
                        Log.e(TAG, "Asked For BLUETOOTH");
                        
                    }
                    currentConnections = new HashMap<String, ConnectedThread>();
                    socketConnections = new HashMap<String, BluetoothSocket>();
                    //instanceId = newInstanceId;
                    bluetoothRequired = newBluetoothRequired;
                    initialized = true;
                    localHandler = new Handler(){
                        @Override
                        public void handleMessage(Message msg) {
                            KetaiOSCMessage m = new KetaiOSCMessage((byte[]) msg.obj);
                            if(m.isValid()) {
                                if (m.checkAddrPattern("string")) {
                                    Log.e(TAG, "_on_msg_received" + m.addrPattern());
                                    msgIncr = m.get(0).intValue();
                                    String newMsg[] = new String[msgIncr];
                                    for (int i = 1; i < msgIncr + 1; i++) {
                                        newMsg[i - 1] = String.valueOf(m.get(i));
                                    }
                                    emitSignal("on_data_received_string", new Object[] {newMsg});
                                    Log.e(TAG, "_on_data_received_string and send to Godot");
                                    msgIncr = 0;
                                }
                            }
                        }
                    };
                }
            });
        }
    }

    public void startServerThread()
    {
        if (aThread == null)
        {
            aThread = new AcceptThread();
            aThread.start();
        }
    }

/**
 * The Class KetaiOSCMessage.
 */
    public class KetaiOSCMessage extends OscMessage {

	/**
	 * Instantiates a new ketai osc message.
	 *
	 * @param _data the _data
	 */
        public KetaiOSCMessage(byte[] _data) {
            super("");
            this.parseMessage(_data);
        }

	/* (non-Javadoc)
	 * @see oscP5.OscPacket#isValid()
	 */
     public boolean isValid() {
		  return isValid;
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
                                Log.e(TAG, "Asked For BLUETOOTH and closed socket");
                                emitSignal("on_disconnect");
                               // GodotLib.calldeferred(instanceId, "_on_disconnected", new Object[]{});
                            }
                            catch (IOException e) {
                                Log.e(TAG, "ERROR: \n" + e);
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

            Log.e(TAG, "ERROR: Module Wasn't Initialized!");
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
            builder.setTitle("Choose a Paired Device To Connect!");
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
                emitSignal("on_single_device_found", externalDeviceName, externalDeviceAddress, externalDeviceID);
               // GodotLib.calldeferred(instanceId, "_on_single_device_found", new Object[]{ externalDeviceName, externalDeviceAddress, externalDeviceID });
                Log.e(TAG, "_on_single_device_found");
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
                            remoteBluetoothAddress = device.getAddress();
                            createSocket(macAdress);
                        }
                        else{
                            try {
                                socket.close();
                                connected = false;
                                pairedDevicesListed = false;

                                Log.e(TAG, "Bluetooth Disconnected!");
                            }
                            catch (IOException e) {
                                Log.e(TAG,  "ERROR: \n" + e);
                            }
                        }
                    }
            });
        }
        else {
            Log.e(TAG, "ERROR: Module Wasn't Initialized!");
        }
    }

 /**
* Reset connection status'
     */

    public void resetConnection()
    {
        emitSignal("on_disconnected_from_pair");
        //GodotLib.calldeferred(instanceId, "_on_disconnected_from_pair", new Object[]{});
        if (!isServer)
        {
            try {
                socket.close();
                connected = false;
                pairedDevicesListed = false;

                if (cThreadClient != null)
                {
                    cThreadClient.interrupt();
                }
                Log.e(TAG, "reset Bluetooth Disconnected!");
            }
            catch (IOException e) {
                Log.e(TAG,  "ERROR: \n" + e);
            }
        }
    }

    /**
     * Creates the Socket to communicate with another device and establishes the connection
     */

    private void createSocket (String MAC) {

        remoteBluetooth = localBluetooth.getRemoteDevice(MAC);
        try
        {
            socket = remoteBluetooth.createRfcommSocketToServiceRecord(bluetoothUUID);
            if(!socket.isConnected())
            {
                socket.connect();
                Log.e(TAG, "Socket connected ...");
            }
            else
            {
                Log.e(TAG, "Already connected ...");
            }
            connected = true;
            pairedDevicesListed = true;
            cThreadClient = new ConnectedThread(socket);
            cThreadClient.start();
            emitSignal("on_connected", remoteBluetoothName, remoteBluetoothAddress);
            //GodotLib.calldeferred(instanceId, "_on_connected", new Object[]{ remoteBluetoothName,  remoteBluetoothAddress});
            isServer = false;
            Log.e(TAG, "Connected With " + remoteBluetoothName);
        }

        catch (IOException e) {
            pairedDevicesListed = false;
            connected = false;
            emitSignal("on_connected_error");
            //GodotLib.calldeferred(instanceId, "_on_connected_error", new Object[]{});
            Log.e(TAG, "ERROR: Cannot connect to " + MAC + " Exception: " + e);
        }
    }

     /**
     * Calls the method that sends data as bytes to the connected device
     */

    public void sendMsg(){
        if (initialized) {
            activity.runOnUiThread(new Runnable() {
                @Override
                    public void run() {
                        if(connected) {
                            Log.e(TAG, "Send msg.........");
//                            sizeArrayToSendBeforeSend = new OscMessage("sizeArray");
//                            sizeArrayToSendBeforeSend.add(msgIncr);
//                            final byte[] sizeArrayBytesToSend = sizeArrayToSendBeforeSend.getBytes();
                            msg.add(msgIncr);
                            for (int i = 0; i < msgIncr;i++)
                            {
                                msg.add(String.valueOf(msgTemp.get(i)));
                            }
                            final byte[] dataBytesToSend = msg.getBytes();
                            if (isServer)
                            {
                               // cThreadServer.sendMsg(dataBytesToSend);
                                try {
                                    for (Map.Entry<String, ConnectedThread> device : currentConnections.entrySet())
                                    {
                                        device.getValue().sendMsgThread(dataBytesToSend);
                                    }
                                    msgIncr = 0;

                                }
                                catch(ConcurrentModificationException e)
                                {
                                    Log.e(TAG, "Concurrent Error " + e);
                                }
                            }
                            else
                            {
//                                cThreadClient.sendMsgThread(sizeArrayBytesToSend);
//
//                                try
//                                {
//                                    Thread.sleep(100);
//                                }
//                                catch(InterruptedException e)
//                                {
//                                    Log.e(TAG, "Error : " + e);
//                                }
                                cThreadClient.sendMsgThread(dataBytesToSend);
                                msgIncr = 0;
                            }
                        }
                        else {
                            Log.e(TAG, "Bluetooth not connected! not able to send data bytes");
                        }
                    }
            });
        }
    }
    public String getDeviceName()
    {
        return localBluetooth.getName();
    }
    public String getDeviceMacAdress()
    {
        return localBluetooth.getAddress();
    }
    public boolean isServer()
    {
        return isServer;
    }
    public void msgSetName(String name)
    {
        msg = new OscMessage(name);
        msgTemp = new OscMessage(name);
    }
    public void msgAddString(String stringMsg)
    {
        msgTemp.add(stringMsg);
        msgIncr++;
    }
    private class AcceptThread extends Thread
    {
        private final BluetoothServerSocket mmServerSocket;
        private ConnectedThread tempThreadServer;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = localBluetooth.listenUsingRfcommWithServiceRecord("DisD", bluetoothUUID);
                Log.e(TAG, "Socket's listen() method success");
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            mmServerSocket = tmp;

        }

        public void run() {
            socketServer = null;
            // Keep listening until exception occurs or a socket is returned.
            while (true)
            {
                try
                {
                    socketServer = mmServerSocket.accept();
                    Log.e(TAG, "Socket's accept() method success");
                } catch (IOException e)
                {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (socketServer != null)
                {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    emitSignal("on_received_connection",socketServer.getRemoteDevice().getAddress());
                   // GodotLib.calldeferred(instanceId, "_on_received_connection", new Object[]{socketServer.getRemoteDevice().getAddress()});

                    Log.e(TAG, "Socket's received connection" + socketServer.getRemoteDevice().getAddress());
                    connected = true;
                    if (currentConnections.containsKey(socketServer.getRemoteDevice().getAddress()))
                    {
                        //currentConnections.get(socketServer.getRemoteDevice().getAddress()).interrupt();
                        currentConnections.remove(socketServer.getRemoteDevice().getAddress()); socketConnections.remove(socketServer.getRemoteDevice().getAddress());

//                        try
//                        {
//                            socketConnections.get(socketServer.getRemoteDevice().getAddress()).close();
////                            socketServer.close();
//                        }
//                        catch(IOException e)
//                        {
//                            Log.e(TAG, "Could not close the connecting socket", e);
//                        }
                    }
                    if (!currentConnections.containsKey(socketServer.getRemoteDevice().getAddress()))
                    {

                        cThreadServer = new ConnectedThread(socketServer);
                        cThreadServer.start();
                        socketConnections.put(socketServer.getRemoteDevice().getAddress(), socketServer);
                        currentConnections.put(socketServer.getRemoteDevice().getAddress(), cThreadServer);
                    }
                    socketServer = null;

                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                mmServerSocket.close();
                Log.e(TAG, "Closed Server side Socket");
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
        private final BluetoothSocket tempSocket;
        public ConnectedThread(BluetoothSocket newSocket) {

            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            tempSocket = newSocket;
            try
            {
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
                try
                {
                    bytes = mmInStream.read(buffer);
                    String externalData = new String(buffer, 0, bytes);

                    byte[] data = Arrays.copyOfRange(buffer, 0, bytes);
                    if (localHandler != null)
                    {
                        localHandler.obtainMessage(MESSAGE_READ, bytes, -1, data).sendToTarget();
                    }
                }

                catch (IOException f) {
                    emitSignal("on_disconnected_from_server", tempSocket.getRemoteDevice().getAddress());
                 //   GodotLib.calldeferred(instanceId, "_on_disconnected_from_server", new Object[]{tempSocket.getRemoteDevice().getAddress()});
                    Log.e(TAG, "localhandler error");
                    if (!isServer)
                    {
                        connected = false;
                    }
//                    if (mmInStream != null)
//                    {
//                        try
//                        {
//                            mmInStream.close();
//                            Log.e(TAG, "Closed InStream");
//                        }
//                        catch (Exception e)
//                        {
//                            Log.e(TAG, "Closing connected InStream error");
//                        }
////                        mmInStream = null;
//                    }
//
//                    if (mmOutStream != null)
//                    {
//                        try
//                        {
//                            mmOutStream.close();
//                            Log.e(TAG, "Closed OutStream");
//                        }
//                        catch (Exception e)
//                        {
//                            Log.e(TAG, "Closing connected OutStream error");
//                        }
////                        mmOutStream = null;
//                    }

                    if (tempSocket != null)
                    {
                        try
                        {
                            tempSocket.close();
//                            tempSocket = null;
                            Log.e(TAG, "Closed socket");
                        }
                        catch (Exception e)
                        {
                            Log.e(TAG, "Closing connected socket error");
                        }
//                        tempSocket = null;
                    }
//                    try
//                                        {
////                        mmInStream.close();
////                        mmOutStream.close();
////                        tempSocket.close();
////                        connected = false
//                        Log.e(TAG, "Closed socket");
//                    }
//                    catch (IOException f)
//                    {
//                        Log.e(TAG, "Closing connected socket error");
//                    }
                    //resetConnection();
                    break;
                }
            }
        }

        public void sendMsgThread(byte[] bytes)
        {
            try {
                mmOutStream.write(bytes);
            }
            catch (IOException e) { }
        }
//        public void cancel()
//        {
//
//            if (tempSocket != null)
//            {
//                try
//                {
//                    tempSocket.close();
////                            tempSocket = null;
//                    Log.e(TAG, "Closed socket");
//                }
//                catch (Exception e)
//                {
//                    Log.e(TAG, "Closing connected socket error");
//                }
////                        tempSocket = null;
//            }
//        }
    }

    /**
     * Internal callbacks
     */

    @Override
    public void onMainActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_ENABLE_BT:

                if(resultCode == Activity.RESULT_OK) {
                    Log.e(TAG,  "Bluetooth Activated!");
                }
                else {
                    if(bluetoothRequired){
                        Log.e(TAG, "Bluetooth wasn't activated, application closed!");
                        activity.finish();
                    }
                    else{
                        Log.e(TAG, "Bluetooth wasn't activated!");
                    }
                }

                break;

            default:
                Log.e(TAG, "ERROR: Unknown situation!");
        }
    }


//    @Override
//    public void this.onResume()
//    {
//        super.onResume();
//        GodotLib.calldeferred(instanceId, "_on_resume", new Object[]{});
//    }
//    @Override
//    public void onPause()
//    {
//        super.onPause();
//        GodotLib.calldeferred(instanceId, "_on_pause", new Object[]{});
//    }

    /* Definitions
     * ********************************************************************** */


}
