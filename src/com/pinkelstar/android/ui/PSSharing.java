/*
 *  Copyright (c) <2010> PinkelStar by MillMobile BV

 Permission is hereby granted, free of charge, to any person obtaining a copy

 of this software and associated documentation files (the "Software"), to deal

 in the Software without restriction, including without limitation the rights

 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell

 copies of the Software, and to permit persons to whom the Software is

 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in

 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR

 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,

 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE

 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER

 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,

 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN

 THE SOFTWARE.

Except as contained in this notice, the name(s) and/or trademarks of the above copyright holders shall not be used in advertising or otherwise to promote the sale, use or other dealings in this Software without prior written authorization.

 
 */
package com.pinkelstar.android.ui;

import java.util.ArrayList;
import java.util.TimerTask;

import pinkelstar.android.R;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.pinkelstar.android.server.Constants;
import com.pinkelstar.android.server.Server;
import com.pinkelstar.android.server.Utils;
import com.pinkelstar.android.ui.tasks.PostTask;
import com.pinkelstar.android.ui.util.ImageCache;
import com.pinkelstar.android.ui.util.ImageCallback;

public class PSSharing extends Activity {

	private EditText userMessage;
	private TextView developerMessage;
	private Button publishButton;
	private String contentUrl;
	private ToggleButton[] networkButtons;
	private Handler mHandler;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.pssharing);
		setupNetworkCheckboxes(savedInstanceState);

		developerMessage = (TextView) findViewById(R.id.iconmsg);
		Intent intent = getIntent();
		if (intent != null) {
			if (intent.getStringExtra("developerMessage") != null) {
				developerMessage.setText(intent.getStringExtra("developerMessage"));
			}
			if (intent.getStringExtra("contentUrl") != null) {
				contentUrl = intent.getStringExtra("contentUrl");
			}
		}

		publishButton = (Button) findViewById(R.id.publishbutton);
		publishButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				publish();
			}
		});
		userMessage = (EditText) findViewById(R.id.message);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		if (Server.getInstance().getState() == Server.STATE_INITIALIZED) {
			boolean[] states = new boolean[networkButtons.length];
			for (int i = 0; i < states.length; i++) {
				states[i] = networkButtons[i].isChecked();
			}
			outState.putBooleanArray("networkButtonStates", states);
			super.onSaveInstanceState(outState);
		}
	}

	private void setupNetworkCheckboxes(Bundle savedInstanceState) {
		if (Server.getInstance().getState() != Server.STATE_INITIALIZED) {
			mHandler = new Handler();
			MyTimerTask mtt = new MyTimerTask();
			mHandler.removeCallbacks(mtt);
			mHandler.postDelayed(mtt, 100);
		} else {
			checkboxes();
			preloadSmallNetworkImages();
		}

		if (savedInstanceState != null) {
			boolean[] states = savedInstanceState.getBooleanArray("networkButtonStates");
			if (states != null) {
				for (int i = 0; i < states.length; i++) {
					networkButtons[i].setChecked(states[i]);
				}
			}
		}
	}

	/**
	 * gets called when a login activity returns.
	 * 
	 * @see android.app.Activity#onActivityResult(int, int,
	 *      android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		networkButtons[requestCode].setChecked(resultCode == RESULT_OK);
		// as soon as registering of any network fails, stop the process
		if (resultCode != RESULT_OK)
			return;

		// publish is called again if another networks need to be authenticated
		publish();
	}

	private void checkboxes() {
		ImageCache.getInstance().loadDrawable(Server.getInstance().getIconUrl(), new ImageCallback() {
			public void setDrawable(Drawable d) {
				ImageView iv = (ImageView) findViewById(R.id.iconimg);
				iv.setImageDrawable(d);
			}
		});

		if (Server.getInstance().getState() != Server.STATE_INITIALIZED) {
			return;
		}

		networkButtons = new ToggleButton[Server.getInstance().getKnownNetworks().length];
		LinearLayout ll = (LinearLayout) findViewById(R.id.LinearLayout01);
		ll.removeAllViews();
		for (int i = 0; i < networkButtons.length; i++) {
			networkButtons[i] = createButton(i);
			LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			llp.setMargins(10, 10, 10, 10);
			ll.addView(networkButtons[i], llp);
		}
	}

	private ToggleButton createButton(int i) {
		final ToggleButton tb = new ToggleButton(PSSharing.this);
		String networkName = Server.getInstance().getKnownNetworks()[i];
		String imageUrl = Utils.buildImageUrl(networkName, Constants.LARGE_IMAGES);
		tb.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.placeholder_button_icon, 0, 0);

		ImageCache.getInstance().loadDrawable(imageUrl, new ImageCallback() {
			public void setDrawable(Drawable d) {
				tb.setCompoundDrawablesWithIntrinsicBounds(null, d, null, null);
			}
		});

		tb.setBackgroundResource(R.drawable.buttonstates);
		tb.setTextOn(networkName);
		tb.setTextOff(networkName);
		tb.setTextColor(Color.WHITE);
		tb.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
		tb.setChecked(Server.getInstance().isNetworkAuthenticated(networkName));

		return tb;
	}

	/**
	 * checks if selected networks are authenticated and starts authentication
	 * activites if necessary. this method could be called several times if
	 * multiple networks need to be authenticated
	 */
	private void publish() {
		if (Server.getInstance().getState() != Server.STATE_INITIALIZED) {
			Toast.makeText(PSSharing.this, R.string.waitforsettings, Toast.LENGTH_SHORT).show();
			return;
		}

		String[] selectedNetworks = selectedNetworks();

		if (selectedNetworks.length == 0) {
			Toast.makeText(PSSharing.this, R.string.selectnetwork, Toast.LENGTH_SHORT).show();
			return;
		}

		boolean allAuthenticated = authenticateNetworks(selectedNetworks);
		if (!allAuthenticated)
			return;

		String networks = TextUtils.join(",", selectedNetworks);
		new PostTask(this).execute(networks, userMessage.getText().toString(), developerMessage.getText().toString(), contentUrl);
	}

	private String[] selectedNetworks() {
		ArrayList<String> selectedNetworks = new ArrayList<String>();
		for (int i = 0; i < Server.getInstance().getKnownNetworks().length; i++) {
			if (networkButtons[i].isChecked()) {
				selectedNetworks.add(Server.getInstance().getKnownNetworks()[i]);
			}
		}
		return selectedNetworks.toArray(new String[selectedNetworks.size()]);
	}

	private boolean authenticateNetworks(String[] networks) {
		for (String networkName : networks) {
			if (!Server.getInstance().isNetworkAuthenticated(networkName)) {
				Server.getInstance().startAuthentication(this, networkName);
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, 0, 0, R.string.settings).setIcon(R.drawable.settings);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (Server.getInstance().getState() == Server.STATE_INITIALIZED) {
			startActivity(new Intent(PSSharing.this, PSSettings.class));
		} else {
			Toast.makeText(PSSharing.this, R.string.waitforsettings, Toast.LENGTH_SHORT).show();
		}
		return super.onOptionsItemSelected(item);
	}

	private void preloadSmallNetworkImages() {
		for (String networkName : Server.getInstance().getKnownNetworks()) {
			String networkUrl = Utils.buildImageUrl(networkName, Constants.SMALL_IMAGES);
			ImageCache.getInstance().preloadDrawable(networkUrl);
		}
	}
	
	private void showConnectionErrorMessage() {
		LinearLayout ll = (LinearLayout) findViewById(R.id.LinearLayout02);
		ll.removeAllViews();
		TextView tv = new TextView(PSSharing.this);
		tv.setText(R.string.nosettings);
		tv.setTextColor(Color.WHITE);
		ll.addView(tv);
		Log.d("PinkelStar", "Sharing could not be started as the Server is in an error state.");	
	}

	public class MyTimerTask extends TimerTask {
		@Override
		public void run() {
			switch (Server.getInstance().getState()) {
			case Server.STATE_INITIALIZED:
				mHandler.removeCallbacks(this);
				setupNetworkCheckboxes(null);
				break;
			case Server.STATE_LOADING:
				mHandler.postDelayed(this, 1000);
				break;
			case Server.STATE_ERROR:
				showConnectionErrorMessage();
				break;
			}
		}
	}
}