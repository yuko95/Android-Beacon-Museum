package com.biusobonnanno.progetto_iot;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;

import com.biusobonnanno.progetto_iot.Models.BeaconUtility;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity implements BeaconConsumer {
    private static final String TAG = "myBeacon";

    private BluetoothAdapter bluetoothState = BluetoothAdapter.getDefaultAdapter();
    private BroadcastReceiver observeBluetoothState;

    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final int PERMISSION_REQUEST_BACKGROUND_LOCATION = 2;
    private BeaconManager beaconManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(bluetoothState == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            finish();
        }
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        initBeaconManager(); //inizializzo il beaconManager

        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleScan(true);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(observeBluetoothState);
        toggleScan(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        final MenuItem bluetoothButton = menu.findItem(R.id.action_bluetooth);
        setBluetoothIcon(bluetoothButton, bluetoothState.isEnabled());

        observeBluetoothState = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    switch (state) {
                        case BluetoothAdapter.STATE_OFF:
                            setBluetoothIcon(bluetoothButton, false);
                            Toast.makeText(MainActivity.this, "Bluetooth disabled", Toast.LENGTH_SHORT).show();
                            toggleScan(false);
                            break;
                        case BluetoothAdapter.STATE_ON:
                            setBluetoothIcon(bluetoothButton, true);
                            Toast.makeText(MainActivity.this, "Bluetooth enabled", Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(observeBluetoothState, filter);
        return true;
    }

    /** Imposta l'icona al Bluetooth*/
    private void setBluetoothIcon(MenuItem item, boolean status){
        Drawable icon = AppCompatResources.getDrawable(MainActivity.this, status ? R.drawable.ic_round_bluetooth_24px : R.drawable.ic_round_bluetooth_disabled_24px).mutate();
        item.setIcon(icon);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        if (id == R.id.action_bluetooth) {
            if (!bluetoothState.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 0);
            }
            else bluetoothState.disable();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /** Codice per logica Beacon */

    /** Inizializza il BeaconManager**/
    private void initBeaconManager() {
        beaconManager = BeaconManager.getInstanceForApplication(this);
        // Add all the beacon types we want to discover
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:0-2=0499,i:4-19,i:20-21,i:22-23,p:24-24")); // TBD - RUUVI_LAYOUT
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24")); // iBeacon - IBEACON_LAYOUT
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_UID_LAYOUT));
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_URL_LAYOUT));
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_TLM_LAYOUT));
    }

    /** Funzione che individua i beacon  */
    @Override
    public void onBeaconServiceConnect() {
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if(beaconManager.isBound(MainActivity.this) == true) showBeacons(beacons);
            }
        });
        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) { e.printStackTrace(); }
    }

    /** Start o Stop dello scan **/
    private void toggleScan(boolean enable) {
        boolean isScan = false;
        if(beaconManager.isBound(MainActivity.this) == true) beaconManager.unbind(MainActivity.this);
        else if (enable && isPermissionGranted()) {
            if (!bluetoothState.isEnabled()) Toast.makeText(MainActivity.this, R.string.enable_bluetooth_to_start_scanning, Toast.LENGTH_LONG).show();
            else {
                beaconManager.bind(MainActivity.this);
                isScan = true;
            }
        }

        FloatingActionButton fab = findViewById(R.id.fab);
        findViewById(R.id.progressBar).setVisibility(isScan ? View.VISIBLE : View.GONE);
        fab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(MainActivity.this, isScan ? R.color.colorPauseFab : R.color.colorSecondary)));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AnimatedVectorDrawableCompat anim = AnimatedVectorDrawableCompat.create(MainActivity.this, isScan ? R.drawable.play_to_pause : R.drawable.pause_to_play);
            fab.setImageDrawable(anim);
            anim.start();
        } else fab.setImageDrawable(AppCompatResources.getDrawable(MainActivity.this, isScan ? R.drawable.pause_icon : R.drawable.play_icon));
    }

    /** Memorizza i beacons **/
    private void showBeacons(Collection<Beacon> beacons){
        if (beacons.size() <= 0) return;
        LinearLayout dynamicContent = findViewById(R.id.dynamic_content);
        dynamicContent.removeAllViews();
        Iterator<Beacon> beaconIterator = beacons.iterator();
        while (beaconIterator.hasNext()){
            final Beacon findBeacon = beaconIterator.next();
            View newBeaconView = getLayoutInflater().inflate(R.layout.item_beacon, dynamicContent, false);
            newBeaconView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG,"Beacon click " + findBeacon.toString());
                }
            });
            ((TextView)newBeaconView.findViewById(R.id.beacon_address)).setText(findBeacon.getBluetoothAddress());
            ((TextView)newBeaconView.findViewById(R.id.beacon_type)).setText(BeaconUtility.getType(findBeacon));
            ((TextView)newBeaconView.findViewById(R.id.beacon_name)).setText(findBeacon.getBluetoothName());
            ((TextView)newBeaconView.findViewById(R.id.beacon_distance)).setText(String.format("%.2f", findBeacon.getDistance()));
            dynamicContent.addView(newBeaconView);
        }
    }

    /** Codice per avere accesso alla localizzazione **/
    private boolean isPermissionGranted(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (this.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return true;
                    /*if (this.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("This app needs background location access");
                        builder.setMessage("Please grant location access so this app can detect beacons in the background.");
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @TargetApi(23)
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, PERMISSION_REQUEST_BACKGROUND_LOCATION);
                            }
                        });
                        builder.show();
                    }
                    else {
                        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("Functionality limited");
                        builder.setMessage("Since background location access has not been granted, this app will not be able to discover beacons in the background.  Please go to Settings -> Applications -> Permissions and grant background location access to this app.");
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                            }
                        });
                        builder.show();
                    }
                    */
                }
                return true;
            } else {
                if (this.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
                }
                else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Permission needed");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons.");
                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
                        }
                    });
                    builder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    builder.show();
                }
                return false;
            }
        } return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_FINE_LOCATION: {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                return;
            }
            case PERMISSION_REQUEST_BACKGROUND_LOCATION: {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since background location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }
}
