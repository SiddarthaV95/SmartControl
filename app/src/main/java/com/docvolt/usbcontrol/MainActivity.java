package com.docvolt.usbcontrol;

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
    private TextView tvSensor1, tvSensor2, tvSensor3;
    private final String TAG = "MainActivity";
    UsbIOService mUsbIOService = new UsbIOService();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize TextViews
        tvSensor1 = findViewById(R.id.tv_sensor1);
        tvSensor2 = findViewById(R.id.tv_sensor2);
        tvSensor3 = findViewById(R.id.tv_sensor3);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Remove or replace this line if cb_rsd doesn't exist in your layout
        // ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.cb_rsd), (v, insets) -> {
        //    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        //    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
        //    return insets;
        // });

        findViewById(R.id.btn_txd).setOnClickListener(this);
        findViewById(R.id.btn_dtr).setOnClickListener(this);
        findViewById(R.id.btn_rts).setOnClickListener(this);
        findViewById(R.id.btn_rxd).setOnClickListener(this);

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


        //digitalWrite(PIN_RI, 1); // Force RI high permanently
    }

    private void checkSensors() {
        runOnUiThread(() -> {
            // Sensor 1 (DSR pin)
            int sensor1 = digitalRead(PIN_DSR);
            tvSensor1.setText(sensor1 == 1 ? "HIGH" : "LOW");
            tvSensor1.setTextColor(sensor1 == 1 ? Color.GREEN : Color.RED);

            // Sensor 2 (DCD pin)
            int sensor2 = digitalRead(PIN_DCD);
            tvSensor2.setText(sensor2 == 1 ? "HIGH" : "LOW");
            tvSensor2.setTextColor(sensor2 == 1 ? Color.GREEN : Color.RED);

            // Sensor 3 (CTS pin)
            int sensor3 = digitalRead(PIN_CTS);
            tvSensor3.setText(sensor3 == 1 ? "HIGH" : "LOW");
            tvSensor3.setTextColor(sensor3 == 1 ? Color.GREEN : Color.RED);
        });
    }

    @Override
    public void onPinChange() {
        ((ToggleButton) findViewById(R.id.btn_txd)).setChecked(digitalRead(PIN_TXD) == 1);
        ((ToggleButton) findViewById(R.id.btn_dtr)).setChecked(digitalRead(PIN_DTR) == 1);
        ((ToggleButton) findViewById(R.id.btn_rts)).setChecked(digitalRead(PIN_RTS) == 1);
        ((ToggleButton) findViewById(R.id.btn_rxd)).setChecked(digitalRead(PIN_RXD) == 1);
        ((ToggleButton) findViewById(R.id.btn_ri)).setChecked(digitalRead(PIN_RI) == 1);
        checkSensors();
    }

    @Override
    public void onClick(View view) {

        digitalWrite(PIN_RI, 1);
        int isChecked = ((CompoundButton) view).isChecked() ? 1 : 0;

        if (view.getId() == R.id.btn_txd) {
            digitalWrite(PIN_TXD, isChecked);  // Relay 1
        } else if (view.getId() == R.id.btn_rxd) {
            digitalWrite(PIN_RXD, isChecked); // Relay 2
        } else if (view.getId() == R.id.btn_rts) {
            digitalWrite(PIN_RTS, isChecked);  // Relay 3
        } else if (view.getId() == R.id.btn_dtr) {
            digitalWrite(PIN_DTR, isChecked);  // Relay 4
        }
    }

    /*This handles authentication of USB devices*/
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