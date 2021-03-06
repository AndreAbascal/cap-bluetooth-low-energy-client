

package com.bleclient.plugin;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginHandle;
import com.getcapacitor.PluginMethod;
import org.json.JSONException;
import java.lang.Exception;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@NativePlugin(
		permissions = {
				Manifest.permission.BLUETOOTH,
				Manifest.permission.BLUETOOTH_ADMIN,
				Manifest.permission.ACCESS_FINE_LOCATION
		},
		requestCodes = {
				BluetoothLEClient.REQUEST_ENABLE_BT,
				BluetoothLEClient.REQUEST_LOCATION_PERMISSIONS
		}
)
public class BluetoothLEClient extends Plugin {

	static final int REQUEST_ENABLE_BT = 420;
	static final int REQUEST_LOCATION_PERMISSIONS = 2022;

	static final int SERVICES_UNDISCOVERED = 0;
	static final int SERVICES_DISCOVERING = 1;
	static final int SERVICES_DISCOVERED = 2;

	static final String BASE_UUID_HEAD = "0000";
	static final String BASE_UUID_TAIL = "-0000-1000-8000-00805F9B34FB";

	static final String keyDiscovered = "discoveredState";
	static final String keyPeripheral = "peripheral";
	static final String keyConnectionState = "connectionState";

	static final String keyEnabled = "enabled";
	static final String keyAvailable = "isAvailable";
	static final String keyAvailableDevices = "devices";
	static final String keyAddress = "id";
	static final String keyUuid = "uuid";
	static final String keyServices = "services";
	static final String keyService = "service";
	static final String keyAutoConnect = "autoConnect";
	static final String keyConnected = "connected";
	static final String keyDisconnected = "disconnected";
	static final String keyIncludedServices = "included";
	static final String keyCharacteristics = "characteristics";
	static final String keyCharacteristic = "characteristic";
	static final String keyDescriptor = "descriptor";
	static final String keyValue = "value";
	static final String keyDiscoveryState = "discovered";
	static final String keySuccess = "success";
	static final String keyDeviceType = "type";
	static final String keyBondState = "bondState";
	static final String keyDeviceName = "name";
	static final String keyCharacterisicDescripors = "descriptors";
	static final String keyCharacteristicProperies = "properties";
	static final String keyIsPrimaryService = "isPrimary";
	static final String keyPropertyAuthenticatedSignedWrites = "authenticatedSignedWrites";
	static final String keyPropertyBroadcast = "broadcast";
	static final String keyPropertyIndicate = "indicate";
	static final String keyPropertyNotify = "notify";
	static final String keyPropertyRead = "read";
	static final String keyPropertyWrite = "write";
	static final String keyPropertyWriteWithoutResponse = "writeWithoutResponse";

	static final String keyErrorAddressMissing = "Property id is required";
	static final String keyErrorBytesMissing = "First and/or last bytes are missing";
	static final String keyErrorCharacteristicMissing = "Property characteristic is required";
	static final String keyErrorCharacteristicNotFound = "Characteristic not found";
	static final String keyErrorConnectTimeout = "Connection reached timeout";
	static final String keyErrorDescriptorMissing = "Property descriptor is required";
	static final String keyErrorDescriptorNotFound = "Descriptor not found";
	static final String keyErrorNameMissing = "Property name is missing";
	static final String keyErrorNotConnected = "Not connected to peripheral";
	static final String keyErrorServiceNotFound = "Service not found";
	static final String keyErrorServiceMissing = "Property service is required";
	static final String keyErrorValueMissing = "Property value is required";
	static final String keyErrorValueSet = "Failed to set value";
	static final String keyErrorValueWrite = "Failed to write value";
	static final String keyErrorValueRead = "Failed to read value";

	static final String keyOperationConnect = "connectCallback";
	static final String keyOperationDisconnect = "disconnectCallback";
	static final String keyOperationDiscover = "discoverCallback";
	static final String keyOperationReadDescriptor = "readDescriptorCallback";
	static final String keyOperationWriteDescriptor = "writeDescriptorCallback";
	static final String keyOperationRead = "readCharacteristicCallback";
	static final String keyOperationWrite = "writeCharacteristicCallback";

	static final String keyFirstByte = "first";
	static final String keyLastByte = "last";

	static final int clientCharacteristicConfigurationUuid = 0x2902;

	private BluetoothAdapter bluetoothAdapter;
	private BluetoothLeScanner bleScanner;

	private ScanCallback scanCallback;
	private HashMap<String, BluetoothDevice> availableDevices = new HashMap<String, BluetoothDevice>();
	private HashMap<String, Object> connections = new HashMap<>();

	private String currentOperation = "";
	private BLEDevice deviceToDiscover;
	private boolean shouldStopForTimeout = false;

	private String firstByte = "CC";
	private String lastByte = "CF";
	private int[] packet = new int[0];

	private BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
		@Override
		public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
			super.onPhyUpdate(gatt, txPhy, rxPhy, status);
		}

		@Override
		public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
			super.onPhyRead(gatt, txPhy, rxPhy, status);
		}

		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			Log.d(getLogTag(),"CONNECTION STATE CHANGE: "+String.valueOf(newState));
			BluetoothDevice device = gatt.getDevice();
			String address = device.getAddress();
			HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
			if (connection == null) {
				return;
			}
			if (status == BluetoothGatt.GATT_SUCCESS) {
				switch (newState) {
					case BluetoothProfile.STATE_CONNECTING: {
						connection.put(keyConnectionState, BluetoothProfile.STATE_CONNECTING);
						break;
					}
					case BluetoothProfile.STATE_CONNECTED: {
						Log.d(getLogTag(),"currentOperation: "+currentOperation);
						if(currentOperation == "connect"){
							Log.e(getLogTag(), "SENDBACK RESULT CONNECT!");
							connection.put(keyConnectionState, BluetoothProfile.STATE_CONNECTED);
							PluginCall call = (PluginCall) connection.get(keyOperationConnect);
							if (call == null) {
								break;
							}
							boolean discoveryStarted = gatt.discoverServices();
							if (discoveryStarted) {
								connection.put(keyDiscovered, SERVICES_DISCOVERING);
								connection.put(keyOperationDiscover, call);
							} else {
								call.reject("Failed to start service discovery");
								return;
							}
						}else{
							connection.put(keyConnectionState, BluetoothProfile.STATE_CONNECTED);
							PluginCall call = (PluginCall) connection.get(keyOperationConnect);
							if (call == null) {
								break;
							}
							JSObject ret = new JSObject();
							addProperty(ret, keyConnected, true);
							call.resolve(ret);
							connection.remove(keyOperationConnect);
						}
						break;
					}
					case BluetoothProfile.STATE_DISCONNECTING: {
						connection.put(keyConnectionState, BluetoothProfile.STATE_DISCONNECTING);
						break;
					}
					case BluetoothProfile.STATE_DISCONNECTED: {
						Log.d(getLogTag(),"DISCONNECTED 01");
						connection.put(keyConnectionState, BluetoothProfile.STATE_DISCONNECTED);
						Log.d(getLogTag(),"DISCONNECTED 02");
						PluginCall call = (PluginCall) connection.get(keyOperationDisconnect);
						Log.d(getLogTag(),"DISCONNECTED 03");
						if (call == null) {
							Log.d(getLogTag(),"DISCONNECTED 03.1");
							break;
						}
						Log.d(getLogTag(),"DISCONNECTED 04");
						Log.d(getLogTag(),"DISCONNECTED 05");
						connection.remove(keyOperationDisconnect);
						connections.remove(address);
						Log.d(getLogTag(),"DISCONNECTED 06");
						JSObject ret = new JSObject();
						addProperty(ret, keyDisconnected, true);
						call.resolve(ret);
						break;
					}
				}
			} else {
				if (connection.get(keyOperationConnect) != null) {
					PluginCall call = (PluginCall) connection.get(keyOperationConnect);
					call.error("Unable to connect to Peripheral");
					connection.remove(keyOperationConnect);
					return;
				} else if (connection.get(keyOperationDisconnect) != null) {
					PluginCall call = (PluginCall) connection.get(keyOperationDisconnect);
					call.error("Unable to disconnect from Peripheral");
					connection.remove(keyOperationDisconnect);
					return;
				} else {
					Log.e(getLogTag(), "GATT operation unsuccessfull");
					return;
				}
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			BluetoothDevice device = gatt.getDevice();
			String address = device.getAddress();
			HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
			if(connection == null){
				Log.e(getLogTag(),"No connection");
			}
			if(currentOperation == "connect"){
				Log.d(getLogTag(), "onServicesDiscovered... RESULT DISCOVERSERVICES!");
				PluginCall call = (PluginCall) connection.get(keyOperationConnect);
				if (call == null) {
					Log.e(getLogTag(), "No saved call");
					return;
				}
				Log.d(getLogTag(), "onServicesDiscovered... THERE IS A CALL");
				String result = "error";
				if (status == BluetoothGatt.GATT_SUCCESS) {
					Log.d(getLogTag(),"onServicesDiscovered... GATT SUCCESS");
					List<BluetoothGattService> services = gatt.getServices();
					Log.d(getLogTag(),"onServicesDiscovered... WE HAVE "+String.valueOf(services.size())+" SERVICES");
					for(int i=0;i<services.size();i++){
						BluetoothGattService service = services.get(i);
						Log.d(getLogTag(),"SERVICE FOUND: "+service.getUuid().toString());
						Log.d(getLogTag(),"SERVICE DESIRED: "+deviceToDiscover.getService().toString());
						if(service.getUuid().equals(deviceToDiscover.getService())){
							Log.d(getLogTag(),"onServicesDiscovered... WE HAVE "+String.valueOf(services.size())+" SERVICES");
							Log.d(getLogTag(),"FOUND SERVICE: "+service.getUuid().toString());
							List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
							Log.d(getLogTag(),"FOUND "+String.valueOf(characteristics.size())+" CHARACTERISTICS");
							for(int j=0;j<characteristics.size();j++){
								BluetoothGattCharacteristic characteristic = characteristics.get(j);
								Log.d(getLogTag(),"CHARACTERISTIC FOUND: "+service.getUuid().toString());
								Log.d(getLogTag(),"CHARACTERISTIC DESIRED: "+deviceToDiscover.getCharacteristic().toString());
								if(characteristic.getUuid().equals(deviceToDiscover.getCharacteristic())){
									shouldStopForTimeout = false;
									Log.d(getLogTag(),"FOUND CHARACTERISTIC: "+characteristic.getUuid().toString());
									/*Boolean notificationResult = BluetoothLEClient.this.setNotificationEnabled(gatt,characteristic);
									if(notificationResult){
										Log.d(getLogTag(),"CHARACTERISTIC NOTIFICATION ON");
									}else{
										Log.d(getLogTag(),"CHARACTERISTIC NOTIFICATION OFF");
									}*/
									result = "success";
									break;
								}else{
									Log.d(getLogTag(),"onServicesDiscovered...CARACTERISTICAS DIFERENTES...");
								}
							}
							if(result == "success"){
								break;
							}
						}else{
							Log.d(getLogTag(),"onServicesDiscovered...SERVIÇOS DIFERENTES...");
						}
					}
				}
				if(result == "success"){
					Log.d(getLogTag(),"RETURNING SUCCESS TO CONNECT(params)");
					connection.put(keyDiscovered, SERVICES_DISCOVERED);
					JSObject ret = new JSObject();
					addProperty(ret, keyConnected, true);
					Log.d(getLogTag(),"ret: "+ret.toString());
					call.resolve(ret);
				}else{
					Log.d(getLogTag(),"RETURNING SERVICE NOT FOUND ERROR TO CONNECT(params)");
					call.reject(keyErrorServiceNotFound);
					return;
				}
			}else{
				PluginCall call = (PluginCall) connection.get(keyOperationDiscover);
				if (call == null) {
					Log.e(getLogTag(), "No saved call");
					return;
				}
				JSObject ret = new JSObject();
				if (status == BluetoothGatt.GATT_SUCCESS) {
					connection.put(keyDiscovered, SERVICES_DISCOVERED);
					addProperty(ret, keyDiscoveryState, true);
					call.resolve(ret);
				} else {
					call.error("Service discovery unsuccessful");
				}
				connection.remove(keyOperationDiscover);
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			Log.d(getLogTag(),"onCharacteristicRead...");
			Log.d(getLogTag(),"GATT READ STATUS: "+status);
			Log.d(getLogTag(),"GATT READ VALUE: "+characteristic.getValue().toString());
			BluetoothDevice device = gatt.getDevice();
			String address = device.getAddress();
			HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
			if (connection == null) {
				Log.e(getLogTag(), "No connection found");
				return;
			}
			PluginCall call = (PluginCall) connection.get(keyOperationRead);
			connection.remove(keyOperationRead);
			if (call == null) {
				Log.e(getLogTag(), "No callback for operation found");
				return;
			}
			JSObject ret = new JSObject();
			if (status == BluetoothGatt.GATT_SUCCESS || status == 10) {
				int[] arr = this.byteArrayToUnsignedIntArray(characteristic.getValue());
				addProperty(ret, keyValue, JSArray.from(arr));
				//addProperty(ret, keyValue, JSArray.from(characteristicValue));
				call.resolve(ret);
			} else {
				call.error(keyErrorValueRead);
			}
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			Log.d(getLogTag(),"onCharacteristicWrite...");
			BluetoothDevice device = gatt.getDevice();
			String address = device.getAddress();
			HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
			if (connection == null) {
				Log.e(getLogTag(), "No connection found");
				return;
			}
			PluginCall call = (PluginCall) connection.get(keyOperationWrite);
			connection.remove(keyOperationWrite);
			if (call == null) {
				Log.e(getLogTag(), "No callback for operation found");
				return;
			}
			JSObject ret = new JSObject();
			if (status == BluetoothGatt.GATT_SUCCESS) {
				byte[] value = characteristic.getValue();
				Log.d(getLogTag(),"onCharacteristicWrite SUCCESS");
				Log.d(getLogTag(),"onCharacteristicWrite c.getValue: "+characteristic.getValue().toString());
				Log.d(getLogTag(),"CURRENT OPERATION: "+currentOperation);
				addProperty(ret, keyValue, JSArray.from(value));
				if(currentOperation == "write"){
					call.resolve();
				}
			} else {
				call.error(keyErrorValueWrite);
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			Log.d(getLogTag(),"onCharacteristicChanged... characteristic: "+characteristic.getUuid().toString());
			BluetoothDevice device = gatt.getDevice();
			String address = device.getAddress();
			UUID characteristicUuid = characteristic.getUuid();
			BluetoothGattService service = characteristic.getService();
			UUID serviceUuid = service.getUuid();
			byte[] characteristicValue = characteristic.getValue();
			int[] part = this.byteArrayToUnsignedIntArray(characteristicValue);
			Integer characteristic16BitUuid = get16BitUUID(characteristicUuid);
			if (characteristic16BitUuid == null) {
				return;
			}
			JSObject ret = new JSObject();
			//addProperty(ret, keyValue, JSArray.from(normalizedValue));
			Log.d(getLogTag(),"NOTIFYING EVENT: "+characteristic16BitUuid.toString());
			Log.d(getLogTag(),"NOTIFYING DATA: "+ret.toString());
			this.runNotificationCheck(characteristic16BitUuid.toString(),part);
			//notifyListeners(characteristic16BitUuid.toString(), ret);
			if(currentOperation == "write"){
				Log.d(getLogTag(),"THIS IS WHERE WRITE WOULD RETURN...");
			}
		}

		@Override
		public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			BluetoothDevice device = gatt.getDevice();
			String address = device.getAddress();
			HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
			if (connection == null) {
				return;
			}
			PluginCall call = (PluginCall) connection.get(keyOperationReadDescriptor);
			if (call == null) {
				return;
			}
			if (status == BluetoothGatt.GATT_SUCCESS) {
				JSObject ret = new JSObject();
				byte[] value = descriptor.getValue();
				addProperty(ret, keyValue, JSArray.from(value));
				call.resolve(ret);
			} else {
				call.error(keyErrorValueRead);
			}
			connection.remove(keyOperationReadDescriptor);
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			Log.d(getLogTag(),"onDescriptorWrite... status: "+status);
			BluetoothDevice device = gatt.getDevice();
			String address = device.getAddress();
			HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
			if (connection == null) {
				return;
			}
			PluginCall call = (PluginCall) connection.get(keyOperationWriteDescriptor);
			if (call == null) {
				return;
			}
			byte[] value = descriptor.getValue();
			JSObject ret = new JSObject();
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Log.d(getLogTag(),"onDescriptorWrite SUCCESS");
				addProperty(ret, keyValue, JSArray.from(value));
				call.resolve(ret);
			} else {
				Log.d(getLogTag(),"onDescriptorWrite ERROR");
				call.error(keyErrorValueWrite);
			}
			connection.remove(keyOperationWriteDescriptor);
		}

		@Override
		public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
			super.onReliableWriteCompleted(gatt, status);
		}

		@Override
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			super.onReadRemoteRssi(gatt, rssi, status);
		}

		@Override
		public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
			super.onMtuChanged(gatt, mtu, status);
		}

		private void respondNotification(String eventName, int[] packet){
			this.runDebugPacket(packet);
			JSObject ret = new JSObject();
			addProperty(ret, keyValue, JSArray.from(packet));
			notifyListeners(eventName,ret);
		}

		private void runClearPacket(){
			BluetoothLEClient.this.packet = new int[0];
		}

		private void runDebugPacket(int[] packet){
			String res = "";
			for(int i=0;i<packet.length;i++){
				res += Integer.toHexString(packet[i])+" ";
			}
			Log.d(getLogTag(),"PACKET: "+res);
		}

		private void runNotificationCheck(String eventName, int[] unsignedBytes){
			int expectedFirstByte = Integer.parseInt(BluetoothLEClient.this.firstByte,16);
			int expectedLastByte = Integer.parseInt(BluetoothLEClient.this.lastByte,16);
			int firstByte = unsignedBytes[0];
			int lastByte = unsignedBytes[unsignedBytes.length-1];
			if(expectedFirstByte == firstByte && expectedLastByte == lastByte){
				this.respondNotification(eventName, unsignedBytes);
				this.runClearPacket();
				//IT IS A FULL PACKET, SO NOTIFY IT AND CLEAR IT
			}else if(expectedFirstByte == firstByte){
				//IT IS THE FIRST PART OF A PACKET, SO SET ITS VALUE AND DO NOT NOTIFY
				BluetoothLEClient.this.packet = unsignedBytes;
			}else if(expectedLastByte == lastByte){
				//IT IS THE LAST PART OF A PACKET, SO ADD TO ITS VALUE AND NOTIFY
				this.respondNotification(eventName, this.concatArray(BluetoothLEClient.this.packet,unsignedBytes));
				this.runClearPacket();
			}else {
				//ADD TO CURRENT PACKET AND STILL DO NOT NOTIFY
				BluetoothLEClient.this.packet = this.concatArray(BluetoothLEClient.this.packet,unsignedBytes);
			}
		}

		private int[] concatArray(int[] a, int[] b) {
			int[] c = new int[a.length + b.length];
			int i = 0;
			for (int x : a) { c[i] = x; i ++; }
			for (int x : b) { c[i] = x; i ++; }
			return c;
		}

		private int[] byteArrayToUnsignedIntArray(byte[] bytearray){
			int[] arr = new int[bytearray.length];
			for(int i=0;i<bytearray.length;i++){
				int original = bytearray[i];
				int unsigned = (original & 0xff);
				arr[i] = unsigned;
			}
			return arr;
		}

	};

	private class BLEScanCallback extends ScanCallback {

		@Override
		public void onBatchScanResults(List<ScanResult> results) {
			super.onBatchScanResults(results);
			Log.d(getLogTag(),"onBatchScanResults...");
		}

		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			super.onScanResult(callbackType, result);
			Log.d(getLogTag(),"SCAN RESULT DESCRIBE CONTENTS: "+result.toString());
			ScanRecord record = result.getScanRecord();
			Log.d(getLogTag(),"record.getDeviceName(): "+record.getDeviceName());
			Log.d(getLogTag(),"record.toString(): "+record.toString());
			Log.d(getLogTag(),"onScanResult...");
			Log.d(getLogTag(),"onScanResult... currentOperation: "+currentOperation);

			BluetoothDevice device = result.getDevice();
			Log.d(getLogTag(),"onScanResult device: "+device.toString());
			if (!availableDevices.containsKey(device.getAddress())) {
				availableDevices.put(device.getAddress(), device);
			}
			Log.d(getLogTag(),"onScanResult 03");
			if(currentOperation == "connect"){
				Log.d(getLogTag(), "RESULT SCAN!");
				String name = device.getName();
				if(name == null){
					name = record.getDeviceName();
					Log.d(getLogTag(),"NAME WAS NULL... BUT I FOUND THIS: "+name);
				}
				byte[] recordBytes = record.getBytes();
				final BleAdvertisedData badata = BleUtil.parseAdvertisedData(recordBytes);
				Log.d(getLogTag(),"badata.getName(): "+badata.getName());
				String name2 = deviceToDiscover.getName();
				Log.d(getLogTag(), "NAME: "+name);
				Log.d(getLogTag(), "deviceToDiscover.getName(): "+name2);
				if(name != null && name.equals(name2)){
					Log.d(getLogTag(),"Calling stop scan from onScanResult");
					stopScan(false);
					Log.d(getLogTag(),"SAME NAME!");
					PluginCall call = getSavedCall();
					if(call == null){
						return;
					}
					HashMap<String, Object> con = new HashMap<>();
					con.put(keyDiscovered, SERVICES_UNDISCOVERED);
					con.put(keyOperationConnect, call);
					BluetoothGatt gatt;
					/*if(Build.VERSION.SDK_INT >= 23){
						Log.d(getLogTag(),"BLE CONNECTGATT LE ONLY");
						gatt = device.connectGatt(BluetoothLEClient.this.getContext(), false, BluetoothLEClient.this.bluetoothGattCallback,BluetoothDevice.TRANSPORT_LE);
					}else{
						Log.d(getLogTag(),"BLE CONNECTGATT AUTO(?)");
						gatt = device.connectGatt(BluetoothLEClient.this.getContext(), false, BluetoothLEClient.this.bluetoothGattCallback);
					}*/
					gatt = device.connectGatt(BluetoothLEClient.this.getContext(), false, BluetoothLEClient.this.bluetoothGattCallback);
					con.put(keyPeripheral, gatt);
					connections.put(device.getAddress(), con);
					Log.d(getLogTag(),"NEW CONNECTION: "+con.toString());
				}else{
					Log.d(getLogTag(),name+" != "+name2);
				}
			}
			return;
		}

		@Override
		public void onScanFailed(int errorCode) {
			Log.e(getLogTag(), "BLE scan failed with code " + errorCode);
			return;
		}
	}

	@Override
	protected void handleOnStart() {
		BluetoothManager bluetoothManager = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
		bluetoothAdapter = bluetoothManager.getAdapter();
	}

	@PluginMethod()
	public void connect(PluginCall call) {
		BluetoothLEClient.this.packet = new int[0];
		String id = call.getString(keyAddress);
		Log.d(getLogTag(),"BLE CONNECT ID: "+id);
		String name = call.getString("name");
		if(name == null){
			call.reject(keyErrorNameMissing);
			return;
		}
		Log.d(getLogTag(),"BLE CONNECT NAME: "+name);
		String service = call.getString("service");
		if(service == null){
			call.reject(keyErrorServiceMissing);
			return;
		}
		Log.d(getLogTag(),"BLE CONNECT SERVICE: "+service);
		UUID serviceUUID = get128BitUUID(service);
		String characteristic = call.getString("characteristic");
		if(characteristic == null){
			call.reject(keyErrorCharacteristicMissing);
			return;
		}
		Log.d(getLogTag(),"BLE CONNECT CHARACTERISTIC: "+characteristic);
		UUID characteristicUUID = get128BitUUID(characteristic);
		int connectTimeout = call.getInt("timeout",0);
		Log.d(getLogTag(),"BLE CONNECT TIMEOUT: "+String.valueOf(connectTimeout));
		if(connectTimeout == 0){
			connectTimeout = 5000;
		}
		saveCall(call);
		this.deviceToDiscover = new BLEDevice(name,serviceUUID,characteristicUUID);
		bleScanner = bluetoothAdapter.getBluetoothLeScanner();
		/*Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
		for (BluetoothDevice bd : bondedDevices) {
			JSObject obj = BleUtil.getBondedDeviceJSON(bd);
			Log.d(getLogTag(),"BONDED DEVICE: "+obj.toString());
			//this.unpairDevice(bd);
		}*/
		availableDevices = new HashMap<String, BluetoothDevice>();
		scanCallback = new BLEScanCallback();
		Log.d(getLogTag(),"PLUGIN CONNECT 04");
		ScanSettings settings;
		/*if(Build.VERSION.SDK_INT >= 23){
			Log.d(getLogTag(),"SDK >= 23, SO WE WILL USE MATCH MODE AGGRESSIVE AND MATCH ONE AD");
			settings = new ScanSettings.Builder()
					.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
					.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
					//.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
					.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
					.setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
					.build();
		}else{
			settings = new ScanSettings.Builder()
					.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
					.build();
		}*/
		settings = new ScanSettings.Builder()
					.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
					.build();
		Log.d(getLogTag(),"PLUGIN CONNECT 05");
		currentOperation = "connect";
		List<ScanFilter> filters = new ArrayList<>();
		Log.d(getLogTag(),"PLUGIN CONNECT SEARCHING FOR NAME: "+name);
		ScanFilter macFilter = new ScanFilter.Builder().setDeviceAddress(id).build();
		ScanFilter nameFilter = new ScanFilter.Builder().setDeviceName(name).build();
		//filters.add(macFilter);
		//filters.add(nameFilter);
		bleScanner.startScan(filters, settings, scanCallback);
		//TODO: TRY TO USE THE startScan() FUNCTION WITHOUT filters AND settings
		//bleScanner.startScan(scanCallback);
		shouldStopForTimeout = true;
		Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if(shouldStopForTimeout){
					BluetoothLEClient.this.stopScan(true);
				}else{
					Log.d(getLogTag(),"RUN PREVENTED");
				}
			}
		},connectTimeout);
		Log.d(getLogTag(),"PLUGIN CONNECT 06");
	}

	@PluginMethod()
	public void disableNotifications(PluginCall call) {
		String address = call.getString(keyAddress);
		if (address == null) {
			call.reject(keyErrorAddressMissing);
			return;
		}
		HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
		if (connection == null) {
			call.reject(keyErrorNotConnected);
			return;
		}
		BluetoothGatt gatt = (BluetoothGatt) connection.get(keyPeripheral);
		Integer propertyService = call.getInt(keyService);
		if (propertyService == null) {
			call.reject(keyErrorServiceMissing);
			return;
		}
		UUID serviceUuid = get128BitUUID(propertyService);
		BluetoothGattService service = gatt.getService(serviceUuid);
		if (service == null) {
			call.reject(keyErrorServiceNotFound);
			return;
		}
		Integer propertyCharacteristic = call.getInt(keyCharacteristic);
		if (propertyCharacteristic == null) {
			call.reject(keyErrorCharacteristicMissing);
			return;
		}
		UUID characteristicUuid = get128BitUUID(propertyCharacteristic);
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
		if (characteristic == null) {
			call.reject(keyErrorCharacteristicNotFound);
			return;
		}
		UUID clientCharacteristicConfDescriptorUuid = get128BitUUID(clientCharacteristicConfigurationUuid);
		BluetoothGattDescriptor notificationDescriptor = characteristic.getDescriptor(clientCharacteristicConfDescriptorUuid);
		if (notificationDescriptor == null) {
			call.reject(keyErrorDescriptorNotFound);
			return;
		}
		boolean notificationUnset = gatt.setCharacteristicNotification(characteristic, false);
		if (!notificationUnset) {
			call.reject("Unable to unset characteristic notification");
			return;
		}
		boolean result = notificationDescriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
		if (!result) {
			call.reject(keyErrorValueSet);
			return;
		}
		connection.put(keyOperationWriteDescriptor, call);
		result = gatt.writeDescriptor(notificationDescriptor);
		if (!result) {
			connection.remove(keyOperationWriteDescriptor);
			call.reject(keyErrorValueWrite);
			return;
		}
	}

	@PluginMethod()
	public void disconnect(PluginCall call) {
		BluetoothManager bluetoothManager = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
		Log.d(getLogTag(),"DISCONNECT 01");
		String address = call.getString(keyAddress);
		if (address == null) {
			Log.d(getLogTag(),"DISCONNECT NO ADDRESS");
			call.reject(keyErrorAddressMissing);
			return;
		}
		HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
		if (connection == null) {
			List<BluetoothDevice> devices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
			for (BluetoothDevice bd : devices) {
				Log.d(getLogTag(),"ALREADY CONNECTED TO: "+bd.toString());
			}
			Log.d(getLogTag(),"DISCONNECT NO CONNECTION");
			JSObject ret = new JSObject();
			addProperty(ret, keyDisconnected, true);
			call.resolve(ret);
			return;
		}
		connection.put(keyOperationDisconnect, call);
		BluetoothGatt gatt = (BluetoothGatt) connection.get(keyPeripheral);
		if(gatt == null){
			Log.d(getLogTag(),"DISCONNECT GATT OBJECT IS DEAD/IS NULL");
			call.resolve();
			return;
		}
		BluetoothDevice device = gatt.getDevice();
		if(device == null){
			JSObject ret = new JSObject();
			addProperty(ret, keyDisconnected, true);
			call.resolve(ret);
			return;
		}
		int connectionState = bluetoothManager.getConnectionState(device,BluetoothProfile.GATT);
		Log.d(getLogTag(),"GATT CURRENT CONNECTION STATE: "+connectionState);
		if(connectionState == BluetoothProfile.STATE_DISCONNECTED){
			JSObject ret = new JSObject();
			addProperty(ret, keyDisconnected, true);
			call.resolve(ret);
			return;
		}
		Log.d(getLogTag(),"GATT: "+gatt.toString());
		call.save();
		gatt.disconnect();
		return;
	}

	@PluginMethod()
	public void discover(PluginCall call) {
		String address = call.getString(keyAddress);
		if (address == null) {
			call.reject(keyErrorAddressMissing);
			return;
		}
		HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
		if (connection == null) {
			call.reject(keyErrorNotConnected);
			return;
		}
		BluetoothGatt gatt = (BluetoothGatt) connection.get(keyPeripheral);
		boolean discoveryStarted = gatt.discoverServices();
		if (discoveryStarted) {
			connection.put(keyDiscovered, SERVICES_DISCOVERING);
			connection.put(keyOperationDiscover, call);
		} else {
			call.reject("Failed to start service discovery");
			return;
		}
	}

	@PluginMethod()
	public void enable(PluginCall call) {
		Log.d(getLogTag(),"enable 01");
		if (!bluetoothAdapter.isEnabled()) {
			Log.d(getLogTag(),"enable 02.1");
			saveCall(call);
			Log.d(getLogTag(),"enable 03");
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			Log.d(getLogTag(),"enable 04");
			getActivity().startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
			//startActivityForResult(getActivity(), enableIntent, REQUEST_ENABLE_BT);
			Log.d(getLogTag(),"enable 05");
		}else{
			Log.d(getLogTag(),"enable 02.2");
			JSObject ret = new JSObject();
			ret.put(keyEnabled, true);
			call.resolve(ret);
		}
	}

	@PluginMethod()
	public void enableNotifications(PluginCall call) {
		Log.d(getLogTag(),"enableNotifications 01");
		String address = call.getString(keyAddress);
		if (address == null) {
			call.reject(keyErrorAddressMissing);
			return;
		}
		Log.d(getLogTag(),"address: "+address);
		HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
		if (connection == null) {
			call.reject(keyErrorNotConnected);
			return;
		}
		BluetoothGatt gatt = (BluetoothGatt) connection.get(keyPeripheral);
		Integer propertyService = call.getInt(keyService);
		Log.d(getLogTag(),"service: "+String.valueOf(propertyService));
		if (propertyService == null) {
			call.reject(keyErrorServiceMissing);
			return;
		}
		UUID serviceUuid = get128BitUUID(propertyService);
		BluetoothGattService service = gatt.getService(serviceUuid);
		if (service == null) {
			call.reject(keyErrorServiceNotFound);
			return;
		}
		Log.d(getLogTag(),"enableNotifications 02");
		Integer propertyCharacteristic = call.getInt(keyCharacteristic);
		Log.d(getLogTag(),"characteristic: "+String.valueOf(propertyCharacteristic));
		if (propertyCharacteristic == null) {
			call.reject(keyErrorCharacteristicMissing);
			return;
		}
		UUID characteristicUuid = get128BitUUID(propertyCharacteristic);
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
		if (characteristic == null) {
			call.reject(keyErrorCharacteristicNotFound);
			return;
		}
		currentOperation = "enableNotifications";
		Log.d(getLogTag(),"enableNotifications 03... clientCharacteristicConfigurationUuid: "+String.valueOf(clientCharacteristicConfigurationUuid));
		Log.d(getLogTag(),"enableNotifications 03.1... IN HEX: "+Integer.toHexString(clientCharacteristicConfigurationUuid));
		UUID clientCharacteristicConfDescriptorUuid = get128BitUUID(clientCharacteristicConfigurationUuid);
		Log.d(getLogTag(),"clientCharacteristicConfDescriptorUuid: "+clientCharacteristicConfDescriptorUuid.toString());
		BluetoothGattDescriptor notificationDescriptor = characteristic.getDescriptor(clientCharacteristicConfDescriptorUuid);
		if (notificationDescriptor == null) {
			call.reject(keyErrorDescriptorNotFound);
			return;
		}
		boolean notificationSet = gatt.setCharacteristicNotification(characteristic, true);
		if (!notificationSet) {
			call.reject("Unable to set characteristic notification");
			return;
		}
		boolean result = false;
		Log.d(getLogTag(),"enableNotifications 04");
		if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == BluetoothGattCharacteristic.PROPERTY_NOTIFY) {
			Log.d(getLogTag(),"ENABLE NOTIFICATION IN DESCRIPTOR");
			result = notificationDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
		} else {
			Log.d(getLogTag(),"ENABLE INDICATION IN DESCRIPTOR");
			result = notificationDescriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
		}
		if (!result) {
			call.reject(keyErrorValueSet);
			return;
		}
		Log.d(getLogTag(),"enableNotifications 05");
		connection.put(keyOperationWriteDescriptor, call);
		result = gatt.writeDescriptor(notificationDescriptor);
		if (!result) {
			Log.d(getLogTag(),"enableNotifications ERROR");
			connection.remove(keyOperationWriteDescriptor);
			call.reject(keyErrorValueWrite);
			return;
		}else{
			Log.d(getLogTag(),"enableNotifications 06");
		}
	}

	@PluginMethod()
	public void getCharacteristic(PluginCall call) {
		String address = call.getString(keyAddress);
		if (address == null) {
			call.reject(keyErrorAddressMissing);
			return;
		}
		HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
		if (connection == null) {
			call.reject(keyErrorNotConnected);
			return;
		}
		BluetoothGatt gatt = (BluetoothGatt) connection.get(keyPeripheral);
		Integer propertyService = call.getInt(keyService);
		if (propertyService == null) {
			call.reject(keyErrorServiceMissing);
			return;
		}
		BluetoothGattService service = gatt.getService(get128BitUUID(propertyService));
		if (service == null) {
			call.reject(keyErrorServiceNotFound);
			return;
		}
		Integer propertyCharacteristic = call.getInt(keyCharacteristic);
		if (propertyCharacteristic == null) {
			call.reject(keyErrorCharacteristicMissing);
			return;
		}
		Log.d(getLogTag(),"characteristic: "+String.valueOf(propertyCharacteristic));
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(get128BitUUID(propertyCharacteristic));
		if (characteristic == null) {
			call.reject(keyErrorCharacteristicNotFound);
			return;
		}
		JSObject retCharacteristic = createJSBluetoothGattCharacteristic(characteristic);
		call.resolve(retCharacteristic);
	}

	@PluginMethod()
	public void getCharacteristics(PluginCall call) {
		String address = call.getString(keyAddress);
		if (address == null) {
			call.reject(keyErrorAddressMissing);
			return;
		}
		HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
		if (connection == null) {
			call.reject(keyErrorNotConnected);
			return;
		}
		BluetoothGatt gatt = (BluetoothGatt) connection.get(keyPeripheral);
		Integer propertyService = call.getInt(keyService);
		if (propertyService == null) {
			call.reject(keyErrorServiceMissing);
			return;
		}
		BluetoothGattService service = gatt.getService(get128BitUUID(propertyService));
		if (service == null) {
			call.reject(keyErrorServiceNotFound);
			return;
		}
		List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
		ArrayList<JSObject> retCharacteristics = new ArrayList<>();
		for (BluetoothGattCharacteristic characteristic : characteristics) {
			retCharacteristics.add(createJSBluetoothGattCharacteristic(characteristic));
		}
		JSObject ret = new JSObject();
		addProperty(ret, keyCharacteristics, JSArray.from(retCharacteristics.toArray()));
		call.resolve(ret);
	}

	@PluginMethod()
	public void getService(PluginCall call) {
		String address = call.getString(keyAddress);
		if (address == null) {
			call.reject(keyErrorAddressMissing);
			return;
		}
		HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
		if (connection == null) {
			call.reject(keyErrorNotConnected);
			return;
		}
		BluetoothGatt peripheral = (BluetoothGatt) connection.get(keyPeripheral);
		Integer propertyService = call.getInt(keyUuid);
		if (propertyService == null) {
			call.reject(keyErrorServiceMissing);
			return;
		}
		BluetoothGattService service = peripheral.getService(get128BitUUID(propertyService));
		if (service == null) {
			call.reject(keyErrorServiceNotFound);
			return;
		}
		call.resolve(createJSBluetoothGattService(service));
	}

	@PluginMethod()
	public void getServices(PluginCall call) {
		String address = call.getString(keyAddress);
		if (address == null) {
			call.reject(keyErrorAddressMissing);
			return;
		}
		HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
		if (connection == null) {
			call.reject(keyErrorNotConnected);
			return;
		}
		BluetoothGatt gatt = (BluetoothGatt) connection.get(keyPeripheral);
		List<BluetoothGattService> services = gatt.getServices();
		ArrayList<JSObject> retServices = new ArrayList<>();
		for (BluetoothGattService service : services) {
			retServices.add(createJSBluetoothGattService(service));
		}
		JSObject ret = new JSObject();
		addProperty(ret, keyServices, JSArray.from(retServices.toArray()));
		call.resolve(ret);
	}

	@PluginMethod()
	public void hasPermissions(PluginCall call){
		currentOperation = "hasPermissions";
		Log.d(getLogTag(),"WHAT IS MY ACTIVITY: "+getActivity().getClass().getName());
		Log.d(getLogTag(),"HAS PERMISSIONS...");
		if (!hasAllPermissions()) {
			Log.d(getLogTag(),"NEED PERMISSIONS...");
			saveCall(call);
			String message = "Este aplicativo coleta dados de localização para permitir a compra através das máquinas Purificatta, mesmo quando o aplicativo está fechado ou não em uso.\nNão utilizaremos seus dados para nenhum outro uso.";
			if(call.getString("disclosure_message") != null){
				message = call.getString("disclosure_message");
			}
			String title = "Usar sua localização";
			if(call.getString("disclosure_title") != null){
				title = call.getString("disclosure_title");
			}
			this.showLocationProminentDisclosure(message, title,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							switch(which){
								case DialogInterface.BUTTON_NEGATIVE:
								case DialogInterface.BUTTON_NEUTRAL:
									dialog.dismiss();
									JSObject ret = new JSObject();
									ret.put("isAllowed",false);
									call.resolve(ret);
									break;
								case DialogInterface.BUTTON_POSITIVE:
									dialog.dismiss();
									pluginRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION, REQUEST_LOCATION_PERMISSIONS);
							}
						}
					}
			);
			//requestAllPermissions();
		}else{
			Log.d(getLogTag(),"WONT NEED PERMISSIONS...");
			JSObject ret = new JSObject();
			ret.put("isAllowed",true);
			call.resolve(ret);
		}
	}

	public void requestAllPermissions(){
		NativePlugin annotation = handle.getPluginAnnotation();
		ActivityCompat.requestPermissions(getActivity(), annotation.permissions(), annotation.permissionRequestCode());
	}

	private void showLocationProminentDisclosure(String message, String title,
	DialogInterface.OnClickListener onClick){
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setCancelable(false)
			.setMessage(message)
			.setNegativeButton("Cancelar", onClick)
			.setPositiveButton("OK", onClick)
			.setTitle(title);
		AlertDialog alert = builder.create();
		alert.show();
	}

	private boolean hasAllPermissions() {
		PackageManager mPackageManager = getActivity().getPackageManager();
		PluginHandle handle = getPluginHandle();
		NativePlugin annotation = handle.getPluginAnnotation();
		String[] permissions = annotation.permissions();
		for(int i=0;i<permissions.length;i++) {
			String permission = permissions[i];
			int status;
			if(Build.VERSION.SDK_INT >= 23){
				status = getActivity().checkSelfPermission(permission);
			}else{
				status = mPackageManager.checkPermission(permission,getActivity().getPackageName());
			}
			if(status != PackageManager.PERMISSION_GRANTED){
				Log.d(getLogTag(),"NO PERMISSION: "+permission);
				return false;
			}else{
				Log.d(getLogTag(),"I HAVE PERMISSION: "+permission);
			}
		}
		return true;
	}

	@PluginMethod()
	public void isAvailable(PluginCall call) {
		JSObject ret = new JSObject();
		if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			ret.put(keyAvailable, true);
			call.resolve(ret);
		} else {
			ret.put(keyAvailable, false);
			call.resolve(ret);
		}
	}

	@PluginMethod()
	public void isConnected(PluginCall call) {
		String address = call.getString(keyAddress);
		if (address == null) {
			call.reject(keyErrorAddressMissing);
			return;
		}
		HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
		if(connection != null){
			Log.d(getLogTag(),"CONNECT 04.1");
			Log.d(getLogTag(),"CONNECTION: "+connection.toString());
			boolean isAlreadyConnected = (Integer) connection.get(keyConnectionState) == BluetoothProfile.STATE_CONNECTED;
			boolean servicesDiscovered = (Integer) connection.get(keyDiscovered) == SERVICES_DISCOVERED;
			if(isAlreadyConnected && servicesDiscovered ){
				JSObject ret = new JSObject();
				addProperty(ret, keyConnected, true);
				call.resolve(ret);
				return;
			}
		}
		Log.d(getLogTag(),"IS NOT CONNECTED...");
		JSObject ret = new JSObject();
		addProperty(ret, keyConnected, false);
		call.resolve(ret);
		return;
	}

	@PluginMethod()
	public void isEnabled(PluginCall call) {
		JSObject ret = new JSObject();
		if (bluetoothAdapter.isEnabled()) {
			ret.put(keyEnabled, true);
			call.resolve(ret);
		} else {
			ret.put(keyEnabled, false);
			call.resolve(ret);
		}
	}

	@PluginMethod()
	public void read(PluginCall call) {
		Log.d(getLogTag(),"PLUGIN READ...");
		String address = call.getString(keyAddress);
		if (address == null) {
			call.reject(keyErrorAddressMissing);
			return;
		}
		HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
		if (connection == null) {
			call.reject(keyErrorNotConnected);
			return;
		}
		BluetoothGatt gatt = (BluetoothGatt) connection.get(keyPeripheral);
		String propertyCharacteristic = call.getString(keyCharacteristic);
		if (propertyCharacteristic == null) {
			call.reject(keyErrorCharacteristicMissing);
			return;
		}
		String propertyService = call.getString(keyService);
		if (propertyService == null) {
			call.reject(keyErrorServiceMissing);
			return;
		}
		UUID service128BitUuid = get128BitUUID(propertyService);
		BluetoothGattService service = gatt.getService(service128BitUuid);
		if (service == null) {
			call.reject(keyErrorServiceNotFound);
			return;
		}
		UUID characteristic128BitUuid = get128BitUUID(propertyCharacteristic);
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristic128BitUuid);
		if (characteristic == null) {
			call.reject(keyErrorCharacteristicNotFound);
			return;
		}
		currentOperation = "read";
		connection.put(keyOperationRead, call);
		boolean success = gatt.readCharacteristic(characteristic);
		if (!success) {
			Log.d(getLogTag(),"COULD NOT EVEN READ CHARACTERISTIC");
			call.error(keyErrorValueRead);
			connection.remove(keyOperationRead);
		}
	}

	@PluginMethod()
	public void readDescriptor(PluginCall call) {
		String address = call.getString(keyAddress);
		if (address == null) {
			call.reject(keyErrorAddressMissing);
			return;
		}
		HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
		if (connection == null) {
			call.reject(keyErrorNotConnected);
			return;
		}
		BluetoothGatt gatt = (BluetoothGatt) connection.get(keyPeripheral);
		Integer propertyService = call.getInt(keyService);
		if (propertyService == null) {
			call.reject(keyErrorServiceMissing);
			return;
		}
		Integer propertyCharacteristic = call.getInt(keyCharacteristic);
		if (propertyCharacteristic == null) {
			call.reject(keyErrorCharacteristicMissing);
			return;
		}
		Integer propertyDescriptor = call.getInt(keyDescriptor);
		if (propertyDescriptor == null) {
			call.reject(keyErrorDescriptorMissing);
			return;
		}
		BluetoothGattService service = gatt.getService(get128BitUUID(propertyService));
		if (service == null) {
			call.reject(keyErrorServiceNotFound);
			return;
		}
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(get128BitUUID(propertyCharacteristic));
		if (characteristic == null) {
			call.reject(keyErrorCharacteristicNotFound);
			return;
		}
		BluetoothGattDescriptor descriptor = characteristic.getDescriptor(get128BitUUID(propertyDescriptor));
		if (descriptor == null) {
			call.reject(keyErrorDescriptorNotFound);
			return;
		}
		connection.put(keyOperationReadDescriptor, call);
		boolean success = gatt.readDescriptor(descriptor);
		if (!success) {
			connection.remove(keyOperationReadDescriptor);
			call.reject(keyErrorValueRead);
			return;
		}
	}

	@PluginMethod()
	public void refreshCache(PluginCall call) {
		String address = call.getString(keyAddress);
		if (address == null) {
			call.reject(keyErrorAddressMissing);
			return;
		}
		HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
		if (connection == null) {
			call.reject(keyErrorNotConnected);
			return;
		}
		BluetoothGatt gatt = (BluetoothGatt) connection.get(keyPeripheral);
		int callbackDelay = call.getInt("delay",5000);
		callbackDelay = 1000;
		//gattRefresh(gatt);
		Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				Log.d(getLogTag(),"refreshCache delayed run...");
				PluginCall call = getSavedCall();
				if(call == null){
					Log.d(getLogTag(),"refreshCache call null...");
					return;
				}
				Log.d(getLogTag(),"refreshCache call exists...");
				call.resolve();
			}
		},callbackDelay);
		saveCall(call);
	}

	@PluginMethod()
	public void scan(PluginCall call) {
		bleScanner = bluetoothAdapter.getBluetoothLeScanner();
		availableDevices = new HashMap<String, BluetoothDevice>();
		scanCallback = new BLEScanCallback();
		ScanSettings settings = new ScanSettings.Builder()
				.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
				.build();
		bleScanner.startScan(new ArrayList<>(), settings, scanCallback);
		Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				Log.d(getLogTag(),"Calling stop scan from scan()");
				BluetoothLEClient.this.stopScan(true);
			}
		},2000);
		saveCall(call);
	}

	@PluginMethod()
	private void setBytes(PluginCall call){
		String firstByte = call.getString(keyFirstByte);
		String lastByte = call.getString(keyLastByte);
		if(firstByte == null || lastByte == null){
			call.reject(keyErrorBytesMissing);
			return;
		}
		this.setByteStrings(firstByte, lastByte);
		call.resolve();
	}

	private void setByteStrings(String firstByte, String lastByte){
		this.firstByte = firstByte;
		this.lastByte = lastByte;
	}

	@PluginMethod()
	public void write(PluginCall call) {
		String address = call.getString(keyAddress);
		if (address == null) {
			call.reject(keyErrorAddressMissing);
			return;
		}
		Log.d(getLogTag(),"address: "+address);
		Log.d(getLogTag(),"connections: "+connections.toString());
		HashMap<String, Object> connection = (HashMap<String, Object>) connections.get(address);
		if (connection == null) {
			call.reject(keyErrorNotConnected);
			return;
		}
		Log.d(getLogTag(),"WRITE TO CONNECTION: "+connection.toString());
		BluetoothGatt gatt = (BluetoothGatt) connection.get(keyPeripheral);
		List<BluetoothGattService> services = gatt.getServices();
		for(int i=0;i<services.size();i++){
			BluetoothGattService service = services.get(i);
			Log.d(getLogTag(),"GATT SERVICE: "+service.getUuid().toString());
		}
		String propertyCharacteristic = call.getString(keyCharacteristic);
		if (propertyCharacteristic == null) {
			call.reject(keyErrorCharacteristicMissing);
			return;
		}
		Log.d(getLogTag(),"write() propertyCharacteristic: "+String.valueOf(propertyCharacteristic));
		String propertyService = call.getString(keyService);
		if (propertyService == null) {
			call.reject(keyErrorServiceMissing);
			return;
		}
		Log.d(getLogTag(),"write() propertyService: "+String.valueOf(propertyService));
		UUID service128BitUuid = get128BitUUID(propertyService);
		Log.d(getLogTag(),"write() service128BitUuid: "+service128BitUuid.toString());
		BluetoothGattService service = gatt.getService(service128BitUuid);
		if (service == null) {
			Log.d(getLogTag(),"WRITE: SERVICE NOT FOUND FOR UUID "+service128BitUuid.toString());
			call.reject(keyErrorServiceNotFound);
			return;
		}
		UUID characteristic128BitUuid = get128BitUUID(propertyCharacteristic);
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristic128BitUuid);
		if (characteristic == null) {
			call.reject(keyErrorCharacteristicNotFound);
			return;
		}
		String value = call.getString(keyValue);
		if (value == null) {
			call.reject(keyErrorValueMissing);
			return;
		}
		currentOperation = "write";
		//byte[] toWrite = toByteArray(value);
		byte[] toWrite = stringToByteArray(value);
		Log.d(getLogTag(),"toWrite: "+toWrite.toString());
		if (toWrite == null) {
			call.reject("Unsufficient value given");
			return;
		}
		characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
		boolean valueSet = characteristic.setValue(toWrite);
		if (!valueSet) {
			call.reject(keyErrorValueSet);
			return;
		}
		connection.put(keyOperationWrite, call);
		boolean success = gatt.writeCharacteristic(characteristic);
		if (!success) {
			call.error(keyErrorValueWrite);
			connection.remove(keyOperationWrite);
		}
	}

	private void stopScan(Boolean shouldReturn) {
		if(shouldReturn == null){
			shouldReturn = true;
		}
		Log.d(getLogTag(),"STOP SCAN... shouldReturn: "+String.valueOf(shouldReturn));
		if (bleScanner == null) {
			bleScanner = bluetoothAdapter.getBluetoothLeScanner();
		}

		if(shouldReturn){
			bleScanner.flushPendingScanResults(scanCallback);
		}
		bleScanner.stopScan(scanCallback);
		JSObject ret = new JSObject();
		PluginCall savedCall = getSavedCall();
		if(savedCall == null){
			Log.d(getLogTag(),"STOP SCAN... CALL IS NULL!");
			return;
		}
		if(currentOperation == "connect" && shouldReturn){
			Log.d(getLogTag(),"STOP SCAN RETURNING ERROR");
			ret.put(keyConnected, false);
			savedCall.reject(keyErrorConnectTimeout);
		}
		return;
	}

	private JSObject createBLEDeviceResult(BluetoothDevice device) {
		JSObject ret = new JSObject();
		addProperty(ret, keyDeviceName, device.getName());
		addProperty(ret, keyAddress, device.getAddress());
		addProperty(ret, keyBondState, device.getBondState());
		addProperty(ret, keyDeviceType, device.getType());
		return ret;
	}

	private JSObject createJSBluetoothGattService(BluetoothGattService service) {
		JSObject retService = new JSObject();
		addProperty(retService, keyUuid, get16BitUUID(service.getUuid()));
		if (service.getType() == BluetoothGattService.SERVICE_TYPE_PRIMARY) {
			addProperty(retService, keyIsPrimaryService, true);
		} else {
			addProperty(retService, keyIsPrimaryService, false);
		}
		ArrayList<Integer> included = new ArrayList<>();
		List<BluetoothGattService> subServices = service.getIncludedServices();
		for (BluetoothGattService incService : subServices) {
			included.add(get16BitUUID(incService.getUuid()));
		}
		retService.put(keyIncludedServices, JSArray.from(included.toArray()));
		ArrayList<Integer> retCharacteristics = new ArrayList<>();
		List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
		for (BluetoothGattCharacteristic characteristic : characteristics) {
			retCharacteristics.add(get16BitUUID(characteristic.getUuid()));
		}
		retService.put(keyCharacteristics, JSArray.from(retCharacteristics.toArray()));
		return retService;
	}

	private JSObject createJSBluetoothGattCharacteristic(BluetoothGattCharacteristic characteristic) {
		JSObject retCharacteristic = new JSObject();
		addProperty(retCharacteristic, keyUuid, get16BitUUID(characteristic.getUuid()));
		addProperty(retCharacteristic, keyCharacteristicProperies, getCharacteristicProperties(characteristic));
		List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
		ArrayList<Integer> descriptorUuids = new ArrayList<>();
		for (BluetoothGattDescriptor descriptor : descriptors) {
			descriptorUuids.add(get16BitUUID(descriptor.getUuid()));
		}
		addProperty(retCharacteristic, keyCharacterisicDescripors, JSArray.from(descriptorUuids.toArray()));
		return retCharacteristic;
	}

	private JSObject getCharacteristicProperties(BluetoothGattCharacteristic characteristic) {
		JSObject properties = new JSObject();
		if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0) {
			addProperty(properties, keyPropertyAuthenticatedSignedWrites, true);
		} else {
			addProperty(properties, keyPropertyAuthenticatedSignedWrites, false);
		}

		if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_BROADCAST) != 0) {
			addProperty(properties, keyPropertyBroadcast, true);
		} else {
			addProperty(properties, keyPropertyBroadcast, false);
		}

		if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
			addProperty(properties, keyPropertyIndicate, true);
		} else {
			addProperty(properties, keyPropertyIndicate, false);
		}

		if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
			addProperty(properties, keyPropertyNotify, true);
		} else {
			addProperty(properties, keyPropertyNotify, false);
		}

		if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
			addProperty(properties, keyPropertyRead, true);
		} else {
			addProperty(properties, keyPropertyRead, false);
		}

		if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
			addProperty(properties, keyPropertyWrite, true);
		} else {
			addProperty(properties, keyPropertyWrite, false);
		}
		if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
			addProperty(properties, keyPropertyWriteWithoutResponse, true);
		} else {
			addProperty(properties, keyPropertyWriteWithoutResponse, false);
		}
		return properties;
	}

	private JSArray getScanResult() {
		ArrayList<JSObject> scanResults = new ArrayList<>();
		for (Map.Entry<String, BluetoothDevice> entry : availableDevices.entrySet()) {
			BluetoothDevice device = entry.getValue();
			scanResults.add(createBLEDeviceResult(device));
		}
		return JSArray.from(scanResults.toArray());
	}

	@Override
	protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
		super.handleOnActivityResult(requestCode, resultCode, data);
		Log.d(getLogTag(), "handleOnActivityResult... requestCode: " + String.valueOf(requestCode));
		Log.d(getLogTag(), "handleOnActivityResult... resultCode: " + String.valueOf(resultCode));
		PluginCall call = getSavedCall();
		if (call == null) {
			Log.d(getLogTag(),"CALL IS NULL...");
			return;
		}
		if (requestCode == REQUEST_ENABLE_BT) {
			Log.d(getLogTag(),"CALL IS NOT NULL...");
			JSObject ret = new JSObject();
			addProperty(ret, keyEnabled, resultCode == 0 ? false : true);
			Log.d(getLogTag(),"SENDING: "+ret.toString());
			call.resolve(ret);
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(getLogTag(), "onActivityResult... requestCode: " + String.valueOf(requestCode));
		Log.d(getLogTag(), "onActivityResult... resultCode: " + String.valueOf(resultCode));
	}

	@Override
	protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.handleRequestPermissionsResult(requestCode, permissions, grantResults);
		Log.d(getLogTag(),"handleRequestPermissionsResult... requestCode: "+requestCode);
		PluginCall savedCall = getSavedCall();
		if(savedCall == null){
			return;
		}
		if(requestCode == REQUEST_LOCATION_PERMISSIONS){
			JSObject ret = new JSObject();
			boolean isAllowed = true;
			for(int result : grantResults) {
				if (result == PackageManager.PERMISSION_DENIED) {
					isAllowed = false;
					return;
				}
			}
			ret.put("isAllowed",isAllowed);
			savedCall.resolve(ret);
		}
	}

	private ArrayList<UUID> getServiceUuids(JSArray serviceUuidArray) {
		ArrayList<UUID> serviceUuids = new ArrayList<>();
		if (serviceUuidArray == null) {
			return serviceUuids;
		}
		List<Integer> uuidList;
		try {
			uuidList = serviceUuidArray.toList();
		} catch (JSONException e) {
			Log.e(getLogTag(), "Error while converting JSArray to List");
			return serviceUuids;
		}
		if (!(uuidList.size() > 0)) {
			Log.i(getLogTag(), "No uuids given");
			return serviceUuids;
		}
		for (Integer uuid : uuidList) {
			UUID uuid128 = get128BitUUID(uuid);
			if (uuid128 != null) {
				serviceUuids.add(uuid128);
			}
		}
		return serviceUuids;
	}

	private byte[] toByteArray(String base64Value) {
		if (base64Value == null) {
			return null;
		}
		byte[] bytes = Base64.decode(base64Value, Base64.NO_WRAP);
		if (bytes == null || bytes.length == 0) {
			return null;
		}
		return bytes;
	}

	private byte[] stringToByteArray(String base64Value) {
		if (base64Value == null) {
			return null;
		}
		byte[] bytes = Base64.decode(base64Value, Base64.NO_WRAP);
		if (bytes == null || bytes.length == 0) {
			return null;
		}
		return bytes;
	}

	private UUID get128BitUUID(Integer uuid) {
		if (uuid == null) {
			return null;
		}
		String hexString = Integer.toHexString(uuid);
		if (hexString.length() != 4) {
			Log.d(getLogTag(),"HEX BEFORE: "+hexString);
			hexString = String.format("%4s", hexString).replace(' ', '0');
			Log.d(getLogTag(),"HEX AFTER: "+hexString);
			//return null;
		}
		String uuidString = BASE_UUID_HEAD + hexString + BASE_UUID_TAIL;
		Log.d(getLogTag(),"get128BitUUID... uuidString: "+uuidString);
		return UUID.fromString(uuidString);
	}

	private UUID get128BitUUID(String uuid) {
		if (uuid == null) {
			return null;
		}
		String uuidString = BASE_UUID_HEAD + uuid + BASE_UUID_TAIL;
		Log.d(getLogTag(),"get128BitUUID(String)... uuidString: "+uuidString);
		return UUID.fromString(uuidString);
	}

	private int get16BitUUID(UUID uuid) {
		String uuidString = uuid.toString();
		int hexUuid = Integer.parseInt(uuidString.substring(4, 8), 16);
		return hexUuid;
	}

	private void addProperty(JSObject obj, String key, Object value) {
		if (value == null) {
			obj.put(key, JSObject.NULL);
			return;
		}
		obj.put(key, value);
	}

	private void gattRefresh(BluetoothGatt gatt){
		try {
			Log.d(getLogTag(),"gattRefresh 01");
			final Method refresh = gatt.getClass().getMethod("refresh");
			Log.d(getLogTag(),"gattRefresh 02");
			if (refresh != null) {
				Log.d(getLogTag(),"gattRefresh 03");
				refresh.invoke(gatt);
				Log.d(getLogTag(),"gattRefresh 04");
			}
		} catch(Exception ex) {
			Log.e(getLogTag(),"gattRefresh Exception: ");
			ex.printStackTrace();
		}
	}

	private void unpairDevice(BluetoothDevice device) {
		try {
			Method m = device.getClass().getMethod("removeBond", (Class[]) null);
			m.invoke(device, (Object[]) null);
		} catch (Exception e) { Log.e(getLogTag(), e.getMessage()); }
	}

	private Boolean setNotificationEnabled(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic){
		UUID clientCharacteristicConfDescriptorUuid = get128BitUUID(clientCharacteristicConfigurationUuid);
		BluetoothGattDescriptor notificationDescriptor = characteristic.getDescriptor(clientCharacteristicConfDescriptorUuid);
		if (notificationDescriptor == null) {
			return false;
		}
		boolean notificationSet = gatt.setCharacteristicNotification(characteristic, true);
		if (!notificationSet) {
			return false;
		}
		boolean result = false;
		Log.d(getLogTag(),"enableNotifications 04");
		if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == BluetoothGattCharacteristic.PROPERTY_NOTIFY) {
			Log.d(getLogTag(),"ENABLE NOTIFICATION NO DESCRIPTOR");
			result = notificationDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
		} else {
			Log.d(getLogTag(),"ENABLE INDICATION NO DESCRIPTOR");
			result = notificationDescriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
		}
		return result;
	}
}