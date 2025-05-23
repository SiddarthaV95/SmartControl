package com.docvolt.usbcontrol;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import android.os.Handler;

import java.io.IOException;

import static com.docvolt.usbcontrol.UsbIOService.*;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ArduinoListener {
    private TextView tvSensor1, tvSensor2;
    private final String TAG = "MainActivity";
    UsbIOService mUsbIOService = new UsbIOService();

    // Track the desired state (what the user wants)
    private boolean desiredRelayState = false;
    private boolean lastKnownApplianceState = false;
    private boolean isControllingRelay = false;
    private static final long RELAY_DEBOUNCE_TIME = 2000;
    // Track if we're in inverted mode
    private boolean invertedMode = false;
    private boolean applianceState = false;
    private boolean relayState = false;
    private boolean isManualOverride = false;
    private long lastControlTime = 0;
    private static final long CONTROL_TIMEOUT = 3000; // 3 seconds
    private boolean isSyncingWithBlynk = false;
    private long lastBlynkUpdateTime = 0;
    private static final long BLYNK_UPDATE_COOLDOWN = 2000; // 2 seconds
    private boolean blynkButtonState = false;
    private long lastBlynkSyncTime = 0;
    private static final long BLYNK_SYNC_INTERVAL = 5000; // 5 seconds

    private final Handler blynkSyncHandler = new Handler();
    private final Runnable blynkSyncRunnable = new Runnable() {
        @Override
        public void run() {
            // Only sync if enough time has passed since last sync
            if (System.currentTimeMillis() - lastBlynkSyncTime >= BLYNK_SYNC_INTERVAL) {
                syncWithBlynk();
                lastBlynkSyncTime = System.currentTimeMillis();
            }
            blynkSyncHandler.postDelayed(this, BLYNK_SYNC_INTERVAL);
        }
    };
    private void syncWithBlynk() {
        // Update Blynk with actual appliance state
        updateBlynkVirtualPin("V0", applianceState ? 1 : 0);

        // Get latest state from Blynk
        readBlynkVirtualPin("V0", (ToggleButton) findViewById(R.id.btn_txd));
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvSensor1 = findViewById(R.id.tv_sensor1);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        findViewById(R.id.btn_txd).setOnClickListener(this);

        bindService(new Intent(this, UsbIOService.class), connection, Context.BIND_AUTO_CREATE);

        IntentFilter usbIntentFilter = new IntentFilter();
        usbIntentFilter.addAction(UsbIOService.ACTION_USB_PERMISSION_GRANTED);
        usbIntentFilter.addAction(UsbIOService.ACTION_NO_VALID_USB);
        usbIntentFilter.addAction(UsbIOService.ACTION_USB_DISCONNECTED);
        usbIntentFilter.addAction(UsbIOService.ACTION_USB_NOT_SUPPORTED);
        usbIntentFilter.addAction(UsbIOService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, usbIntentFilter);

        ToggleButton btnRi = findViewById(R.id.btn_ri);
        btnRi.setChecked(true);
        btnRi.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                buttonView.setChecked(true);
                Toast.makeText(this, "RI must stay ON for outputs to work", Toast.LENGTH_SHORT).show();
            }
        });

        blynkSyncHandler.post(blynkSyncRunnable);
    }

    private void updateBlynkVirtualPin(String pin, int value) {
        OkHttpClient client = new OkHttpClient();
        String token = "5KR8iZa0Q5i-lFnmh40qFWd5Q-dRG_TR";
        String url = "https://blynk.cloud/external/api/update?token=" + token + "&" + pin + "=" + value;

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("BLYNK", "Failed to update: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("BLYNK", "Update failed: " + response.code());
                }
            }
        });
    }

    private void readBlynkVirtualPin(String pin, ToggleButton toggleButton) {
        OkHttpClient client = new OkHttpClient();
        String token = "5KR8iZa0Q5i-lFnmh40qFWd5Q-dRG_TR";
        String url = "https://blynk.cloud/external/api/get?token=" + token + "&" + pin;

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("BLYNK", "Failed to fetch: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String value = response.body().string().trim();
                    blynkButtonState = value.equals("1");

                    runOnUiThread(() -> {
                        if (!isManualOverride && blynkButtonState != desiredRelayState) {
                            desiredRelayState = blynkButtonState;
                            toggleButton.setChecked(desiredRelayState);
                            updateRelayState();
                        }
                    });
                }
            }
        });
    }

    private void checkSensors() {
        boolean newApplianceState = (digitalRead(PIN_DSR) == 0);
        if (newApplianceState != applianceState) {
            applianceState = newApplianceState;
            updateUI();

            // Update Blynk only if state changed and not in manual override
            if (!isManualOverride) {
                updateBlynkVirtualPin("V0", applianceState ? 1 : 0);
            }
        }
    }

    @Override
    public void onPinChange() {
        checkSensors();
    }

    @Override
    public void onClick(View view) {
        digitalWrite(PIN_RI, 1);

        if (view.getId() == R.id.btn_txd) {
            isManualOverride = true;
            desiredRelayState = ((CompoundButton) view).isChecked();
            updateRelayState();

            // Clear manual override after timeout
            blynkSyncHandler.postDelayed(() -> isManualOverride = false, CONTROL_TIMEOUT);
        }
    }

    private void updateRelayState() {
        // Don't allow rapid successive control attempts
        if (System.currentTimeMillis() - lastControlTime < 500) {
            return;
        }
        lastControlTime = System.currentTimeMillis();

        // Only act if desired state doesn't match actual state
        if (desiredRelayState != applianceState) {
            // Toggle relay state
            relayState = !relayState;
            digitalWrite(PIN_TXD, relayState ? 1 : 0);

            // Verify state after delay
            blynkSyncHandler.postDelayed(this::verifyState, 1000);
        }
    }
    private void verifyState() {
        boolean newApplianceState = (digitalRead(PIN_DSR) == 0);
        if (newApplianceState != applianceState) {
            applianceState = newApplianceState;
            updateUI();

            // If still not in desired state, try again
            if (desiredRelayState != applianceState) {
                updateRelayState();
            }
        }
    }
    private void updateUI() {
        runOnUiThread(() -> {
            // Update sensor display
            tvSensor1.setText(applianceState ? "ON" : "OFF");
            tvSensor1.setTextColor(applianceState ? Color.GREEN : Color.RED);

            // Update toggle button to reflect desired state
            ToggleButton toggle = findViewById(R.id.btn_txd);
            if (toggle.isChecked() != desiredRelayState) {
                toggle.setChecked(desiredRelayState);
            }
        });
    }



    /* Rest of the code remains the same */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        private AlertDialog no_usb_dialog = null;

        @Override
        public void onReceive(Context context, Intent intent) {
            AlertDialog.Builder no_usb_dialog_builder = new AlertDialog
                    .Builder(MainActivity.this)
                    .setPositiveButton(R.string.retry, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            mUsbIOService.initIODevice();
                        }
                    })
                    .setMessage(R.string.NO_USB);

            switch (Objects.requireNonNull(intent.getAction())) {
                case UsbIOService.ACTION_USB_PERMISSION_GRANTED:
                    if (no_usb_dialog != null && no_usb_dialog.isShowing())
                        no_usb_dialog.dismiss();
                    break;
                case UsbIOService.ACTION_USB_PERMISSION_NOT_GRANTED:
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    break;
                case UsbIOService.ACTION_NO_VALID_USB:
                    if (no_usb_dialog == null || !no_usb_dialog.isShowing()) {
                        no_usb_dialog = no_usb_dialog_builder.create();
                        no_usb_dialog.show();
                    }
                    break;
                case UsbIOService.ACTION_USB_DISCONNECTED:
                    if (no_usb_dialog == null || !no_usb_dialog.isShowing()) {
                        no_usb_dialog = no_usb_dialog_builder.create();
                        no_usb_dialog.show();
                    }
                    break;
                case UsbIOService.ACTION_USB_NOT_SUPPORTED:
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            UsbIOService.UsbBinder binder = (UsbIOService.UsbBinder) service;
            mUsbIOService = binder.getService();
            mUsbIOService.setArduinoFunctionsCB(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "ServiceDisconnected");
            mUsbIOService = null;
        }
    };
}