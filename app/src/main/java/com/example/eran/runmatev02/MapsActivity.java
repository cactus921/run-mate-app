package com.example.eran.runmatev02;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.DoubleBuffer;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static android.content.ContentValues.TAG;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    //for debug
    boolean isMapReady = false;
    boolean isRecord = false;

    Marker idleMarker, currentMarker;

    LatLng lastLoc;
    boolean gotLocation = false;
    PolylineOptions mPolylineOptions;
    Polyline mPolyline;
    double totalDistance = 0;
    List<LatLng> mPointsList;

    private static final int SAMPLE_SIZE = 10;
    LatLng[] pointsArr = new LatLng[SAMPLE_SIZE];
    int i=0;

    BluetoothAdapter btAdapter = null;
    BluetoothDevice btDevice = null;
    BluetoothSocket btSocket = null;
    String HCMACAddress = "98:D3:31:FB:2A:BB";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    Set<BluetoothDevice> pairedDevices = null;

    CheckedTextView connText;
    CheckedTextView runText;
    ToggleButton startStopBtn;
    TextView totDistText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(mReceiver, filter);

        connText = (CheckedTextView) findViewById(R.id.checkedTextView_Conn);
        runText = (CheckedTextView) findViewById(R.id.checkedTextView_Run);
        startStopBtn = (ToggleButton) findViewById(R.id.toggleButton2);
        totDistText = (TextView) findViewById(R.id.totDistVal);

        /* BT CONNECTION */
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        if (btAdapter == null) {
            // Device does not support Bluetooth
        }

        if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);

        }
        else {
            connectToHC();
        }

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mPolylineOptions = new PolylineOptions();
        mPolyline = mMap.addPolyline(mPolylineOptions);
        LatLng home = new LatLng(31.964805,34.809951);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(home));
        mMap.moveCamera(CameraUpdateFactory.zoomTo(18));
        idleMarker = mMap.addMarker(new MarkerOptions().position(home).title("My Position").visible(false));
        currentMarker = mMap.addMarker(new MarkerOptions().position(home).title("My Position").visible(false));

        isMapReady = true;
    }

    public void onUpdateLocation (double latitude, double longitude, double distance){
        if (isMapReady){
            LatLng newLoc = new LatLng(latitude, longitude);

            if (isRecord){
                //mMap.addMarker(new MarkerOptions().position(newLoc));
                if (idleMarker.isVisible())
                    idleMarker.setVisible(false);
                if (!currentMarker.isVisible())
                    currentMarker.setVisible(true);
                currentMarker.setPosition(newLoc);
                mMap.moveCamera(CameraUpdateFactory.newLatLng(newLoc));
                mMap.moveCamera(CameraUpdateFactory.zoomTo(18));
                if (distance>0) { //means that we have lastLoc.
                    mMap.addCircle(new CircleOptions().center(lastLoc).radius(0.2).fillColor(R.color.colorPrimary));
                    mMap.addPolyline(new PolylineOptions().add(lastLoc).add(newLoc));
                }

                totalDistance += distance;
                totDistText.setText(String.valueOf((int)totalDistance));
            }
            else{
                idleMarker.setPosition(newLoc);
                idleMarker.setVisible(true);
            }

            lastLoc = newLoc;
        }
    }

    public void onReceiveLocation (double latitude, double longitude, double distance){
        pointsArr[i] = new LatLng(latitude,longitude);
        i++;

        if(i>=10){
            //onUpdateLocation(latitude,longitude, distance);
            double meanLat = 0, meanLng = 0;
            for (int j=0; j<SAMPLE_SIZE; j++){
                meanLat += pointsArr[j].latitude;
                meanLng += pointsArr[j].longitude;
            }
            meanLat /= SAMPLE_SIZE;
            meanLng /= SAMPLE_SIZE;
            onUpdateLocation(meanLat, meanLng, distance);
            i=0;
        }
    }

    public void onStartStopBtn(View view){
        boolean running = startStopBtn.isChecked();
        if (running){
            isRecord = true;
            runText.setText(getResources().getString(R.string.run_true));
            runText.setTextColor(getResources().getColor(R.color.colorDkGreen));
            mMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromResource(R.mipmap.start_marker)).position(lastLoc).title("Start"));
        }
        else{
            isRecord = false;
            runText.setText(getResources().getString(R.string.run_false));
            runText.setTextColor(getResources().getColor(R.color.colorDkRed));
            mMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromResource(R.mipmap.finish_marker)).position(lastLoc).title("Finish"));
        }
    }

    public void onBTConn(){
        /*On Connect Actions*/
        connText.setText(getResources().getString(R.string.connect_true));
        connText.setTextColor(getResources().getColor(R.color.colorDkGreen));
    }

    public void onBTDisConn(){
        /*On Connect Actions*/
        connText.setText(getResources().getString(R.string.connect_false));
        connText.setTextColor(getResources().getColor(R.color.colorDkRed));
    }

    public void connectToHC(){
        pairedDevices = btAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                if(deviceHardwareAddress.equals(HCMACAddress)){
                    btDevice = device;
                }
            }
        }

        try {
            btSocket = btDevice.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            e.printStackTrace();
        }

        ConnectThread mConnectThread = new ConnectThread(btDevice);
        mConnectThread.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        // check if the request code is same as what is passed  here it is 2
        if(requestCode==1) //BT
        {
            if(resultCode == 0) {
                //"Cancel"
                return;
            }
            else{
                //"Allow"
                connectToHC();
            }

        }
    }

    /*  BT CONNECT THREAD */
    public class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        public ConnectedThread mConnectedThread;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
            //onBTConn();
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            btAdapter.cancelDiscovery();
            //onBTConn();
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            //onBTConn();
            //manageMyConnectedSocket(mmSocket);
            mConnectedThread = new ConnectedThread(mmSocket);
            mConnectedThread.start();
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    /*BT CONNECTED THREAD*/
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        public void run() {
            byte[] buffer = new byte[1024];
            int begin = 0;
            int bytes = 0;
            while (true) {
                try {
                    bytes += mmInStream.read(buffer, bytes, buffer.length - bytes);
                    for(int i = begin; i < bytes; i++) {
                        if(buffer[i] == "#".getBytes()[0]) {
                            mHandler.obtainMessage(1, begin, i, buffer).sendToTarget();
                            begin = i + 1;
                            if(i == bytes - 1) {
                                bytes = 0;
                                begin = 0;
                            }
                        }
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            byte[] buf = (byte[]) msg.obj;
            int begin = (int)msg.arg1;
            int end = (int)msg.arg2;

            String str = new String(buf);
            float results[] = new float[2];

            switch(msg.what) {
                case 1:
                    //String writeMessage = new String(writeBuf);
                    //writeMessage = writeMessage.substring(begin, end);
                    int index = str.indexOf("$");
                    String str1 = str.substring(0,index);
                    String str2 = str.substring(index+1, str.indexOf("#"));
                    double latitude = Double.parseDouble(str1);
                    double longitude = Double.parseDouble(str2);
                    if (gotLocation){
                        Location.distanceBetween(lastLoc.latitude,lastLoc.longitude,latitude,longitude,results);
                        if (results[0]>0) {
                            //onUpdateLocation(latitude, longitude, results[0]);
                            onReceiveLocation(latitude, longitude, results[0]);
                        }
                    }
                    else{
                        onUpdateLocation(latitude, longitude, 0);
                        gotLocation = true;
                    }
                    break;
            }
        }
    };


    //The BroadcastReceiver that listens for bluetooth broadcasts
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                //Device found
            }
            else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                //Device is now connected
                onBTConn();
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                //Done searching
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
                //Device is about to disconnect
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                //Device has disconnected
                onBTDisConn();
            }
        }
    };
};

