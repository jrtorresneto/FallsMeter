package com.example.jtorres.fallsmeter;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.jtorres.fallsmeter.utils.SendToDataLayerThread;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends WearableActivity implements SensorEventListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.US);

    private Context mContext;

    private GoogleApiClient apiClient;

    private boolean isInit = false;

    private TextView mStatusTextView;

    private Button mStartButton;
    private Button mFinishButton;

    private SensorManager mSensorManager;
    private Sensor mHeartRateSensor;
    private Sensor mAccelerometerSensor;
    private Sensor mGiroscopeSensor;
    private SensorEventListener listener;

    // manipula sensores
    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final float EPSILON = 0f;
    private final float[] deltaRotationVector = new float[4];

    private static final String TAG_WEAR_FALLSMETER = "FallsMeter/Wear";
    private final String WEAR_ACCELEROMETER = "/accelerometer";
    private final String WEAR_GYROSCOPE = "/gyroscope";

    // Constants for the low-pass filters
    private float timeConstant = 0.18f;
    private float alpha = 0.9f;
    // Timestamps for the low-pass filters
    private float timestampAcc = System.nanoTime();
    private float timestampOldAcc = System.nanoTime();
    // Gravity and linear accelerations components for the
    // Wikipedia low-pass filter
    private float[] gravity = new float[] { 0, 0, 0 };
    private float[] linear_acceleration = new float[] { 0, 0, 0 };
    // Raw accelerometer data
    private float[] input = new float[] { 0, 0, 0 };
    private int count = 0;
    private long time;

    private LinearLayout mContainerView;
    private TextView mTextView;
    private TextView mClockView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setAmbientEnabled();

        mContext = MainActivity.this;

        initView();
        initListeners();
        //initSensor();

        // Build a new GoogleApiClient for the Wearable API
        apiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mContainerView = (LinearLayout) findViewById(R.id.container);
        mTextView = (TextView) findViewById(R.id.text);
        //mClockView = (TextView) findViewById(R.id.clock);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //initSensor();
    }

    private void initSensor() {

        //instanceListener();

        Thread t = new Thread() {
            public void run() {
                mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));
                mAccelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                mGiroscopeSensor= mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
                //mSensorManager.registerListener(MainActivity.this, mHeartRateSensor, SENSOR_DELAY_SECONDS * 1000); //time in miliseconds
                mSensorManager.registerListener(listener, mAccelerometerSensor, SensorManager.SENSOR_DELAY_UI);
                mSensorManager.registerListener(listener, mGiroscopeSensor, SensorManager.SENSOR_DELAY_UI);
            }
        };
        t.start();

    }

    private void initListeners() {

        instanceListener();

        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Handle();
            }
        });
        mFinishButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Handle();
            }
        });

    }

    private void instanceListener() {
        listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                Sensor sensor = event.sensor;
                Long timestamp = System.currentTimeMillis()/1000;
                long eventTimeStamp = System.nanoTime();
                //int timestamp = tsLong;
                //String nomeArq = "";
                //File folder = null;

                if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    //nomeArq = "accelerometer.csv";
                    float dt = 0;

                    timestampAcc = System.nanoTime();
                    // Find the sample period (between updates).
                    // Convert from nanoseconds to seconds
                    dt = 1 / (count / ((timestampAcc - timestampOldAcc) / 1000000000.0f));
                    count++;
                    alpha = timeConstant / (timeConstant + dt);

                    // Isolate the force of gravity with the low-pass filter.
                    gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
                    gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
                    gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];
                    // Remove the gravity contribution with the high-pass filter.
                    linear_acceleration[0] = event.values[0] - gravity[0];
                    linear_acceleration[1] = event.values[1] - gravity[1];
                    linear_acceleration[2] = event.values[2] - gravity[2];


                    try {
                        sendMessageSensor(timestamp, WEAR_ACCELEROMETER, linear_acceleration);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //lbOut.setText("Accelerometer\n" + "C: " + itemSelected + "\nT:" + timestamp + "\nX: " + event.values[0] + "\nY: " + event.values[1] + "\nZ: " + event.values[2]);
                }
                else if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                    //nomeArq = "gyroscope.csv";
                    final float dT = (eventTimeStamp - timestamp) * NS2S;

                    float axisX = event.values[0];
                    float axisY = event.values[1];
                    float axisZ = event.values[2];

                    // Calculate the angular speed of the sample
                    float omegaMagnitude = (float) Math.sqrt(axisX*axisX + axisY*axisY + axisZ*axisZ);

                    // Normalize the rotation vector if it's big enough to get the axis
                    // (that is, EPSILON should represent your maximum allowable margin of error)
                    if (omegaMagnitude > EPSILON) {
                        axisX /= omegaMagnitude;
                        axisY /= omegaMagnitude;
                        axisZ /= omegaMagnitude;
                    }

                    // Integrate around this axis with the angular speed by the timestep
                    // in order to get a delta rotation from this sample over the timestep
                    // We will convert this axis-angle representation of the delta rotation
                    // into a quaternion before turning it into the rotation matrix.
                    float thetaOverTwo = omegaMagnitude * dT / 2.0f;
                    float sinThetaOverTwo = (float)Math.sin(thetaOverTwo);
                    float cosThetaOverTwo = (float)Math.cos(thetaOverTwo);


                    deltaRotationVector[0] = sinThetaOverTwo * axisX;
                    deltaRotationVector[1] = sinThetaOverTwo * axisY;
                    deltaRotationVector[2] = sinThetaOverTwo * axisZ;
                    deltaRotationVector[3] = cosThetaOverTwo;

                    time = eventTimeStamp;
                    //SensorManager.getRotationMatrixFromVector (mRotationMatrix, deltaRotationVector);

                    try {
                        sendMessageSensor(timestamp, WEAR_GYROSCOPE, deltaRotationVector);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    //lblGyro.setText("Gyroscope\n" + "C: " + itemSelected + "\nT:" + timestamp + "\nX: " + event.values[0] + "\nY: " + event.values[1] + "\nZ: " + event.values[2]);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };
    }

    private void sendMessageSensor(long timestamp, String tag_sensor, float[] values) throws IOException {

        String message = String.valueOf(timestamp)+ "," + String.valueOf(values[0]) + "," + String.valueOf(values[1]) + "," + String.valueOf(values[2]);
        new SendToDataLayerThread(TAG_WEAR_FALLSMETER + tag_sensor , message, apiClient).start();

    }

    private void Handle() {
        if (!isInit){
            mStartButton.setBackgroundResource(R.drawable.custom_button_grey_disable);
            mFinishButton.setBackgroundResource(R.drawable.custom_selector_button_red);
            mStartButton.setClickable(false);
            mFinishButton.setClickable(true);
            //mHRMeasureList = new ArrayList<>();
            initSensor();
            //String message = "Hello Torres";
            //new SendToDataLayerThread("/message_path", message, apiClient).start();
            isInit = !isInit;
        }else{
            mStartButton.setBackgroundResource(R.drawable.custom_selector_button_blue);
            mFinishButton.setBackgroundResource(R.drawable.custom_button_grey_disable);
            mStartButton.setClickable(true);
            mFinishButton.setClickable(false);
            mSensorManager.unregisterListener(listener);
            isInit = !isInit;
        }
    }

    private void initView() {
        //mTextView = (TextView) findViewById(R.id.home_header_textview);
        mStatusTextView = (TextView) findViewById(R.id.home_status_textview);
        mStartButton = (Button) findViewById(R.id.home_start_button);
        mFinishButton = (Button) findViewById(R.id.home_finish_button);
    }

  //  @Override
   // public void onEnterAmbient(Bundle ambientDetails) {
     //   super.onEnterAmbient(ambientDetails);
       // updateDisplay();
    //}

//    @Override
  //  public void onUpdateAmbient() {
    //    super.onUpdateAmbient();
      //  updateDisplay();
    //}

//    @Override
 //   public void onExitAmbient() {
   //     updateDisplay();
    //    super.onExitAmbient();
    //}

//    private void updateDisplay() {
 //       if (isAmbient()) {
  //          mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
   //         mTextView.setTextColor(getResources().getColor(android.R.color.white));
    //        mClockView.setVisibility(View.VISIBLE);

//            mClockView.setText(AMBIENT_DATE_FORMAT.format(new Date()));
  //      } else {
    //        mContainerView.setBackground(null);
      //      mTextView.setTextColor(getResources().getColor(android.R.color.black));
        //    mClockView.setVisibility(View.GONE);
        //}
   // }

    // Connect to the data layer when the Activity starts
    @Override
    protected void onStart() {
        super.onStart();
        apiClient.connect();
    }

    // Send a message when the data layer connection is successful.
    @Override
    public void onConnected(Bundle connectionHint) {

        //String message = "Hello wearable\n Via the data layer\n startao";
        //new SendToDataLayerThread("/message_path", message, apiClient).start();
        mStatusTextView.setText("Comunicação iniciada...");
    }

    // Disconnect from the data layer when the Activity stops
    @Override
    protected void onStop() {
     //  if (null != apiClient && apiClient.isConnected()) {
       //     apiClient.disconnect();
     //}
        super.onStop();
    }

    @Override
    public void onConnectionSuspended(int i) {
        mStatusTextView.setText("Conexão perdida...");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e( "DEVELOPER", "...Failed to connect, with result: " + connectionResult);
        mStatusTextView.setText("Conectando...");

        // if the api client existed, we terminate it
        if (apiClient != null && apiClient.isConnected()) {
            //Plus.AccountApi.clearDefaultAccount(apiClient);
            apiClient.disconnect();
        }
        // build new api client to avoid problems reusing it
        // Build a new GoogleApiClient for the Wearable API
        apiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        apiClient.connect();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
