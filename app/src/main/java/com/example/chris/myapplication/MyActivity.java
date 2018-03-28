package com.example.chris.myapplication;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MyActivity extends Activity implements SeekBar.OnSeekBarChangeListener, OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final String TAG = "MyActivity";

    private static UsbSerialPort sPort = null;

    private final int FM_NOTHING = 0;
    private final int FM_ME = 1;
    private final int FM_CAR = 2;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    int mKeyFlags = 0;
    float[] mAxisPos = new float[4];
    private static final int[] SERIAL_HEADER = {255,254,253,252};

    public class RadioData {
        int throttle;
        int yaw;
        int pitch;
        int roll;
        int dial1;
        int dial2;
        int switches;
        RadioData() {
            reset();
        }
        void reset() {
            throttle = 0;
            yaw = 127;
            pitch = 127;
            roll = 127;
            dial1 = 0;
            dial2 = 0;
            switches = 0;
        }
    }

    class TelemetryData {
        float lat;
        float lon;
        short heading;
        short pitch;
        short roll;
        int alt;
        short flags;
        short pps;

        int tmpLat;
        int tmpLon;
        short tmpHeading;
        short tmpPitch;
        short tmpRoll;
        int tmpAlt;
        short tmpFlags;
        short tmpPPS;
        TelemetryData() {
            reset();
        }
        void reset() {
            lat = lon = 0;
            heading = 0;
            pitch = 0;
            roll = 0;
            flags = 0;
            alt = 0;
        }
    }

    RadioData mRadioData;
    TelemetryData mTelemetryData;

    Location mLocationMe;
    Location mLocationCar;

    Polygon myLocationMarker = null;
    Polygon carLocationMarker = null;

    long mLastCarMarkerUpdateTime;

    //int[] status = new int[5];
    int currentBytePos = 0;

    public static final int BUTTON_X = 0x0001; // cross
    public static final int BUTTON_Y = 0x0002; // circle
    public static final int BUTTON_A = 0x0004; // square
    public static final int BUTTON_B = 0x0008; // triangle

    public static final int BUTTON_L_TRIGGER =  0x0010;
    public static final int BUTTON_R_TRIGGER =  0x0020;
    public static final int BUTTON_L_BUMPER =   0x0040;
    public static final int BUTTON_R_BUMPER =   0x0080;

    public static final int BUTTON_D_LEFT =     0x0100;
    public static final int BUTTON_D_RIGHT =    0x0200;
    public static final int BUTTON_D_UP =       0x0400;
    public static final int BUTTON_D_DOWN =     0x0800;

    public static final int BUTTON_START =   0x1000;
    public static final int BUTTON_SELECT = 0x2000;

    //SeekBar[] mAxisSliders = new SeekBar[4];
    //CheckBox[] mCheckBoxes = new CheckBox[2];
    //TextView mTrackingText;
    TextView mConsoleText;
    ScrollView mScrollView;
    GoogleMap mMap;
    Fragment mMapFragment;
    GLSurfaceView mOGLView;

    TextView mLatText;
    TextView mLonText;
    //TextView mHeadingText;
    TextView mAltText;

    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    boolean mSetFirstZoom = false;

    protected Application mApplication;
    protected SerialPort mSerialPort;
    protected OutputStream mOutputStream;
    private InputStream mInputStream;

    protected Camera mCamera = null;
    protected CameraPreview mPreview;

    protected int mFollowMode = FM_NOTHING;
    protected RadioButton mRadioButtonNothing;
    protected RadioButton mRadioButtonMe;
    protected RadioButton mRadioButtonCar;

    protected MyRenderer mRenderer;

    public MyActivity() {
        mRadioData = new RadioData();
        mTelemetryData = new TelemetryData();
        mLocationMe = new Location("");
        mLocationCar = new Location("");
        mLastCarMarkerUpdateTime = System.currentTimeMillis();
    }

    private SerialInputOutputManager mSerialIoManager;
    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {
                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }
                @Override
                public void onNewData(final byte[] data) {
                    MyActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MyActivity.this.updateReceivedData(data);
                        }
                    });
                }
            };

    private void toast(String text){
        Toast toast = Toast.makeText(this, text, Toast.LENGTH_LONG);
        toast.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mApplication = (Application) getApplication();

        buildGoogleApiClient();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_my);

        /*mAxisSliders[0] = (SeekBar) findViewById(R.id.axis1);
        mAxisSliders[1] = (SeekBar) findViewById(R.id.axis2);
        mAxisSliders[2] = (SeekBar) findViewById(R.id.axis3);
        mAxisSliders[3] = (SeekBar) findViewById(R.id.axis4);
        for (int i = 0; i < mAxisSliders.length; i++) {
            mAxisSliders[i].setOnSeekBarChangeListener(this);
            mAxisSliders[i].setProgress(50);
        }

        mCheckBoxes[0] = (CheckBox)findViewById(R.id.switch1CheckBox);
        mCheckBoxes[1] = (CheckBox)findViewById(R.id.switch2CheckBox);
        for (int i = 0; i < mCheckBoxes.length; i++) {
            mCheckBoxes[i].setChecked(false);
        }*/

        mRadioButtonNothing = (RadioButton)findViewById(R.id.radioButtonNothing);
        mRadioButtonMe = (RadioButton)findViewById(R.id.radioButtonMe);
        mRadioButtonCar = (RadioButton)findViewById(R.id.radioButtonCar);

        mRadioButtonNothing.setChecked(true);

        //mTrackingText = (TextView)findViewById(R.id.tracking);
        mConsoleText = (TextView)findViewById(R.id.console);
        mScrollView = (ScrollView)findViewById(R.id.scrollView);
        //mTitleTextView = (TextView)findViewById(R.id.title);
        mMapFragment = getFragmentManager().findFragmentById(R.id.map);
        ((MapFragment)getFragmentManager().findFragmentById(R.id.map)).getMapAsync(this);

        mLatText = (TextView)findViewById(R.id.lat);
        mLonText = (TextView)findViewById(R.id.lon);
        //mHeadingText = (TextView)findViewById(R.id.heading);
        mAltText = (TextView)findViewById(R.id.alt);

        findUsbSerial();

        getCameraInstance();
        mPreview = new CameraPreview(this,mCamera);
        final FrameLayout cameraFrameLayout = (FrameLayout)findViewById(R.id.camera);
        cameraFrameLayout.addView(mPreview);

        android.hardware.Camera.Parameters camParams = mCamera.getParameters();
        Camera.Size previewSize = camParams.getPreviewSize();
        float cameraPreviewAspect = previewSize.width / (float)previewSize.height;


        mRenderer = new MyRenderer(this);

        mOGLView = new GLSurfaceView(this);
        mOGLView.setRenderer(mRenderer);
        final FrameLayout oglFrameLayout = (FrameLayout)findViewById(R.id.ogl);
        oglFrameLayout.addView(mOGLView);

        mOGLView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        /*ViewTreeObserver observer = cameraFrameLayout.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                                               @Override
                                               public void onGlobalLayout() {
                                                   int headerLayoutHeight = cameraFrameLayout.getHeight();
                                                   int headerLayoutWidth = cameraFrameLayout.getWidth();
                                                   FrameLayout.LayoutParams params = new FrameLayout.LayoutParams((int)(1.777*934),934);
                                                   mPreview.setLayoutParams(params);
                                                   cameraFrameLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                                               }
                                           });*/

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(1280,934);
        mPreview.setLayoutParams(params);

    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "onConnected");
        /*mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        if (mLastLocation != null) {
            //mLatitudeText.setText(String.valueOf(mLastLocation.getLatitude()));
            //mLongitudeText.setText(String.valueOf(mLastLocation.getLongitude()));
            LatLng loc = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
            CameraPosition cameraPosition = new CameraPosition.Builder().target(loc).zoom(18).build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }*/
        createLocationRequest();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended");
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        toast("Map ready");

        float newZoom = 20;
        CameraPosition cameraPosition = new CameraPosition.Builder().target(new LatLng(35.615133, 139.612240)).zoom(newZoom).build();
        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

        //createLocationRequest();
    }

    protected void createLocationRequest() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(3000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setSmallestDisplacement(1);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    protected double[] getRotatedPolygonPoints(float angle) {
        double[] result = new double[8];

        angle *= 0.0174532925199432957;

        result[0] = -0.000008 * Math.cos(angle) - -0.000004 * Math.sin(angle);
        result[1] = -0.000008 * Math.sin(angle) + -0.000004 * Math.cos(angle);

        result[2] = 0;
        result[3] = 0;

        result[4] = -0.000008 * Math.cos(angle) - +0.000004 * Math.sin(angle);
        result[5] = -0.000008 * Math.sin(angle) + +0.000004 * Math.cos(angle);

        result[6] = 0.000008 * Math.cos(angle) - 0 * Math.sin(angle);
        result[7] = 0.000008 * Math.sin(angle) + 0 * Math.cos(angle);

        return result;
    }

    public void onLocationChanged(Location location) {
        mLocationMe = location;
        Log.d(TAG, "onLocationChanged "+location.toString());

        if ( mFollowMode == FM_ME )
            setMapToLocation(mLocationMe);

        if ( myLocationMarker != null )
            myLocationMarker.remove();

        double x = mLocationMe.getLatitude();
        double y = mLocationMe.getLongitude();
        float bearing = mLocationMe.getBearing();
        LatLng loc = new LatLng(x,y);
        //CircleOptions co = new CircleOptions().center(loc).strokeColor(Color.RED).strokeWidth(5).radius(0.5);
        double[] p = getRotatedPolygonPoints(bearing);
        PolygonOptions po = new PolygonOptions().add(new LatLng(x+p[0], y+p[1]), new LatLng(x+p[2], y+p[3]), new LatLng(x+p[4], y+p[5]), new LatLng(x+p[6], y+p[7])).strokeColor(Color.RED).strokeWidth(5);
        myLocationMarker = mMap.addPolygon(po);
    }

    void setMapToLocation(Location location) {
        LatLng loc = new LatLng(location.getLatitude(), location.getLongitude());
        //float newZoom = mSetFirstZoom ? mMap.getCameraPosition().zoom : 18;
        //CameraPosition cameraPosition = new CameraPosition.Builder().target(loc).zoom(mMap.getCameraPosition().zoom).build();
        mMap.animateCamera(CameraUpdateFactory.newLatLng(loc));
        mMapFragment.getView().invalidate();
        mSetFirstZoom = true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.my, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onNiceButton(View v) {
        Log.d(TAG, "Nice button");
        //tryUsbSerial();
        logConsole("Camera position: " + mMap.getCameraPosition().toString());
        mMap.setMyLocationEnabled(true);
    }

    private byte[] sliderPos = new byte[1];

    /*public void sendStatus() {
        if ( sPort == null )
            return;

        for (int i = 0; i < 4; i++)
            status[i] = (byte)mAxisSliders[i].getProgress();
        status[4] = 0;
        status[5] = 0;

        if ( (mKeyFlags & BUTTON_X) != 0 ) status[4] |= 0x01;
        if ( (mKeyFlags & BUTTON_Y) != 0 ) status[4] |= 0x02;
        if ( (mKeyFlags & BUTTON_A) != 0 ) status[4] |= 0x04;
        if ( (mKeyFlags & BUTTON_B) != 0 ) status[4] |= 0x08;
        if ( (mKeyFlags & BUTTON_L_BUMPER) != 0 )  status[4] |= 0x10;
        if ( (mKeyFlags & BUTTON_R_BUMPER) != 0 )  status[4] |= 0x20;
        if ( (mKeyFlags & BUTTON_L_TRIGGER) != 0 ) status[4] |= 0x40;
        if ( (mKeyFlags & BUTTON_R_TRIGGER) != 0 ) status[4] |= 0x80;
        if ( (mKeyFlags & BUTTON_D_LEFT) != 0 )  status[5] |= 0x01;
        if ( (mKeyFlags & BUTTON_D_RIGHT) != 0 ) status[5] |= 0x02;
        if ( (mKeyFlags & BUTTON_D_UP) != 0 )    status[5] |= 0x04;
        if ( (mKeyFlags & BUTTON_D_DOWN) != 0 )  status[5] |= 0x08;
        if ( (mKeyFlags & BUTTON_START) != 0 )   status[5] |= 0x10;
        if ( (mKeyFlags & BUTTON_SELECT) != 0 )  status[5] |= 0x20;

        try {
            sPort.write(status, 1000);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
        //sendStatus();
    }
    public void onStartTrackingTouch(SeekBar seekBar) {
        //mTrackingText.setText(getString(R.string.seekbar_tracking_on));
    }
    public void onStopTrackingTouch(SeekBar seekBar) {
        //mTrackingText.setText(getString(R.string.seekbar_tracking_off));
        seekBar.setProgress(50);
    }

    public void logConsole(String s) {

        Log.d(TAG, s);

        mConsoleText.append(s + "\n");

        // Erase excessive lines
        int excessLineNumber = mConsoleText.getLineCount() - 100;
        if (excessLineNumber > 0) {
            int eolIndex = -1;
            CharSequence charSequence = mConsoleText.getText();
            for(int i=0; i<excessLineNumber; i++) {
                do {
                    eolIndex++;
                } while(eolIndex < charSequence.length() && charSequence.charAt(eolIndex) != '\n');
            }
            if (eolIndex < charSequence.length()) {
                mConsoleText.getEditableText().delete(0, eolIndex+1);
            }
            else {
                mConsoleText.setText("");
            }
        }

        mScrollView.fullScroll(View.FOCUS_DOWN);
    }

    public void findUsbSerial() {

        logConsole("findUsbSerial");

        // Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            Log.d(TAG, "No UsbSerialDrivers");
            logConsole("No UsbSerialDrivers");
            return;
        }
        else {
            logConsole("" + availableDrivers.size() + " drivers");
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            Log.d(TAG, "You probably need to call UsbManager.requestPermission(driver.getDevice()");
            logConsole("You probably need to call UsbManager.requestPermission(driver.getDevice()");
            return;
        }
        else
            logConsole("Connection opened");

        // Read some data! Most have just one port (port 0).
        List<UsbSerialPort> ports = driver.getPorts();
        if (ports.isEmpty()) {
            Log.d(TAG, "No ports");
            logConsole("No ports");
            return;
        }
        else
            logConsole(""+ports.size()+" ports");

        UsbSerialPort port = ports.get(0);
        sPort = port;

        /*try {
            port.open(connection);
            port.setParameters(9600, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            byte buffer[] = new byte[16];
            int totalRead = 0;
            //while (totalRead < 100) {
                int numBytesRead = port.read(buffer, 1000);
                Log.d(TAG, "Read " + numBytesRead + " bytes.");
                logConsole("Read " + numBytesRead + " bytes.");
                totalRead += numBytesRead;
            //}
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                port.close();
            }
            catch (IOException e1) {
                e1.printStackTrace();
            }
        }*/

    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
// Ignore.
            }
            sPort = null;
        }
        finish();
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, port=" + sPort);
        logConsole("Resumed, port=" + sPort);
        if (sPort == null) {
            //mTitleTextView.setText("No serial device.");
            logConsole("No serial device");
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (connection == null) {
                logConsole("Opening device failed");
                return;
            }
            try {
                sPort.open(connection);
                sPort.setParameters(57600, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                try {
                    //sendStatus();
                }
                catch (Exception e) {
                    StackTraceElement[] ste = e.getStackTrace();
                    for (int i = 0; i < ste.length; i++) {
                        logConsole( ste[i].toString() +"\n" );
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                logConsole("Error opening device: " + e.getMessage());
                try {
                    sPort.close();
                } catch (IOException e2) {
// Ignore.
                }
                sPort = null;
                return;
            }
            logConsole("Serial device: " + sPort.getClass().getSimpleName());
        }
        onDeviceStateChange();
    }
    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            logConsole("Stopping io manager ..\n");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }
    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            logConsole("Starting io manager ..\n");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);

            try {
                //sendStatus();
            }
            catch (Exception e) {
                StackTraceElement[] ste = e.getStackTrace();
                for (int i = 0; i < ste.length; i++) {
                    logConsole( ste[i].toString() +"\n" );
                }
            }
        }
    }
    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void updateCheckBoxes() {
        //mCheckBoxes[0].setChecked( (mRadioData.switches & 0x1) == 0x1 );
        //mCheckBoxes[1].setChecked( (mRadioData.switches & 0x2) == 0x2 );
    }

    void updateCarLocationMarker() {

        mLocationCar.setLatitude(mTelemetryData.lat);
        mLocationCar.setLongitude(mTelemetryData.lon);

        long now = System.currentTimeMillis();

        if ( now - mLastCarMarkerUpdateTime < 500 )
            return;

        mLastCarMarkerUpdateTime = now;

        if ( mFollowMode == FM_CAR )
            setMapToLocation(mLocationCar);

        if ( carLocationMarker != null )
            carLocationMarker.remove();

        double x = mLocationCar.getLatitude();
        double y = mLocationCar.getLongitude();
        short bearing = mTelemetryData.heading;
        LatLng loc = new LatLng(x,y);
        //CircleOptions co = new CircleOptions().center(loc).strokeColor(Color.YELLOW).strokeWidth(5).radius(0.5);
        double[] p = getRotatedPolygonPoints(bearing);
        PolygonOptions po = new PolygonOptions().add(new LatLng(x+p[0], y+p[1]), new LatLng(x+p[2], y+p[3]), new LatLng(x+p[4], y+p[5]), new LatLng(x+p[6], y+p[7])).strokeColor(Color.YELLOW).strokeWidth(5);
        carLocationMarker = mMap.addPolygon(po);
    }

    private void processRXByte(int b) {
        //logConsole("p "+currentBytePos+" b "+b);
        switch ( currentBytePos ) {
            case 0: if (b == SERIAL_HEADER[0]) currentBytePos++; break;
            case 1: if (b == SERIAL_HEADER[1]) currentBytePos++; break;
            case 2: if (b == SERIAL_HEADER[2]) currentBytePos++; break;
            case 3: if (b == SERIAL_HEADER[3]) currentBytePos++; break;

            case 4: mRadioData.throttle = b; /*mAxisSliders[0].setProgress( (int) (b/(float)255.0 * 100) );*/ currentBytePos++; break;
            case 5: mRadioData.yaw = b; /*mAxisSliders[1].setProgress((int) (b / (float) 255.0 * 100));*/ currentBytePos++; break;
            case 6: mRadioData.pitch = b; /*mAxisSliders[2].setProgress((int) (b / (float) 255.0 * 100));*/ currentBytePos++; break;
            case 7: mRadioData.roll = b; /*mAxisSliders[3].setProgress((int) (b / (float) 255.0 * 100));*/ currentBytePos++; break;

            case 8: mRadioData.dial1 = b; /*mAxisSliders[4].setProgress( (int) (b/(float)255.0 * 100) );*/ currentBytePos++; break;
            case 9: mRadioData.dial2 = b; /*mAxisSliders[5].setProgress((int) (b / (float) 255.0 * 100));*/ currentBytePos++; break;

            case 10: mRadioData.switches = b; updateCheckBoxes(); currentBytePos++; break;

            case 11: mTelemetryData.tmpLat = 0; mTelemetryData.tmpLat |= (b); currentBytePos++; break;
            case 12: mTelemetryData.tmpLat |= (b<<8); currentBytePos++; break;
            case 13: mTelemetryData.tmpLat |= (b<<16); currentBytePos++; break;
            case 14: mTelemetryData.tmpLat |= (b<<24); mTelemetryData.lat = Float.intBitsToFloat(mTelemetryData.tmpLat); mLatText.setText("Lat: "+mTelemetryData.lat); currentBytePos++; break;

            case 15: mTelemetryData.tmpLon = 0; mTelemetryData.tmpLon |= (b); currentBytePos++; break;
            case 16: mTelemetryData.tmpLon |= (b<<8); currentBytePos++; break;
            case 17: mTelemetryData.tmpLon |= (b<<16); currentBytePos++; break;
            case 18: mTelemetryData.tmpLon |= (b<<24); mTelemetryData.lon = Float.intBitsToFloat(mTelemetryData.tmpLon); mLonText.setText("Lon: " + mTelemetryData.lon); currentBytePos++; break;

            case 19: mTelemetryData.tmpHeading = 0; mTelemetryData.tmpHeading |= (b); currentBytePos++; break;
            case 20: mTelemetryData.tmpHeading |= (b<<8); mTelemetryData.heading = mTelemetryData.tmpHeading; /*mHeadingText.setText("Head: "+mTelemetryData.heading);*/ updateCarLocationMarker(); currentBytePos++; break;

            case 21: mTelemetryData.tmpPitch = 0; mTelemetryData.tmpPitch |= (b); currentBytePos++; break;
            case 22: mTelemetryData.tmpPitch |= (b<<8); mTelemetryData.pitch = mTelemetryData.tmpPitch; currentBytePos++; break;

            case 23: mTelemetryData.tmpRoll = 0; mTelemetryData.tmpRoll |= (b); currentBytePos++; break;
            case 24: mTelemetryData.tmpRoll |= (b<<8); mTelemetryData.roll = mTelemetryData.tmpRoll; currentBytePos++; break;

            case 25: mTelemetryData.tmpAlt = 0; mTelemetryData.tmpAlt |= (b); currentBytePos++; break;
            case 26: mTelemetryData.tmpAlt |= (b<<8); currentBytePos++; break;
            case 27: mTelemetryData.tmpAlt |= (b<<16); currentBytePos++; break;
            case 28: mTelemetryData.tmpAlt |= (b<<24); mTelemetryData.alt = mTelemetryData.tmpAlt; mAltText.setText("Alt: "+(mTelemetryData.alt*0.01)); currentBytePos++; break;

            case 29: mTelemetryData.flags = (short)b; currentBytePos++; break;
            //case 30: mTelemetryData.tmpFlags |= (b<<8); mTelemetryData.flags = mTelemetryData.tmpFlags; updateCarLocationMarker(); currentBytePos++; break;

            case 30: mTelemetryData.tmpPPS = 0; mTelemetryData.tmpPPS |= (b); currentBytePos++; break;
            case 31: mTelemetryData.tmpPPS |= (b<<8); mTelemetryData.pps = mTelemetryData.tmpPPS; currentBytePos++; break;

        }

        if ( currentBytePos == 32 )
            currentBytePos = 0;
    }

    private void updateReceivedData(byte[] data) {

        for (int i = 0; i < data.length; i++) {
            processRXByte( (int) data[i] & 0xff );
        }

        /*String message = //new String(data);
                "Read " + data.length + " bytes: \n" +
                        HexDump.dumpHexString(data) + "\n\n";*/
        //for (int i = 0; i < data.length; i++)
        //    message += data[i];
                //HexDump.dumpHexString(data) + "\n\n";
        //        message += "\n\n";
        //logConsole(message);
        //mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
    }

    /*static void show(Context context, UsbSerialPort port) {
        //sPort = port;
        final Intent intent = new Intent(context, MyActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }*/

    /*public boolean onKey(View v, int keyCode, KeyEvent event) {
        logConsole( "onKey: keyCode "+keyCode+" keyEvent:"+event.toString()+"\n");
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                return true;
        }
        return false;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        logConsole( "onKeyDown: keyCode "+keyCode+" keyEvent:"+event.toString()+"\n");
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                return true;
        }
        return false;
    }*/

    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        //logConsole( "MotionEvent: "+event.toString()+"\n");
        /*if ( event.getAction() == MotionEvent.ACTION_MOVE ) {
            mAxisSliders[0].setProgress( (int)(50 + event.getAxisValue(MotionEvent.AXIS_X) * 50) );
            mAxisSliders[1].setProgress( (int)(50 + event.getAxisValue(MotionEvent.AXIS_Y) * 50) );
            mAxisSliders[2].setProgress( (int)(50 + event.getAxisValue(MotionEvent.AXIS_Z) * 50) );
            mAxisSliders[3].setProgress( (int)(50 + event.getAxisValue(MotionEvent.AXIS_RZ) * 50) );
        }*/
        return true;
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        //logConsole( "KeyEvent: "+event.toString()+"\n");
        if ( event.getRepeatCount() > 0 )
            return false;
        int flag = -1;
        switch ( event.getKeyCode() ) {
            case KeyEvent.KEYCODE_DPAD_LEFT:    flag = BUTTON_D_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:   flag = BUTTON_D_RIGHT; break;
            case KeyEvent.KEYCODE_DPAD_UP:      flag = BUTTON_D_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN:    flag = BUTTON_D_DOWN; break;
            case KeyEvent.KEYCODE_BUTTON_X: flag = BUTTON_X; break;
            case KeyEvent.KEYCODE_BUTTON_Y: flag = BUTTON_Y; break;
            case KeyEvent.KEYCODE_BUTTON_A: flag = BUTTON_A; break;
            case KeyEvent.KEYCODE_BUTTON_B: flag = BUTTON_B; break;
            case KeyEvent.KEYCODE_BUTTON_L1: flag = BUTTON_L_BUMPER; break;
            case KeyEvent.KEYCODE_BUTTON_L2: flag = BUTTON_L_TRIGGER; break;
            case KeyEvent.KEYCODE_BUTTON_R1: flag = BUTTON_R_BUMPER; break;
            case KeyEvent.KEYCODE_BUTTON_R2: flag = BUTTON_R_TRIGGER; break;
            case KeyEvent.KEYCODE_BUTTON_START: flag = BUTTON_START; break;
            case KeyEvent.KEYCODE_BUTTON_SELECT: flag = BUTTON_SELECT; break;
        }
        if ( flag != -1 ) {
            if (event.getAction() == KeyEvent.ACTION_DOWN)
                mKeyFlags |= flag;
            else if (event.getAction() == KeyEvent.ACTION_UP)
                mKeyFlags &= ~flag;
        }
        updateButtonStatus();
        //sendStatus();
        return true;
    }

    void updateButtonStatus() {
        String s = "";
        if ( (mKeyFlags & BUTTON_X) != 0 ) s += "X ";
        if ( (mKeyFlags & BUTTON_Y) != 0 ) s += "Y ";
        if ( (mKeyFlags & BUTTON_A) != 0 ) s += "A ";
        if ( (mKeyFlags & BUTTON_B) != 0 ) s += "B ";
        if ( (mKeyFlags & BUTTON_D_LEFT) != 0 ) s += "DLEFT ";
        if ( (mKeyFlags & BUTTON_D_RIGHT) != 0 ) s += "DRIGHT ";
        if ( (mKeyFlags & BUTTON_D_UP) != 0 ) s += "DUP ";
        if ( (mKeyFlags & BUTTON_D_DOWN) != 0 ) s += "DDOWN ";
        if ( (mKeyFlags & BUTTON_L_BUMPER) != 0 ) s += "LBUMP ";
        if ( (mKeyFlags & BUTTON_R_BUMPER) != 0 ) s += "RBUMP ";
        if ( (mKeyFlags & BUTTON_L_TRIGGER) != 0 ) s += "LTRIG ";
        if ( (mKeyFlags & BUTTON_R_TRIGGER) != 0 ) s += "RTRIG ";
        if ( (mKeyFlags & BUTTON_START) != 0 ) s += "START ";
        if ( (mKeyFlags & BUTTON_SELECT) != 0 ) s += "SELECT ";
        logConsole(s);
    }

    private void getCameraInstance(){
        try {
            mCamera = Camera.open(); // attempt to get a Camera instance
            // get Camera parameters
            Camera.Parameters params = mCamera.getParameters();
            // set the focus mode
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            // set Camera parameters
            mCamera.setParameters(params);
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            e.printStackTrace();
        }
    }

    public void onRadioButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();
        if ( !checked )
            return;
        switch(view.getId()) {
            case R.id.radioButtonNothing:
                mFollowMode = FM_NOTHING;
                break;
            case R.id.radioButtonMe:
                mFollowMode = FM_ME;
                setMapToLocation(mLocationMe);
                break;
            case R.id.radioButtonCar:
                mFollowMode = FM_CAR;
                setMapToLocation(mLocationCar);
                break;
        }
    }

    short getPitch() { return mTelemetryData.pitch; }
    short getRoll() { return mTelemetryData.roll; }
    short getYaw() { return mTelemetryData.heading; }
    RadioData getRadioData() { return mRadioData; }
    int getPPS() { return mTelemetryData.pps; }
    int getFlags() { return mTelemetryData.flags; }
}












