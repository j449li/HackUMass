package com.mobility42.azurechatr;

import java.net.MalformedURLException;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.os.AsyncTask;

import com.google.android.gms.gcm.GoogleCloudMessaging;
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
import com.mobility42.azurechatr.services.BlueToothService;

import java.util.*;

public class ChatActivity extends Activity {
	
	public static final String DISPLAY_MESSAGE_ACTION = "displaymessage";
	public static final String EXTRA_USERNAME = "Jay Sarbad";
	public static final String EXTRA_MESSAGE = "message";

	// Secret IDs, Azure App Keys and Connection Strings NOT to be shared with the public
	private String AZUREMOBILESERVICES_URI = "https://bluchain.azure-mobile.net/ ";
	private String AZUREMOBILESERVICES_APPKEY = "ctYVxpxKJiUndMzYtqgKEkaTnNRUTf32";
	private String AZUREPUSHNOTIFHUB_NAME = "https://bluchainhub-ns.servicebus.windows.net/bluchainhub";
	private String AZUREPUSHNOTIFHUB_CNXSTRING = "Endpoint=sb://bluchainhub-ns.servicebus.windows.net/;SharedAccessKeyName=DefaultListenSharedAccessSignature;SharedAccessKey=x8/8f2tQyO/yEhc6CGipdQpipANSlHMXWYdfnKuCKb4=";
	private String GCMPUSH_SENDER_ID = "bluchain-1108";
	public static final String CLOSE_APP = "closeApp";

	private GoogleCloudMessaging gcm;
	private NotificationHub hub;
	private BroadcastReceiver receiver;

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

		Intent intent = new Intent(this, BlueToothService.class);
		startService(intent);

		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finishAffinity();
			}
		};
		registerReceiver(receiver, new IntentFilter(CLOSE_APP));
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
