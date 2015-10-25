package com.mobility42.azurechatr;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.os.AsyncTask;
import android.widget.Toast;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.microsoft.windowsazure.messaging.*;
import com.microsoft.windowsazure.notifications.NotificationsManager;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.MobileServiceTable;
import com.microsoft.windowsazure.mobileservices.NextServiceFilterCallback;
import com.microsoft.windowsazure.mobileservices.ServiceFilter;
import com.microsoft.windowsazure.mobileservices.ServiceFilterRequest;
import com.microsoft.windowsazure.mobileservices.ServiceFilterResponse;
import com.microsoft.windowsazure.mobileservices.ServiceFilterResponseCallback;
import com.microsoft.windowsazure.mobileservices.TableOperationCallback;
import com.microsoft.windowsazure.mobileservices.TableQueryCallback;
import com.mobility42.azurechatr.util.RequestPackage;
import com.mobility42.azurechatr.util.ServerSocket;
import com.mobility42.azurechatr.util.ServerSocketThread;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;

public class ChatActivity extends Activity {
	
	public static final String DISPLAY_MESSAGE_ACTION = "displaymessage";
	public static final String EXTRA_USERNAME = "Jay Sarbad";
	public static final String EXTRA_MESSAGE = "message";

	boolean testRequest = true;

	// Secret IDs, Azure App Keys and Connection Strings NOT to be shared with the public
	private String AZUREMOBILESERVICES_URI = "https://bluchain.azure-mobile.net/ ";
	private String AZUREMOBILESERVICES_APPKEY = "ctYVxpxKJiUndMzYtqgKEkaTnNRUTf32";
	private String AZUREPUSHNOTIFHUB_NAME = "https://bluchainhub-ns.servicebus.windows.net/bluchainhub";
	private String AZUREPUSHNOTIFHUB_CNXSTRING = "Endpoint=sb://bluchainhub-ns.servicebus.windows.net/;SharedAccessKeyName=DefaultListenSharedAccessSignature;SharedAccessKey=x8/8f2tQyO/yEhc6CGipdQpipANSlHMXWYdfnKuCKb4=";
	private String GCMPUSH_SENDER_ID = "bluchain-1108";
	public static final String CLOSE_APP = "closeApp";

	private GoogleCloudMessaging gcm;
	private NotificationHub hub;

	private static Long REQUESTID = 0l;

	public static final String REQUEST_PACKAGE = "request_package";
	BluetoothAdapter bluetoothAdapter;
	public static Set<BluetoothDevice> pairedDevices;
	IntentFilter filter;
	BroadcastReceiver receiver;
	ServerSocketThread serverSocketThread = new ServerSocketThread(this);
	static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	Map<String, Set<Long>> processedRequests = new HashMap<>();

	/**
	 * Mobile Service Client reference
	 */
	private MobileServiceClient mClient;

	/**
	 * Mobile Service Table used to access data
	 */
	private MobileServiceTable<ChatItem> mChatTable;

	/**
	 * Adapter to sync the items list with the view
	 */
	private ChatItemAdapter mAdapter;

	private ListView mlistViewChat;

	/**
	 * EditText containing the "New Chat" text
	 */
	private EditText mTextNewChat;

	/**
	 * Progress spinner to use for table operations
	 */
	private ProgressBar mProgressBar;

	/**
	 * Initializes the activity
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_chat);
		
		mProgressBar = (ProgressBar) findViewById(R.id.loadingProgressBar);

		// Initialize the progress bar
		mProgressBar.setVisibility(ProgressBar.GONE);
		
		try {
			// Create the Mobile Service Client instance, using the provided
			// Mobile Service URL and key
			mClient = new MobileServiceClient(
					AZUREMOBILESERVICES_URI,
					AZUREMOBILESERVICES_APPKEY,
					this).withFilter(new ProgressFilter());

			// Get the Mobile Service Table instance to use
			mChatTable = mClient.getTable(ChatItem.class);

			mTextNewChat = (EditText) findViewById(R.id.textNewChat);

			// Create an adapter to bind the items with the view
			mAdapter = new ChatItemAdapter(this, R.layout.row_list_chat);
			mlistViewChat = (ListView) findViewById(R.id.listViewChat);
			mlistViewChat.setAdapter(mAdapter);
		
			// Load the items from the Mobile Service
			refreshItemsFromTable();
			
			NotificationsManager.handleNotifications(this, GCMPUSH_SENDER_ID, MyHandler.class);

			gcm = GoogleCloudMessaging.getInstance(this);

			String connectionString = AZUREPUSHNOTIFHUB_CNXSTRING;
			hub = new NotificationHub(AZUREPUSHNOTIFHUB_NAME, connectionString, this);

			registerWithNotificationHubs();

		} catch (MalformedURLException e) {
			createAndShowDialog(new Exception("There was an error creating the Mobile Service. Verify the URL"), "Error");
		}

		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finishAffinity();
			}
		};
		registerReceiver(receiver, new IntentFilter(CLOSE_APP));

//		Intent intent = new Intent(this, BlueToothService.class);
//		startService(intent);

		startBluetooth();

	}

	private void startBluetooth() {
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		if(bluetoothAdapter==null){
			Toast.makeText(getApplicationContext(), "No bluetooth detected", 0).show();
			((Activity) getApplicationContext()).finish();
		}
		else{
			registerBroadcastReceivers();

			if(!bluetoothAdapter.isEnabled()){
				turnOnBlueTooth();
			} else {
				updatePairedDevices();
			}
		}
	}

	private void updatePairedDevices() {
		if(serverSocketThread != null)
			serverSocketThread.closeConnect();

		pairedDevices = bluetoothAdapter.getBondedDevices();
		bluetoothAdapter.startDiscovery();

		serverSocketThread.acceptConnect(bluetoothAdapter, uuid);
		serverSocketThread.start();
	}

	private void registerBroadcastReceivers() {

		receiver = new BroadcastReceiver(){
			@Override
			public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (BluetoothDevice.ACTION_FOUND.equals(action)){
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

				pairDevice(device);
			}
			else if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
				final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
						BluetoothAdapter.ERROR);
				switch (state) {
					case BluetoothAdapter.STATE_TURNING_OFF:
						if(serverSocketThread != null)
							serverSocketThread.closeConnect();
						sendBroadcast(new Intent(ChatActivity.CLOSE_APP));
						break;
					case BluetoothAdapter.STATE_ON:
						updatePairedDevices();
				}
			}
			}
		};

		filter = new IntentFilter();
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		registerReceiver(receiver, filter);
	}

	private void turnOnBlueTooth() {
		Intent dialogIntent = new Intent(this, EnableBlueToothActivity.class);
		dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(dialogIntent);
	}

	private void startDiscovery() {
		bluetoothAdapter.cancelDiscovery();
		bluetoothAdapter.startDiscovery();
	}

	//For Pairing
	private void pairDevice(BluetoothDevice device) {
		try {
			Log.d("pairDevice()", "Start Pairing...");
			device.getClass().getMethod("setPairingConfirmation", boolean.class).invoke(device, true);
			device.getClass().getMethod("cancelPairingUserInput").invoke(device);
			device.getClass().getMethod("createBond", (Class[]) null).invoke(device, (Object[]) null);
			pairedDevices.add(device);
			Log.d("pairDevice()", "Pairing finished.");
		} catch (Exception e) {
			Log.e("pairDevice()", e.getMessage());
		}
	}


	//For UnPairing
	private void unpairDevice(BluetoothDevice device) {
		try {
			Log.d("unpairDevice()", "Start Un-Pairing...");
			Method m = device.getClass().getMethod("removeBond", (Class[]) null);
			m.invoke(device, (Object[]) null);
			pairedDevices.remove(device);
			Log.d("unpairDevice()", "Un-Pairing finished.");
		} catch (Exception e) {
			Log.e("unpairDevice()", e.getMessage());
		}
	}

	private void dataRequest(final RequestPackage requestPackage) {
		requestPackage.relayPath.add(ChatActivity.EXTRA_USERNAME);

		String url = "https://bluchain.azure-mobile.net/api/estar";
		AsyncHttpClient client = new AsyncHttpClient();
		try{
			client.post(getApplicationContext(), url, new StringEntity(requestPackage.toString()), "application/json", new JsonHttpResponseHandler() {
				@Override
				public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
					try {
						RequestPackage responsePackage;
						JSONObject obj = response.getJSONObject(0);
						responsePackage = (RequestPackage) obj.get("response");
						relayRequest(responsePackage);
					} catch (Exception e) {

					}
				}
			});
		} catch (Exception e) {

		}
	}

	private void relayRequest(RequestPackage responsePackage) {
		if(responsePackage.destinationId.equals(ChatActivity.EXTRA_USERNAME)) {
			return;
		}

		if(processedRequests.containsKey(responsePackage.senderId) && processedRequests.get(responsePackage.senderId).contains(responsePackage.requestId)) {
			return;
		}

		for(BluetoothDevice pairedDevice: pairedDevices) {
			BluetoothSocket socket = connect(pairedDevice, uuid);
			if(socket != null) {
				try {
					ServerSocket.sendData(socket, responsePackage.toString());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private boolean isNetworkAvailable() {
		ConnectivityManager connectivityManager
				= (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}

	public void handleRequestPackage(RequestPackage requestPackage) {
		if(isNetworkAvailable()) {
			dataRequest(requestPackage);
		} else {
			relayRequest(requestPackage);
		}
	}

	public BluetoothSocket connect(BluetoothDevice bTDevice, UUID mUUID) {
		BluetoothSocket bTSocket = null;
		try {
			bTSocket = bTDevice.createRfcommSocketToServiceRecord(mUUID);
		} catch (IOException e) {
			Log.d("CONNECTTHREAD","Could not create RFCOMM socket:" + e.toString());
			return null;
		}
		try {
			bTSocket.connect();
		} catch(IOException e) {
			Log.d("CONNECTTHREAD","Could not connect: " + e.toString());
			try {
				bTSocket.close();
			} catch(IOException close) {
				Log.d("CONNECTTHREAD", "Could not close connection:" + e.toString());
				return null;
			}
		}
		return bTSocket;
	}

	@SuppressWarnings("unchecked")
	private void registerWithNotificationHubs() {
	   new AsyncTask() {
	      @Override
	      protected Object doInBackground(Object... params) {
	         try {
	            String regid = gcm.register(GCMPUSH_SENDER_ID);
	            hub.register(regid);
	         } catch (Exception e) {
	            return e;
	         }
	         return null;
	     }
	   }.execute(null, null, null);
	}
	
	/**
	 * Initializes the activity menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	/**
	 * Select an option from the menu
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.menu_refresh) {
			refreshItemsFromTable();
		}
		
		return true;
	}

	/**
	 * Add a new item
	 * 
	 * @param view
	 *            The view that originated the call
	 */
	public void addItem(View view) {
		if (mClient == null) {
			return;
		}

		// Create a new item
		ChatItem item = new ChatItem();

		item.setText(mTextNewChat.getText().toString());
		// This is temporary until we add authentication to the Android version
		item.setUserName(EXTRA_USERNAME);
		
		Date currentDate = new Date(System.currentTimeMillis());
		item.setTimeStamp(currentDate);

		if(testRequest) {
			RequestPackage requestPackage = new RequestPackage();
			requestPackage.senderId = EXTRA_USERNAME;
			requestPackage.requestId = REQUESTID++;
			requestPackage.destinationId = "SERVER";
			requestPackage.request = "THE_REQUEST";
			requestPackage.relayPath.add(EXTRA_USERNAME);

			handleRequestPackage(requestPackage);
		} else {
			// Insert the new item
			mChatTable.insert(item, new TableOperationCallback<ChatItem>() {

				public void onCompleted(ChatItem entity, Exception exception, ServiceFilterResponse response) {

					if (exception == null) {
						mAdapter.add(entity);
						mlistViewChat.setSelection(mlistViewChat.getCount() - 1);
					} else {
						createAndShowDialog(exception, "Error");
					}

				}
			});
		}

		mTextNewChat.setText("");
	}

	/**
	 * Refresh the list with the items in the Mobile Service Table
	 */
	private void refreshItemsFromTable() {

		// Get all the chat items and add them in the adapter
		mChatTable.execute(new TableQueryCallback<ChatItem>() {

			public void onCompleted(List<ChatItem> result, int count, Exception exception, ServiceFilterResponse response) {
				if (exception == null) {
					mAdapter.clear();

					for (ChatItem item : result) {
						mAdapter.add(item);
					}

					mlistViewChat.setSelection(mlistViewChat.getCount() - 1);

				} else {
					createAndShowDialog(exception, "Error");
				}
			}
		});
	}

	/**
	 * Creates a dialog and shows it
	 * 
	 * @param exception
	 *            The exception to show in the dialog
	 * @param title
	 *            The dialog title
	 */
	private void createAndShowDialog(Exception exception, String title) {
		Throwable ex = exception;
		if(exception.getCause() != null){
			ex = exception.getCause();
		}
		createAndShowDialog(ex.getMessage(), title);
	}

	/**
	 * Creates a dialog and shows it
	 * 
	 * @param message
	 *            The dialog message
	 * @param title
	 *            The dialog title
	 */
	private void createAndShowDialog(String message, String title) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setMessage(message);
		builder.setTitle(title);
		builder.create().show();
	}
	
	private class ProgressFilter implements ServiceFilter {
		
		@Override
		public void handleRequest(ServiceFilterRequest request, NextServiceFilterCallback nextServiceFilterCallback,
				final ServiceFilterResponseCallback responseCallback) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					if (mProgressBar != null) mProgressBar.setVisibility(ProgressBar.VISIBLE);
				}
			});
			
			nextServiceFilterCallback.onNext(request, new ServiceFilterResponseCallback() {
				
				@Override
				public void onResponse(ServiceFilterResponse response, Exception exception) {
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							if (mProgressBar != null) mProgressBar.setVisibility(ProgressBar.GONE);
						}
					});
					
					if (responseCallback != null)  responseCallback.onResponse(response, exception);
				}
			});
		}
	}
	
	private final BroadcastReceiver mHandleMessageReceiver =
	        new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        String newMessage = intent.getExtras().getString(EXTRA_MESSAGE);
	        String newUsername = intent.getExtras().getString(EXTRA_USERNAME);
	        
	        ChatItem item = new ChatItem();

			item.setText(newMessage);
			item.setUserName(newUsername);
			
			mAdapter.add(item);
	    }

	};
	
	@Override
	public void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(DISPLAY_MESSAGE_ACTION);
		this.registerReceiver(mHandleMessageReceiver, filter);
		this.registerReceiver(receiver, new IntentFilter(CLOSE_APP));
	}
	
	@Override
	public void onPause() {
		super.onPause();
		this.unregisterReceiver(mHandleMessageReceiver);
		this.unregisterReceiver(receiver);
	}
}
