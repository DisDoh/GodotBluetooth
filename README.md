![Godot Bluetooth](/_img_/header.png?raw=true "Godot Bluetooth")

Original Text"This module is a native Bluetooth implementation intended to perform fundamental tasks in a communication between Microcontrollers (especially Arduino) and Games/Applications, made with Godot Engine, running inside Android. At the moment this module doesn't support communication between two mobile devices, but such functionality can be added in the future."

This module is a fork of a previous module for godot. I'm currently developping it for android communication process.
For now the module can send and receive a string between two android devices. It's still under development.

The module has been tested with [Godot-3.3.x-stable](https://github.com/godotengine/godot/releases) and two android devices.

The following text as been modified to work with Godot 3.3.x-stable:

## Available Features
> Native Dialog Box Layout;

> Easy Implementation Of Custom Layouts Inside Godot. 

## Build/Use the plugin

1. Open Android Studio
2. Open project GodotBluetooth
3. Build it and .aar would be in the output folder
4. Copy the plugin .aar into the android plugin folder of your Godot project
5. Enable the plugin in the export for Android options

**[note]** The mandatory permissions have to be added in godot/platform/android/java/AndroidManifest.xml


```XML
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
```

Or in the Godot export for Android permissions.

## Initialize GodotBluetooth
To use the module functions on your scripts, start the module as follows: 

```GDScript

var bluetooth

func _ready():
	if(Engine.has_singleton("GodotBluetooth")):
		bluetooth = Engine.get_singleton("GodotBluetooth")
		bluetooth.init(get_instance_id(), true)

```

And declare the functions you need:

```GDScript

func getPairedDevices(boolNativeLayout):
	if bluetooth:
		bluetooth.getPairedDevices(boolNativeLayout)

```
(You can learn more about *Singletons* and initializations [here](http://docs.godotengine.org/en/stable/tutorials/step_by_step/singletons_autoload.html)). 


Then use the functions wherever you want, following the API reference below. 

**[note]** The Android and the Microcontroller need to be paired before establishing a connection through this module for communication, you can use the options in the settings of your device to do this. Note that there is a difference between being paired and being connected, to be paired means that two devices are aware of each other's existence, to be connected means that the devices currently share an RFCOMM channel and are able to transmit data with each other.

## API Reference
The following functions are available:

**Startup Function**

```GDScript
void init(get_instance_id(), bool bluetoothRequired)
```
The *bluetoothRequired* is a boolean that tells if the bluetooth is required inside the game/application. If `true`, the game/application will close when the bluetooth is off and the user refuses to activate on the startup, if `false`, the game/application will continue in the occurrence of the same situation.

___

**Start the server on one phone**

```GDScript
void startServerThread()
```

You have to start the server once on one phone to be able to receive connection and communicate with other phones.
___

**Paired Devices Layout**

```GDScript
void getPairedDevices(bool nativeLayout)
```
The *nativeLayout* is a boolean that tells the module that, if `true`, you want the *Native Layout* showing the list of paired devices, if `false`, you want to build your own *Custom Layout* inside Godot.  

**For *Custom Layouts* Only**

```GDScript
void connect(int deviceID)
```
The *deviceID* is an integer representing the device you want to connect, only when using *Custom Layouts* you need to use this function, to get the *deviceID* see the `_on_single_device_found` on the callbacks section bellow. In summary, when using *Custom Layouts* you'll create your own visualization screen of paired devices and when the user chooses any of them you'll need to call this function to complete the connection.

___

**Send String Data**

```GDScript
void msgSetName("string")
void msgAddString(String) / repeat for more string in array
void msgAddString(str(int))
void sendMsg()

example:

func sendStrings(name):
if bluetooth:
    bluetooth.msgSetName("string")
    bluetooth.msgAddString("PlayerNames")
    bluetooth.msgAddString(name)
    bluetooth.msgAddString(name2)
    bluetooth.sendMsg()
    print("sended name")
else:
    print("no bluetooth")

```
To send a string you have to set the message to "string" with bluetooth.msgSetName("string").
Then you should set a tag for the array to be able to retrieve the string array (in the example it's "PlayerNames")
Then add as much string as you want( don't have done an overload test.[Update] done some implementation Test and can send up to 270 string with three packet maximum, the rest will be ignored). 

___

**Send Int Data /Deprecated**


The send int data func is deprecated as the string send function can be used for int too by casting in Godot.
But it's similar use as sending a string.
___

**Callbacks**

```GDScript
_on_data_received_string(String data_received)
_on_data_received_int(int data_received)
_on_disconnected()
_on_single_device_found(String deviceName, String deviceAddress, String deviceID)
_on_connected(String deviceName, String deviceAddress)
_on_connected_error()
```
The *data_received* is an array of string or int containing the data sended by the connected Android device. On the `_on_single_device_found`, the *deviceName*, *deviceAddress* and *deviceID* are the informations found about each of the paired devices individually, as the Android bluetooth adapter finds them (see the *GodotBluetoothDemos*/Deprecated folder for an example of use), the same variables on the `_on_connected` shows the information about the device that has been connected after the user make a choice.

___

**Further Information And Demo Projects**

For complete examples of usage for both *Native Layout* and *Custom Layout*, see the *GodotBluetoothDemos* folder. 

![Godot Bluetooth](/_img_/layouts.png?raw=true "Native and Custom Layouts")

**Note: The example is here just to show how it should work but actually the master branch of this fork is using godot 2.1.6 for his example and there is some change to the use of the plugin you should refer to this README**

