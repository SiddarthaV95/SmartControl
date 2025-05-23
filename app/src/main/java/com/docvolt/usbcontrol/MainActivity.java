package com.docvolt.usbcontrol;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import android.os.Handler;

import java.io.IOException;
import java.util.Objects;

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

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ArduinoListener {
    private TextView tvSensor1;
    private final String TAG = "MainActivity";
    UsbIOService mUsbIOService = new UsbIOService();

    private final Handler blynkSyncHandler = new Handler();
    private final Runnable blynkSyncRunnable = new Runnable() {
        @Override
        public void run() {
            // Poll Blynk virtual pin
            readBlynkVirtualPin("V0", (ToggleButton) findViewById(R.id.btn_txd));

            // Re-run every 4 seconds
            blynkSyncHandler.postDelayed(this, 4000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize TextView
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
        btnRi.setChecked(true);  // Force RI to be checked
        btnRi.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Prevent user from unchecking
            if (!isChecked) {
                buttonView.setChecked(true);
                Toast.makeText(this, "RI must stay ON for outputs to work", Toast.LENGTH_SHORT).show();
            }
        });

        // Start periodic Blynk polling
        blynkSyncHandler.post(blynkSyncRunnable);
    }

    private void updateBlynkVirtualPin(String pin, int value) {
        OkHttpClient client = new OkHttpClient();
        String token = "5KR8iZa0Q5i-lFnmh40qFWd5Q-dRG_TR";  // Your actual Blynk token
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
                } else {
                    Log.d("BLYNK", "Blynk pin " + pin + " updated to " + value);
                }
            }
        });
    }

    private void readBlynkVirtualPin(String pin, ToggleButton toggleButton) {
        OkHttpClient client = new OkHttpClient();
        String token = "5KR8iZa0Q5i-lFnmh40qFWd5Q-dRG_TR";  // Your actual Blynk token
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
                    boolean isOn = value.equals("1");

                    runOnUiThread(() -> {
                        toggleButton.setChecked(isOn);

                        // Update relay if needed on load
                        if (toggleButton.getId() == R.id.btn_txd)
                            digitalWrite(PIN_TXD, isOn ? 1 : 0);
                    });
                } else {
                    Log.e("BLYNK", "Error reading " + pin + ": " + response.code());
                }
            }
        });
    }

    private void checkSensors() {
        runOnUiThread(() -> {
            // Sensor 1 (DSR pin)
            int sensor1 = digitalRead(PIN_DSR);
            tvSensor1.setText(sensor1 == 1 ? "OFF" : "ON");
            tvSensor1.setTextColor(sensor1 == 1 ? Color.GREEN : Color.RED);
        });
    }

    @Override
    public void onPinChange() {
        ((ToggleButton) findViewById(R.id.btn_txd)).setChecked(digitalRead(PIN_TXD) == 1);
        ((ToggleButton) findViewById(R.id.btn_ri)).setChecked(digitalRead(PIN_RI) == 1);
        checkSensors();
    }

    @Override
    public void onClick(View view) {
        digitalWrite(PIN_RI, 1);
        int isChecked = ((CompoundButton) view).isChecked() ? 1 : 0;

        if (view.getId() == R.id.btn_txd) {
            digitalWrite(PIN_TXD, isChecked);  // Relay
            updateBlynkVirtualPin("V0", isChecked);
        }
    }

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