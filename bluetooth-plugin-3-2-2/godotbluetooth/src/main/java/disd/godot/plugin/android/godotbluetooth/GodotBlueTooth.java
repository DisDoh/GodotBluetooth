package disd.godot.plugin.android.godotbluetooth;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.content.DialogInterface;
import android.util.Log;
import android.content.Intent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.Set;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ConcurrentModificationException;


import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

import android.bluetooth.BluetoothServerSocket;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

import disd.godot.plugin.android.oscP5.OscMessage;

/**
 * Created by Rodrigo Favarete, Mad Forest Games' Lead Game Developer, on September 8, 2017
 * Modified By DisD Reda Benjamin Meyer
 */

public class GodotBlueTooth extends GodotPlugin {

    protected Activity activity = null;

    private boolean initialized = false;
    private boolean pairedDevicesListed = false;
    boolean connected = false;
    boolean bluetoothRequired = true;
    OscMessage sizeArrayToSendBeforeSend;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int MESSAGE_READ = 2;
    // private int instanceId = 0;
    OscMessage msg;
    OscMessage msgTemp;
    Object[] pairedDevicesAvailable;
    AcceptThread aThread;
    ConnectedThread cThreadClient = null;
    ConnectedThread cThreadServer = null;
    ConnectedThread cThreadBridge = null;
    private Handler localHandler;

    StringBuilder receivedData = new StringBuilder();
    private static String macAdress;
    String serverBluetoothName;
    String serverBluetoothAddress;
    String[] externalDevicesDialogAux;
    private static final String TAG = "GodotBlueTooth";


    /**
     * The current connections.
     */
    private HashMap<String, ConnectedThread> currentConnections;
    private HashMap<String, ConnectedThread> scatternetConnections;
    private HashMap<String, BluetoothSocket> socketConnections;

    BluetoothAdapter localBluetooth;
    BluetoothDevice remoteBluetooth;
    BluetoothSocket socket;
    BluetoothSocket socketServer;
    private String socketBridgeAddress;
    private boolean isServer = false;
    UUID bluetoothUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    //private boolean firstPart = false;
    private boolean secondPart = false;
    private boolean thirdPart = false;

    private String newMsg[];
    public int firstLimit = 90;
    public int secondLimit = (int) (2 * firstLimit);
    public int thirdLimit = (int) (3 * firstLimit);
    public int msgIncr = 0;
    public int msgIncrReceived = 0;
    //UUID myUuid = UUID.randomUUID();
    public String myUuid = "-1";
    private static String sID = null;
    private static final String INSTALLATION = "INSTALLATION";
    private boolean imABridge = false;
    private BluetoothSocket scatternetSocket;
    private boolean imInTheScatternet = false;
    private boolean mustNotDeconnect = false;
    private String serverUuid;
    private boolean isComingfromServer = false;
    //    private boolean emitedSignalConnected = false;
    private boolean firstConnectToTheBridge = true;
    private int bridgeNumberFromServer = 0;
    private int incrBridgeNumber = 0;
    private boolean isFromResetConnection = false;
    private int countConnectedToServerOrABridge = 0;
    private boolean imConnectedToABridge = false;
    private boolean imConnecting = false;
    private boolean mustResetVars = true;
    private boolean hasAlreadyBeenThere = false;
    /* Methods
     * ********************************************************************** */


    /**
     * Constructor
     */

    public GodotBlueTooth(Godot godot) {
        super(godot);
        activity = getActivity();
        localHandler = null;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GodotBlueTooth";
    }

//    @NonNull
//    @Override
//    public List<String> getPluginMethods() {
//        return Arrays.asList(
//                "init",
//                "getPairedDevices",
//                "getDeviceName",
//                "getDeviceMacAdress",
//                "connect",
//                "sendMsg",
//                "msgSetName",
//                "msgAddString",
//                "startServerThread",
//                "isServer",
//                "resetConnection");
//    }

    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new ArraySet<>();

        signals.add(new SignalInfo("on_disconnected", String.class, String.class));
        signals.add(new SignalInfo("on_data_received_string", Object.class));
        signals.add(new SignalInfo("on_single_device_found", String.class, String.class, String.class));
//        signals.add(new SignalInfo("on_disconnected_from_pair"));
        signals.add(new SignalInfo("on_connected", String.class, String.class));
        signals.add(new SignalInfo("on_connected_error"));
        signals.add(new SignalInfo("on_received_connection", String.class, String.class));
        signals.add(new SignalInfo("on_getting_uuid", String.class));
        signals.add(new SignalInfo("on_devices_found", Object.class, Object.class));

        return signals;
    }


    /**
     * Initialize the Module
     */
    @UsedByGodot
    public void init(final boolean newBluetoothRequired) {
        if (!initialized) {
            myUuid = setUuid(activity.getBaseContext()).split("-")[0];
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    localBluetooth = BluetoothAdapter.getDefaultAdapter();
                    if (localBluetooth == null) {
                        //Log.e(TAG, "ERROR: Bluetooth Adapter not found!");
                        activity.finish();
                    } else if (!localBluetooth.isEnabled()) {
                        Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        activity.startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);
                        //Log.e(TAG, "Asked For BLUETOOTH");

                    }
                    currentConnections = new HashMap<String, ConnectedThread>();
                    socketConnections = new HashMap<String, BluetoothSocket>();
                    scatternetConnections = new HashMap<String, ConnectedThread>();
                    //instanceId = newInstanceId;
                    bluetoothRequired = newBluetoothRequired;
                    initialized = true;
                    localHandler = new Handler(Looper.getMainLooper()) {
                        @Override
                        public void handleMessage(Message msgReceived) {

                            byte[] msgByte = (byte[]) msgReceived.obj;
                            Log.e(TAG, "MsgByte Length: " + msgByte.length);
                            KetaiOSCMessage m = new KetaiOSCMessage(msgByte);
                            Log.e(TAG, "MsgByte data: " + m.toString());
                            if (msgByte.length < 640) {
                                //Log.e(TAG,  "Msg m: \n" + m);
                                if (m.isValid()) {
                                    //if (m.checkAddrPattern("string")) {
                                    //  //Log.e(TAG, "_on_msg_received" + String.valueOf(secondPart) + "  " + String.valueOf(thirdPart));
                                    if (!secondPart && !thirdPart) {
                                        msgIncrReceived = Integer.parseInt(String.valueOf(m.get(0)));
                                        newMsg = new String[msgIncrReceived];
                                        Log.e(TAG, "I'm a Bridge " + String.valueOf(imABridge));
                                        Log.e(TAG, "I'm in the Scatternet " + String.valueOf(imInTheScatternet));
                                        if (String.valueOf(m.get(1)).equals(serverUuid)) {
                                            isComingfromServer = true;
                                        } else {
                                            isComingfromServer = false;
                                        }

                                    }
                                    if (msgIncrReceived <= firstLimit) {
                                        for (int i = 2; i < msgIncrReceived + 2; i++) {
                                            newMsg[i - 2] = String.valueOf(m.get(i));
                                        }

                                        if (imABridge) {
                                            msg = new OscMessage("string");
                                            msg.add(String.valueOf(msgIncrReceived));
                                            msg.add(String.valueOf(myUuid));
                                            //Starting from 2 because 2 index are set at creation of msg.
                                            for (int i = 2; i < msgIncrReceived + 2; i++) {
                                                msg.add(String.valueOf((m.get(i))));
                                            }
                                            sendDataByte();
                                        }
                                        msgIncrReceived = 0;
                                        emitSignal("on_data_received_string", new Object[]{newMsg});
                                        //Log.e(TAG, "_on_data_received_string and send to Godot");
                                    } else if (msgIncrReceived <= secondLimit) {
                                        if (!secondPart) {
                                            for (int i = 2; i < firstLimit + 2; i++) {
                                                newMsg[i - 2] = String.valueOf(m.get(i));
                                            }
                                            if (imABridge) {
                                                msg = new OscMessage("string");
                                                msg.add(String.valueOf(msgIncrReceived));
                                                msg.add(String.valueOf(myUuid));
                                                //Starting from 2 because 2 index are set at creation of msg.
                                                for (int i = 2; i < firstLimit + 2; i++) {
                                                    msg.add(String.valueOf((m.get(i))));
                                                }
                                                sendDataByte();
                                            }
                                            secondPart = true;
                                        } else {
                                            for (int i = firstLimit + 2; i < msgIncrReceived + 2; i++) {
                                                newMsg[i - 2] = String.valueOf(m.get(i - firstLimit - 2));
                                            }
                                            if (imABridge) {
                                                msg = new OscMessage("string");
                                                for (int i = firstLimit + 2; i < msgIncrReceived + 2; i++) {
                                                    msg.add(String.valueOf((m.get(i - firstLimit - 2))));
                                                }
                                                sendDataByte();
                                            }
                                            secondPart = false;
                                            msgIncrReceived = 0;
                                            emitSignal("on_data_received_string", new Object[]{newMsg});
                                            //Log.e(TAG, "_on_data_received_string and send to Godot");
                                        }
                                    } else if (msgIncrReceived <= thirdLimit) {

                                        if (!secondPart) {
                                            for (int i = 2; i < firstLimit + 2; i++) {
                                                newMsg[i - 2] = String.valueOf(m.get(i));
                                            }
                                            if (imABridge) {
                                                msg = new OscMessage("string");
                                                msg.add(String.valueOf(msgIncrReceived));
                                                msg.add(String.valueOf(myUuid));
                                                //Starting from 2 because 2 index are set at creation of msg.
                                                for (int i = 2; i < msgIncrReceived + 2; i++) {
                                                    msg.add(String.valueOf((m.get(i))));
                                                }
                                                sendDataByte();
                                            }
                                            secondPart = true;
                                        } else if (!thirdPart) {
                                            for (int i = firstLimit + 2; i < secondLimit + 2; i++) {
                                                newMsg[i - 2] = String.valueOf(m.get(i - firstLimit - 2));
                                            }
                                            if (imABridge) {
                                                msg = new OscMessage("string");
                                                for (int i = firstLimit + 2; i < secondLimit + 2; i++) {
                                                    msg.add(String.valueOf((m.get(i - firstLimit - 2))));
                                                }
                                                sendDataByte();
                                            }
                                            thirdPart = true;
                                        } else {
                                            for (int i = secondLimit + 2; i < msgIncrReceived + 2; i++) {
                                                newMsg[i - 2] = String.valueOf(m.get(i - secondLimit - 2));
                                            }
                                            if (imABridge) {
                                                msgSetName("string");
                                                msg = new OscMessage("string");
                                                for (int i = secondLimit + 2; i < msgIncrReceived + 2; i++) {
                                                    msg.add(String.valueOf((m.get(i - secondLimit - 2))));
                                                }
                                                sendDataByte();
                                            }
                                            secondPart = false;
                                            thirdPart = false;
                                            msgIncrReceived = 0;
                                            emitSignal("on_data_received_string", new Object[]{newMsg});
                                            //Log.e(TAG, "_on_data_received_string and send to Godot");
                                        }
                                    }
                                    if (String.valueOf(m.get(2)).equals("you're a Bridge")) {
                                        serverUuid = String.valueOf(m.get(1));
                                        imABridge = true;
                                        imInTheScatternet = true;
                                        if (aThread == null) {
                                            aThread = new AcceptThread();
                                            aThread.start();
                                            Log.e(TAG, "Server started");

                                        }
                                        Log.e(TAG, "Started Scatternet");
//                                        msgSetName("string");
//                                        msgAddString("Bridge Number");
//                                        msgAddString(String.valueOf(bridgeNumber));
//                                        sendMsg();

//                                        if (!emitedSignalConnected) {
                                        imConnecting = false;
                                        emitSignal("on_connected", serverBluetoothName, serverBluetoothAddress);
//                                            emitedSignalConnected = true;
//                                        }
                                    }
                                    if (String.valueOf(m.get(2)).equals("Connect to a Bridge") && !imABridge && !isServer) {
                                        mustNotDeconnect = true;
                                        imConnectedToABridge = true;
                                        imConnecting = true;
                                        if (cThreadClient != null) {

                                            try {

                                                if (cThreadClient.mmInStream != null) {
                                                    cThreadClient.mmInStream.close();
                                                }
                                                if (cThreadClient.mmOutStream != null) {
                                                    cThreadClient.mmOutStream.close();
                                                }
                                                if (cThreadClient.tempSocket != null) {
                                                    cThreadClient.tempSocket.close();
                                                }
                                                cThreadClient.interrupt();
                                                cThreadClient = null;
                                                //Log.e(TAG, "reset Bluetooth Disconnected! from client");
                                            } catch (IOException e) {
                                                Log.e(TAG, "ERROR: \n" + e);
                                            }
                                        }
                                        Handler handler = new Handler(Looper.getMainLooper());
                                        handler.postDelayed(() -> {
                                            scatternetSocket = createSocket(String.valueOf(m.get(3)));
                                            imInTheScatternet = true;
                                            connected = true;
                                            imConnecting = false;
                                            Log.e(TAG, "Connected to a Bridge of Scatternet " + String.valueOf(m.get(3)));

                                        }, 1000);
                                    }
                                    if (String.valueOf(m.get(2)).equals("Emit Signal On connected")) {
//                                        if (!emitedSignalConnected) {
                                        imConnecting = false;
                                        emitSignal("on_connected", serverBluetoothName, serverBluetoothAddress);
//                                            emitedSignalConnected = true;
//                                        }
//                                        Log.e(TAG, "Connected to a Bridge of Scatternet");
//                                        if (imABridge) {
//                                            scatternetConnections.put(scatternetSocket.getRemoteDevice().getAddress(), cThreadServer);
//                                        }

                                    }

                                    if (String.valueOf(m.get(2)).equals("Server has Disconnected")) {
                                        imInTheScatternet = false;
                                        mustNotDeconnect = false;
                                        imABridge = false;
                                        connected = false;
                                        //resetConnection();
                                        Log.e(TAG, "Server has Disconnected");
                                        resetConnection();
                                        //emitSignal("on_disconnected", serverBluetoothAddress, myUuid);
                                    }
                                    if (String.valueOf(m.get(2)).equals("Someone disconnected from bridge")) {
                                        if (isServer) {
                                            Log.e(TAG, "Entered isServer && someone disconnected from bridge");
                                            emitSignal("on_disconnected", String.valueOf(m.get(3)), myUuid);
                                        }
                                    }
                                    if (String.valueOf(m.get(2)).equals("A bridge disconnected") && imConnectedToABridge)
                                    {
                                        mustResetVars = false;
                                        Log.e(TAG, "Entered A bridge disconnected");

                                        if (imABridge) {
                                            isComingfromServer = true;
                                            msg = new OscMessage("string");
                                            msg.add(String.valueOf(3));
                                            msg.add(String.valueOf(myUuid));
                                            msg.add("A bridge disconnected");
                                            msg.add("End");
                                            sendDataByte();
                                            //Random rand = new Random();
                                            //int wait_time = rand.nextInt(1000) + rand.nextInt(1000) + rand.nextInt(1000) + rand.nextInt(1000);
//                                            imConnectedToABridge = false;
//                                            imABridge = false;
//                                            imConnecting = true;
//                                            imInTheScatternet = false;
                                            //                                            Handler handler = new Handler(Looper.getMainLooper());
//                                            handler.postDelayed(() -> {
//                                                createSocket(serverBluetoothAddress);
//                                                imConnecting = false;
//                                                //emitSignal("on_connected", serverBluetoothName, serverBluetoothAddress);
//                                                connected = true;
//                                            }, wait_time);
                                        }
                                        if (hasAlreadyBeenThere)
                                        {
                                            mustNotDeconnect = true;
                                            resetConnection();
                                            mustResetVars = true;
                                        }
                                        hasAlreadyBeenThere = true;
                                    }
                                }
                            } else {
                                Log.e(TAG, "over 250kb");
                            }
                        }
                    };
                }
            });
        }
    }

    @UsedByGodot
    public void startServerThread() {
        isServer = true;
        if (aThread == null) {
            aThread = new AcceptThread();
            aThread.start();
            //Log.e(TAG, "Server started");

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

    @UsedByGodot
    public void getPairedDevices(final boolean nativeDialog) {
        if (initialized) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (connected) {
//                            try {
//                                socket.close();
//                                connected = false;
//                                pairedDevicesListed = false;
//                                //Log.e(TAG, "Asked For BLUETOOTH and closed socket");
//                                emitSignal("on_disconnect");
//                               // GodotLib.calldeferred(instanceId, "_on_disconnected", new Object[]{});
//                            }
//                            catch (IOException e) {
//                                //Log.e(TAG, "ERROR: \n" + e);
//                            }
                        resetConnection();
                    }
                    if (nativeDialog) {
                        nativeLayoutDialogBox();
                    } else {
                        listPairedDevices();
                    }

                }
            });
        } else {

            //Log.e(TAG, "ERROR: Module Wasn't Initialized!");
        }
    }

    /**
     * Native dialog box to show paired external devices
     */

    private void nativeLayoutDialogBox() {
        String localDeviceName = localBluetooth.getName();
        String localDeviceAddress = localBluetooth.getAddress();

        Set<BluetoothDevice> pairedDevices = localBluetooth.getBondedDevices();

        if (pairedDevices.size() > 0) {
            pairedDevicesAvailable = (Object[]) pairedDevices.toArray();

            List<String> externalDeviceInfo = new ArrayList<String>();

            for (BluetoothDevice device : pairedDevices) {
                String externalDeviceName = device.getName();
                String externalDeviceAddress = device.getAddress();

                externalDeviceInfo.add(externalDeviceName + "\n" + externalDeviceAddress);
            }
            externalDevicesDialogAux = new String[externalDeviceInfo.size()];
            externalDevicesDialogAux = externalDeviceInfo.toArray(new String[externalDeviceInfo.size()]);
            ;
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

        if (pairedDevices.size() > 0) {
            pairedDevicesAvailable = (Object[]) pairedDevices.toArray();
            int externalTotDeviceID = 0;

            String[] externalDeviceName = new String[pairedDevices.size()];
            String[] externalDeviceAddress = new String[pairedDevices.size()];
            int i = 0;
            for (BluetoothDevice device : pairedDevices) {
                externalDeviceName[i] = device.getName();
                externalDeviceAddress[i] = device.getAddress();
                i++;
                // GodotLib.calldeferred(instanceId, "_on_single_device_found", new Object[]{ externalDeviceName, externalDeviceAddress, externalDeviceID });

                externalTotDeviceID += 1;
            }
            String[][] aS_Devices = new String[2][externalTotDeviceID];
            aS_Devices[0] = externalDeviceName;
            aS_Devices[1] = externalDeviceAddress;
            emitSignal("on_devices_found", new Object[]{aS_Devices[0]}, new Object[]{aS_Devices[1]});
            //Log.e(TAG, "on_devices_found" + String.valueOf(aS_Devices[0][3]));
            pairedDevicesListed = true;
        }
    }

    /**
     * Prepares to connect to another device, identified by the 'newExternalDeviceID'
     */

    @UsedByGodot
    public void connect(final int newExternalDeviceID) {
        if (initialized && pairedDevicesListed) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!connected) {
                        BluetoothDevice device = (BluetoothDevice) pairedDevicesAvailable[newExternalDeviceID];

                        macAdress = device.getAddress();
                        serverBluetoothName = device.getName();
                        serverBluetoothAddress = device.getAddress();

                        createSocket(macAdress);
                        connected = true;
                    }
//                        else{
//                            resetConnection();
//                        }
                }
            });
        } else {
            //Log.e(TAG, "ERROR: Module Wasn't Initialized!");
        }
    }
    /**
     * Reset connection status'
     */
    @UsedByGodot
    public void resetConnection() {

//        int wait_time = 800;
//
//        Handler handler = new Handler(Looper.getMainLooper());
//        handler.postDelayed(() -> {
            if (cThreadClient != null) {
                if (!mustNotDeconnect)
                    emitSignal("on_disconnected", cThreadClient.tempSocket.getRemoteDevice().getAddress(), myUuid);
                isFromResetConnection = true;
                if (cThreadClient.mmInStream != null) {
                    try {
                        cThreadClient.mmInStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (cThreadClient.mmOutStream != null) {
                    try {
                        cThreadClient.mmOutStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (cThreadClient.tempSocket != null) {
                    try {
                        cThreadClient.tempSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (cThreadClient != null)
                {
                    cThreadClient.interrupt();
                    cThreadClient = null;
                }
                //Log.e(TAG, "reset Bluetooth Disconnected! from client");
            }
            if (isServer || imABridge) {

                isServer = false;
                connected = false;
                pairedDevicesListed = false;
                if (aThread != null) {
                    aThread.interrupt();
                    aThread = null;
                }
//            if(cThreadServer != null) {
//                if (cThreadServer.mmInStream != null) {
//                    try {
//                        cThreadServer.mmInStream.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//                if (cThreadServer.mmOutStream != null) {
//                    try {
//                        cThreadServer.mmOutStream.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//                if (cThreadServer.tempSocket != null) {
//                    try {
//                        cThreadServer.tempSocket.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//                cThreadServer.interrupt();
//            }
                //Log.e(TAG, "reset Bluetooth Disconnected! from server");
                isFromResetConnection = true;
                for (Map.Entry<String, ConnectedThread> stringConnectedThreadEntry : currentConnections.entrySet()) {
                    cThreadServer = stringConnectedThreadEntry.getValue();
                    if (cThreadServer != null) {
                        //  if (Objects.requireNonNull(currentConnections.get(item)).mmInStream != null) {
                        try {
                            cThreadServer.mmInStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        //  }
                        //if (Objects.requireNonNull(currentConnections.get(item)).mmOutStream != null) {
                        try {
                            cThreadServer.mmOutStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        cThreadServer.interrupt();
                        cThreadServer = null;
//                    System.out.println(pair.getKey() + " = " + pair.getValue());
//                    it.remove(); // avoids a ConcurrentModificationException
                        //  }
//                if (Objects.requireNonNull(currentConnections.get(item)).tempSocket != null) {
//                    try {
//                        Objects.requireNonNull(currentConnections.get(item)).tempSocket.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//                Objects.requireNonNull(currentConnections.get(item)).interrupt();
                        //currentConnections.remove(item);
                    }
                }
                for (Map.Entry<String, ConnectedThread> stringConnectedThreadEntry : scatternetConnections.entrySet()) {
                    cThreadServer = stringConnectedThreadEntry.getValue();
                    if (cThreadServer != null) {
                        //  if (Objects.requireNonNull(currentConnections.get(item)).mmInStream != null) {
                        try {
                            cThreadServer.mmInStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        //  }
                        //if (Objects.requireNonNull(currentConnections.get(item)).mmOutStream != null) {
                        try {
                            cThreadServer.mmOutStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        cThreadServer.interrupt();
                        cThreadServer = null;
                        //                    System.out.println(pair.getKey() + " = " + pair.getValue());
                        //                    it.remove(); // avoids a ConcurrentModificationException
                        //  }
                        //                if (Objects.requireNonNull(currentConnections.get(item)).tempSocket != null) {
                        //                    try {
                        //                        Objects.requireNonNull(currentConnections.get(item)).tempSocket.close();
                        //                    } catch (IOException e) {
                        //                        e.printStackTrace();
                        //                    }
                        //                }
                        //                Objects.requireNonNull(currentConnections.get(item)).interrupt();
                        //currentConnections.remove(item);
                    }
                }
            }
            if (mustResetVars)
            {
                hasAlreadyBeenThere = false;
                mustNotDeconnect = false;
                countConnectedToServerOrABridge = 0;
                isFromResetConnection = false;
                imConnectedToABridge = false;
                firstConnectToTheBridge = true;
                imABridge = false;
                imInTheScatternet = false;
                connected = false;
                pairedDevicesListed = false;
                currentConnections.clear();
                scatternetConnections.clear();
                socketConnections.clear();
            }
//        }, wait_time);

    }

    /**
     * Creates the Socket to communicate with another device and establishes the connection
     */

    private BluetoothSocket createSocket (String MAC) {

        remoteBluetooth = localBluetooth.getRemoteDevice(MAC);
        try
        {
            socket = remoteBluetooth.createRfcommSocketToServiceRecord(bluetoothUUID);
            if(!socket.isConnected() && cThreadClient == null)
            {
                socket.connect();
                pairedDevicesListed = true;
                cThreadClient = new ConnectedThread(socket);
                cThreadClient.start();
                //Log.e(TAG, "Socket connected ...");
            }
//            else
//            {
//                //Log.e(TAG, "Already connected ...");
//            }
            //currentConnections.put(socket.getRemoteDevice().getAddress(), cThreadClient);
//            Handler handler = new Handler(Looper.getMainLooper());
//            handler.postDelayed(new Runnable() {
//                public void run() {
//                    if (imInTheScatternet) {
//                        emitSignal("on_connected", serverBluetoothName, serverBluetoothAddress);
//                    }
//                }
//            }, 1000);
//            if (imInTheScatternet) {
//                emitSignal("on_connected", serverBluetoothName, serverBluetoothAddress);
//            }
            //GodotLib.calldeferred(instanceId, "_on_connected", new Object[]{ serverBluetoothName,  serverBluetoothAddress});
            isServer = false;
            //Log.e(TAG, "Connected With " + serverBluetoothName);
        }

        catch (IOException e) {
            pairedDevicesListed = false;
            connected = false;
            emitSignal("on_connected_error");
            //GodotLib.calldeferred(instanceId, "_on_connected_error", new Object[]{});
            //Log.e(TAG, "ERROR: Cannot connect to " + MAC + " Exception: " + e);
        }
        return socket;
    }

    @UsedByGodot
    private String getMyUuid () {

        //myUuid = myUuidFromGodot;
        return myUuid;
    }

    @UsedByGodot
    private int getIfImInAScatternet () {

        int imInAScatternet;
        if (imABridge)
        {
            imInAScatternet = 1;
        }
        else
        {
            imInAScatternet = 0;
        }
        return imInAScatternet;
    }

    @UsedByGodot
    private int getImABridge () {
        int imABridgeInt;
        if (imABridge)
        {
            imABridgeInt = 1;
        }
        else
        {
            imABridgeInt = 0;
        }
        //myUuid = myUuidFromGodot;
        return imABridgeInt;
    }

    /**
     * Calls the method that sends data as bytes to the connected device
     */

     @UsedByGodot
     public void sendMsg(){
        if (initialized) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (connected && !imConnecting) {
                        isComingfromServer = false;
                        Log.e(TAG, "Sending msg ..." + String.valueOf(msgTemp));
//                            sizeArrayToSendBeforeSend = new OscMessage("sizeArray");
//                            sizeArrayToSendBeforeSend.add(msgIncr);
//                            final byte[] sizeArrayBytesToSend = sizeArrayToSendBeforeSend.getBytes();
                        Log.e(TAG, "MsgIncr : " + String.valueOf(msgIncr));
                        if (msgIncr <= firstLimit) {
                            msg = new OscMessage("string");
                            msg.add(String.valueOf(msgIncr));
                            msg.add(String.valueOf(myUuid));
                            for (int i = 0; i < msgIncr; i++) {
                                msg.add(String.valueOf(msgTemp.get(i)));
                                // //Log.e(TAG, "Msg : " + String.valueOf(msgTemp.get(i)));
                            }
                            sendDataByte();

                            Log.e(TAG, "Sended msg...first part");

                        }
                        else if (msgIncr <= secondLimit) {
                            msg = new OscMessage("string");
                            msg.add(String.valueOf(msgIncr));
                            msg.add(String.valueOf(myUuid));
                            for (int i = 0; i < firstLimit; i++) {

                                msg.add(String.valueOf(msgTemp.get(i)));
                            }
                            sendDataByte();
                            Log.e(TAG, "Sended msg...second part1");
                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.postDelayed(new Runnable() {
                                public void run() {
                                    msg = new OscMessage("string");
                                    for (int i = firstLimit; i < msgIncr; i++) {

                                        msg.add(String.valueOf(msgTemp.get(i)));
                                    }
                                    sendDataByte();
                                    Log.e(TAG, "Sended msg...second part2");
                                }
                            }, 50);
                        }
                        else if (msgIncr <= thirdLimit) {

                            msg = new OscMessage("string");
                            msg.add(String.valueOf(msgIncr));
                            msg.add(String.valueOf(myUuid));
                            for (int i = 0; i < firstLimit; i++) {

                                msg.add(String.valueOf(msgTemp.get(i)));
                            }
                            sendDataByte();
                            Log.e(TAG, "Sended msg...third part1");
                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.postDelayed(new Runnable() {
                                public void run() {
                                    msg = new OscMessage("string");
                                    for (int i = firstLimit; i < secondLimit; i++) {

                                        msg.add(String.valueOf(msgTemp.get(i)));
                                    }
                                    sendDataByte();
                                    Log.e(TAG, "Sended msg...third part2");
                                }
                            }, 50);
                            handler.postDelayed(new Runnable() {
                                public void run() {
                                    msg = new OscMessage("string");
                                    for (int i = secondLimit; i < thirdLimit; i++) {
                                        msg.add(String.valueOf(msgTemp.get(i)));
                                    }
                                    sendDataByte();
                                    Log.e(TAG, "Sended msg...third part3");

                                }
                            },  100);
                        }
                    }
//                    else {
//                        //Log.e(TAG, "Bluetooth not connected! not able to send data bytes");
//                    }
                }
            });
        }
    }
    public void sendDataByte()
    {
        if (!localBluetooth.isEnabled()) {
            //resetConnection();
            return;
        }

        final byte[] dataBytesToSend = msg.getBytes();
        if (!imABridge) {
            if (isServer) {
                // cThreadServer.sendMsg(dataBytesToSend);
                try {
                    for (Map.Entry<String, ConnectedThread> device : currentConnections.entrySet()) {
                        device.getValue().sendMsgThread(dataBytesToSend);
                    }

                } catch (ConcurrentModificationException e) {
                    //Log.e(TAG, "Concurrent Error " + e);
                }
            } else {
                if (cThreadClient != null)
                cThreadClient.sendMsgThread(dataBytesToSend);
            }
        }
        else {
            if (isComingfromServer) {
                // cThreadServer.sendMsg(dataBytesToSend);
                try {
                    for (Map.Entry<String, ConnectedThread> device : currentConnections.entrySet()) {
                        device.getValue().sendMsgThread(dataBytesToSend);
                    }

                } catch (ConcurrentModificationException e) {
                    //Log.e(TAG, "Concurrent Error " + e);
                }
            }
            else {
                if (cThreadClient != null)
                cThreadClient.sendMsgThread(dataBytesToSend);
            }
        }
    }


    public synchronized static String setUuid(Context context) {
        if (sID == null) {
            File installation = new File(context.getFilesDir(), INSTALLATION);
            try {
                if (!installation.exists())
                    writeInstallationFile(installation);
                sID = readInstallationFile(installation);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        //myUuid = sID;
        return sID;
    }

    private static String readInstallationFile(File installation) throws IOException {
        RandomAccessFile f = new RandomAccessFile(installation, "r");
        byte[] bytes = new byte[(int) f.length()];
        f.readFully(bytes);
        f.close();
        return new String(bytes);
    }

    private static void writeInstallationFile(File installation) throws IOException {
        FileOutputStream out = new FileOutputStream(installation);
        String id = UUID.randomUUID().toString();
        out.write(id.getBytes());
        out.close();
    }
    @UsedByGodot
    public String getDeviceName()
    {
        return localBluetooth.getName();
    }
    @UsedByGodot
    public String getDeviceMacAdress()
    {
        return localBluetooth.getAddress();
    }
    @UsedByGodot
    public boolean isServer()
    {
        return isServer;
    }
    @UsedByGodot
    public void msgSetName(String name)
    {
        msg = new OscMessage(name);
        msgTemp = new OscMessage(name);
        msgIncr = 0;
    }

    @UsedByGodot
    public void msgAddString(String stringMsg)
    {
        msgTemp.add(stringMsg);
        msgIncr++;
    }

    private class AcceptThread extends Thread
    {
        private final BluetoothServerSocket mmServerSocket;
        //private ConnectedThread tempThreadServer;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = localBluetooth.listenUsingRfcommWithServiceRecord("DisD", bluetoothUUID);
                //Log.e(TAG, "Socket's listen() method success");
            } catch (IOException e) {
                //Log.e(TAG, "Socket's listen() method failed", e);
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
                    //Log.e(TAG, "Socket's accept() method success");
                } catch (IOException e)
                {
                    //Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (socketServer != null)
                {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    // GodotLib.calldeferred(instanceId, "_on_received_connection", new Object[]{socketServer.getRemoteDevice().getAddress()});

                    //Log.e(TAG, "Socket's received connection" + socketServer.getRemoteDevice().getAddress());
                    connected = true;
//                    if (currentConnections.containsKey(socketServer.getRemoteDevice().getAddress()))
//                    {
//                        Objects.requireNonNull(currentConnections.get(socketServer.getRemoteDevice().getAddress())).interrupt();
//                        currentConnections.remove(socketServer.getRemoteDevice().getAddress());
//                        socketConnections.remove(socketServer.getRemoteDevice().getAddress());
//
//                    }
//                        try
//                        {
//                            socketConnections.get(socketServer.getRemoteDevice().getAddress()).close();
////                            socketServer.close();
//                        }
//                        catch(IOException e)
//                        {
//                            //Log.e(TAG, "Could not close the connecting socket", e);
//                        }
                    if (!currentConnections.containsKey(socketServer.getRemoteDevice().getAddress())) {
                        cThreadServer = new ConnectedThread(socketServer);
                        cThreadServer.start();
                        socketConnections.put(socketServer.getRemoteDevice().getAddress(), socketServer);

                        if (countConnectedToServerOrABridge >= 4)
                        {
                            scatternetConnections.put(socketServer.getRemoteDevice().getAddress(), cThreadServer);
                            if (scatternetConnections.size() == 1)
                            {
                                currentConnections.put(socketServer.getRemoteDevice().getAddress(), cThreadServer);
                                cThreadBridge = cThreadServer;
                                socketBridgeAddress = socketServer.getRemoteDevice().getAddress();
                                msg = new OscMessage("string");
                                msg.add("2");
                                msg.add(myUuid);
                                msg.add("you're a Bridge");
                                //msg.add(socketBridgeAddress);
                                msg.add("End");
                                cThreadBridge.sendMsgThread(msg.getBytes());

                            }
                            else
                            {
                                msg = new OscMessage("string");
                                msg.add("3");
                                msg.add(myUuid);
                                msg.add("Connect to a Bridge");
                                msg.add(socketBridgeAddress);
                                msg.add("End");
                                mustNotDeconnect = true;
                                cThreadServer.sendMsgThread(msg.getBytes());
                                Handler handler = new Handler(Looper.getMainLooper());
                                handler.postDelayed(() -> {
                                    if(cThreadServer != null) {
                                        if (cThreadServer.mmInStream != null) {
                                            try {
                                                cThreadServer.mmInStream.close();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        if (cThreadServer.mmOutStream != null) {
                                            try {
                                                cThreadServer.mmOutStream.close();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        if (cThreadServer.tempSocket != null) {
                                            try {
                                                cThreadServer.tempSocket.close();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        cThreadServer.interrupt();
                                    }
                                }, 2000);
                            }
                        }
                        else
                            {
                                msg = new OscMessage("string");
                                msg.add("2");
                                msg.add(myUuid);
                                msg.add("Emit Signal On connected");
                                //msg.add(socketBridgeAddress);
                                msg.add("End");
                                cThreadServer.sendMsgThread(msg.getBytes());
                                currentConnections.put(socketServer.getRemoteDevice().getAddress(), cThreadServer);
                                countConnectedToServerOrABridge += 1;
                                Log.e(TAG, "CurrentConnections " + currentConnections.toString() + "countConnectedToServerOrABridge " + countConnectedToServerOrABridge);
                        }
                        if(isServer) {
                            emitSignal("on_received_connection", socketServer.getRemoteDevice().getAddress(), myUuid);
                        }//socketServer = null;
                    }
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                mmServerSocket.close();
                //Log.e(TAG, "Closed Server side Socket");
            } catch (IOException e) {
                //Log.e(TAG, "Could not close the connect socket", e);
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

            byte[] buffer = new byte[2048];
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
                    if (!mustNotDeconnect)
                    {
                        if(imABridge
                            && tempSocket.getRemoteDevice().getAddress().equals(serverBluetoothAddress)
                            && !isFromResetConnection && !imConnectedToABridge)
                        {
                            Log.e(TAG, "Entered ImABridge && tempSocket.Address == ServerAddress && !isFromResetConnection ");

                            isComingfromServer = true;
                            msg = new OscMessage("string");
                            msg.add(String.valueOf(2));
                            msg.add(String.valueOf(myUuid));
                            msg.add("Server has Disconnected");
                            msg.add("End");
                            sendDataByte();
                            //imABridge = false;
                            imInTheScatternet = false;
                            imABridge = false;
                            imConnecting = false;
                            imConnectedToABridge = false;
                            connected = false;
                            resetConnection();
                            //emitSignal("on_disconnected", tempSocket.getRemoteDevice().getAddress(), myUuid);
                        }
                        else if(imABridge && isFromResetConnection)
                        {
                            Log.e(TAG, "Entered ImABridge && isFromResetConnection ");
                            isComingfromServer = true;
                            msg = new OscMessage("string");
                            msg.add(String.valueOf(3));
                            msg.add(String.valueOf(myUuid));
                            msg.add("A bridge disconnected");
                            msg.add("End");
                            sendDataByte();

                            imABridge = false;
                            connected = false;
                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.postDelayed(() -> {

                                mustNotDeconnect = false;
                                mustResetVars = true;
                                resetConnection();
//                                emitSignal("on_disconnected", tempSocket.getRemoteDevice().getAddress(), myUuid);
                            }, 1000);

                            //Log.e(TAG, "Entered ImABridge && isFromResetConnection ");
                           }
                        else if (!isFromResetConnection && imABridge && !imConnecting)
                        {
                            //countConnectedToServerOrABridge -= 1;
                            Log.e(TAG, "Entered ImABridge && !isFromResetConnection ");
                            isComingfromServer = false;
                            msg = new OscMessage("string");
                            msg.add(String.valueOf(3));
                            msg.add(String.valueOf(myUuid));
                            msg.add("Someone disconnected from bridge");
                            msg.add(tempSocket.getRemoteDevice().getAddress());
                            msg.add("End");
                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.postDelayed(() -> sendDataByte() , 300);
                        }
                        else if(!isServer && !imInTheScatternet && !imABridge && !imConnecting && connected)
                        {
                            //resetConnection();
                            Log.e(TAG, "Entered !ImABridge && !imInTheScatternet ");
                            emitSignal("on_disconnected", tempSocket.getRemoteDevice().getAddress(), myUuid);
                        }
                        else if(isServer && !imInTheScatternet && !imABridge && !imConnecting && connected)
                        {
                            Log.e(TAG, "Entered !ImABridge && !imInTheScatternet ");
                            emitSignal("on_disconnected", tempSocket.getRemoteDevice().getAddress(), myUuid);
                        }

                        if (!isServer && !imABridge)
                        {
                            connected = false;
                        }
                    }
                    else{
                        mustNotDeconnect = false;
                    }
                 //   GodotLib.calldeferred(instanceId, "_on_disconnected", new Object[]{tempSocket.getRemoteDevice().getAddress()});
                    //Log.e(TAG, "localhandler error");

                    int wait_time = 500;

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(() -> {
                        if (currentConnections.size() > 0 && !isFromResetConnection)
                        {
                            if (currentConnections.containsKey(tempSocket.getRemoteDevice().getAddress()))
                            {
                                currentConnections.remove(tempSocket.getRemoteDevice().getAddress());
                                if (!tempSocket.getRemoteDevice().getAddress().equals(socketBridgeAddress))
                                    countConnectedToServerOrABridge -= 1;
                            }
                        }
                        if (scatternetConnections.size() > 0 && !isFromResetConnection)
                        {
                            scatternetConnections.remove(tempSocket.getRemoteDevice().getAddress());
                        }
                        try
                        {
                            if (tempSocket != null)
                            {
                                if (tempSocket.getInputStream() != null)
                                {
                                    tempSocket.getInputStream().close();
                                }
                                if (tempSocket.getOutputStream() != null)
                                {
                                    tempSocket.getOutputStream().close();
                                }
                                tempSocket.close();
                            }
//                            tempSocket = null;
                            //Log.e(TAG, "Closed socket");
                        }
                        catch (Exception e)
                        {
                            //Log.e(TAG, "Closing connected socket error");
                        }

                    }, wait_time);
//                    try
//                                        {
////                        mmInStream.close();
////                        mmOutStream.close();
////                        tempSocket.close();
////                        connected = false
//                        //Log.e(TAG, "Closed socket");
//                    }
//                    catch (IOException f)
//                    {
//                        //Log.e(TAG, "Closing connected socket error");
//                    }
                    //resetConnection();
                    Log.e(TAG, "Reconnecting...."+tempSocket.getRemoteDevice().getAddress().equals(socketBridgeAddress) +" imInTheScatternet "+ imInTheScatternet +" isFromResetConnection "+isFromResetConnection +" isServer "+isServer +" imConnectedToABridge "+ imConnectedToABridge +" imConnecting "+ imConnecting);
                    if (imInTheScatternet && !isFromResetConnection && !isServer && imConnectedToABridge && !imConnecting)
                    {
                        Log.e(TAG, "Reconnecting....");
                        Random rand = new Random();
                        int wait_time2 = 1000 + rand.nextInt(500) + rand.nextInt(500);
                        imConnectedToABridge = false;
                        mustNotDeconnect = true;
                        mustResetVars = true;
                        resetConnection();
                        Handler handler2 = new Handler(Looper.getMainLooper());
                        handler2.postDelayed(() -> {
                            createSocket(serverBluetoothAddress);
                            //emitSignal("
                            // ", serverBluetoothName, serverBluetoothAddress);
                            connected = true;
                            imInTheScatternet = false;
                            imABridge = false;
                            imConnecting = true;
                        }, wait_time2);
                    }
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
//                    //Log.e(TAG, "Closed socket");
//                }
//                catch (Exception e)
//                {
//                    //Log.e(TAG, "Closing connected socket error");
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
                    //Log.e(TAG,  "Bluetooth Activated!");
                }
                else {
                    if(bluetoothRequired){
                        //Log.e(TAG, "Bluetooth wasn't activated, application closed!");
                        activity.finish();
                    }
                    else{
                        //Log.e(TAG, "Bluetooth wasn't activated!");
                    }
                }

                break;

            default:
                //Log.e(TAG, "ERROR: Unknown situation!");
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
