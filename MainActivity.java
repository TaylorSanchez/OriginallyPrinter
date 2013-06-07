package edu.cse541.op;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements IBluetoothServiceEventReceiver {
    static final int MSG_DO_IT = 1;
    static final long TIME_BETWEEN_MESSAGES = 200;

    static VerticalSeekBar vSeekBarLeft;

    static VerticalSeekBar vSeekBarRight;

    private PowerManager.WakeLock wakeLock;

    private boolean led = false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final TextView vSeekBarLeftText = (TextView)findViewById(R.id.vSeekBarLeftText);
        final TextView vSeekBarRightText = (TextView)findViewById(R.id.vSeekBarRightText);

        vSeekBarRightText.setTextSize(48);
        vSeekBarLeftText.setTextSize(48);

        vSeekBarRight = (VerticalSeekBar)findViewById(R.id.vSeekBarRight);
        vSeekBarRight.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress -= 15;
                vSeekBarRightText.setText("" + (Math.abs(progress) < 5 ? 0 : progress));
            }
        });

        vSeekBarLeft = (VerticalSeekBar)findViewById(R.id.vSeekBarLeft);
        vSeekBarLeft.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress -= 15;
                vSeekBarLeftText.setText("" + (Math.abs(progress) < 5 ? 0 : progress));
            }
        });

        // Wake lock
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "do_not_turn_off");

        // Initialize Bluetooth
        BluetoothService.initialize(getApplicationContext(), this);
    }

    static Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DO_IT: {
                    int lMotorLow = vSeekBarLeft.getProgress() - 15;
                    int rMotorLow = vSeekBarRight.getProgress() - 15;

                    int lMotorHigh = lMotorLow < 0 ? 0x5 : 0x4;
                    int rMotorHigh = rMotorLow < 0 ? 0x3 : 0x2;

                    // 10000000 - Unused (Parity?)
                    // 01100000 - Motor
                    // 00010000 - Forward/Backward
                    // 00001111 - Speed
                    int lMotor = (lMotorHigh << 4) | (Math.abs(lMotorLow) < 5 ? 0 : Math.abs(lMotorLow));
                    int rMotor = (rMotorHigh << 4) | (Math.abs(rMotorLow) < 5 ? 0 : Math.abs(rMotorLow));
                    byte[] byteArray = new byte[] {
                        (byte)lMotor,
                        (byte)rMotor
                    };
                    if (BluetoothService.isConnected()) {
                        BluetoothService.sendToTarget(byteArray);
                    }

                    Message newMsg = obtainMessage(MSG_DO_IT);
                    sendMessageDelayed(newMsg, TIME_BETWEEN_MESSAGES);
                } break;
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        if (!BluetoothService.requestEnableBluetooth(this)) {
            bluetoothEnabled();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        wakeLock.acquire();

        // Start thread to send Bluetooth message
        Message newMsg = mHandler.obtainMessage(MSG_DO_IT);
        mHandler.sendMessage(newMsg);

        BluetoothService.registerBroadcastReceiver(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        wakeLock.release();

        // Stop thread that sends Bluetooth message
        mHandler.removeMessages(MSG_DO_IT);

        BluetoothService.unregisterBroadcastReceiver(this);
        BluetoothService.disconnect();
    }

    /**
     * Bluetooth is activating
     */
    @Override
    public void bluetoothEnabling() {

        // Set text
        ((TextView) findViewById(R.id.textViewState)).setText(R.string.value_enabling);
    }

    /**
     * Bluetooth is activated
     */
    @Override
    public void bluetoothEnabled() {
        Toast.makeText(this, R.string.bluetooth_enabled, Toast.LENGTH_SHORT).show();

        // Set text
        ((TextView) findViewById(R.id.textViewState)).setText(R.string.value_enabled);

        // Search for devices
        startSearchDeviceIntent();
    }

    /**
     * Searches for a Bluetooth device to connect
     */
    private void startSearchDeviceIntent() {
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, IntentRequestCodes.BT_SELECT_DEVICE);
    }

    /**
     * Bluetooth is deactivating
     */
    @Override
    public void bluetoothDisabling() {

        // Set text
        ((TextView) findViewById(R.id.textViewState)).setText(R.string.value_disabling);
    }

    /**
     * Bluetooth is deactivated
     */
    @Override
    public void bluetoothDisabled() {
        Toast.makeText(this, R.string.bluetooth_not_enabled, Toast.LENGTH_SHORT).show();

        // Set text
        ((TextView) findViewById(R.id.textViewState)).setText(R.string.value_disabled);
        ((TextView) findViewById(R.id.textViewTarget)).setText(R.string.value_na);
    }

    /**
     * Bluetooth connected to a device
     *
     * @param name    Name of the device
     * @param address MAC address of the device
     */
    @Override
    public void connectedTo(String name, String address) {

        // Set text
        ((TextView)findViewById(R.id.textViewTarget)).setText(name + " (" + address + ")");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case IntentRequestCodes.BT_REQUEST_ENABLE: {
                if (BluetoothService.bluetoothEnabled()) {
                    bluetoothEnabled();
                }
                break;
            }

            case IntentRequestCodes.BT_SELECT_DEVICE: {
                if (resultCode == Activity.RESULT_OK) {

                    // Get MAC address of the device
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

                    // Connect
                    BluetoothService.connectToDevice(address);
                }
            }

            default: {
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (BluetoothService.bluetoothAvailable()) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.option_menu, menu);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.scan:
                
                if (!BluetoothService.bluetoothEnabled()) {
                    BluetoothService.requestEnableBluetooth(this);
                    return true;
                }
                
                // Search for devices
                startSearchDeviceIntent();
                return true;
        }
        return false;
    }

    public void triggerLED(View view) {
        led = !led;

        byte[] byteArray = new byte[] {
            led ? (byte)0x6f : (byte)0x60
        };
        if (BluetoothService.isConnected()) {
            BluetoothService.sendToTarget(byteArray);
        }
    }
}
