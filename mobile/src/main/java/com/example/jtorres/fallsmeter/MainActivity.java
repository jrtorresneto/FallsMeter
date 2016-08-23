package com.example.jtorres.fallsmeter;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.multidex.MultiDex;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements DataApi.DataListener, MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks{

    private GoogleApiClient apiClient;

    private TextView mWearAccelerometer;
    private TextView mWearGyroscope;

    private static final String TAG_WEAR_FALLSMETER = "FallsMeter/Wear";
    private static final String TAG_MOBI_FALLSMETER = "FallsMeter/Mobi";
    private final String WEAR_ACCELEROMETER = "/accelerometer";
    private final String WEAR_GYROSCOPE = "/gyroscope";
    
    // Módulo Mobile
    Sensor accelerometer;
    Sensor giroscope;
    private SensorManager sm;
    TextView lbOut;
    TextView lblGyro;
    //String appName = "FallsMeter";
    private Button btStart;
    private Button btStop;
    private String array_spinner[];
    private Spinner spin1;
    private String itemSelected;

    //manipulacao de arquivos
    private File dir;
    private String nomeDir;
    private String dirApp;
    private String dirAppWear;
    private boolean isCreated = false;
    private boolean isCreatedWearDiretory = false;


    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final float EPSILON = 0f;
    private final float[] deltaRotationVector = new float[4];
    private long time;

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

    //gerenciador de sensores
    private SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            Sensor sensor = event.sensor;
            Long timestamp = System.currentTimeMillis()/1000;
            long eventTimeStamp = System.nanoTime();
            //int timestamp = tsLong;
            String nomeArq = "";
            File folder = null;
            if(!isCreated){

                // dirApp = Environment.getExternalStoragePublicDirectory(Environment. DIRECTORY_DOWNLOADS) + "/"+timestamp+"/";

                dirApp = Environment.getExternalStorageDirectory() + "/" + TAG_MOBI_FALLSMETER + "/"+timestamp+"/";
                //folder = new File(Environment.getExternalStorageDirectory() + "/"+timestamp+"/");
                folder = new File(Environment.getExternalStorageDirectory() + "/" + TAG_MOBI_FALLSMETER + "/"+timestamp+"/");
                if (!folder.exists()) {
                    folder.mkdirs();
                    isCreated = true;
                }
            }

            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                nomeArq = "accelerometer.csv";
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
                    writeFile(timestamp, nomeArq, linear_acceleration, dirApp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                lbOut.setText("Mob Accelerometer " + "C: " + itemSelected + " T:" + timestamp + " X: " + event.values[0] + " Y: " + event.values[1] + " Z: " + event.values[2]);
            }
            else if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                nomeArq = "gyroscope.csv";
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
                    writeFile(timestamp, nomeArq, deltaRotationVector, dirApp);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                lblGyro.setText("Mob Gyroscope" + " C: " + itemSelected + " T:" + timestamp + " X: " + event.values[0] + " Y: " + event.values[1] + " Z: " + event.values[2]);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
  ///      fab.setOnClickListener(new View.OnClickListener() {
     //       @Override
       //     public void onClick(View view) {
         //       Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
           //             .setAction("Action", null).show();
            //}
        //});

        initSensor();
        initView();
        initListener();

        apiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();
    }

    private void initListener() {
        spin1 = (Spinner) findViewById(R.id.spinner1);
        ArrayAdapter adapter = ArrayAdapter.createFromResource(this, R.array.classe_arrays, R.layout.spinner_layout);
        spin1.setAdapter(adapter);

        spin1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                itemSelected = parent.getItemAtPosition(pos).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void initSensor() {
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        giroscope= sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    private void initView() {
        mWearAccelerometer = (TextView) findViewById(R.id.lb_wear_acc);
        mWearGyroscope = (TextView) findViewById(R.id.lb_wear_gir);

        lbOut = (TextView) findViewById(R.id.lbl_Out);
        lblGyro = (TextView) findViewById(R.id.lb_Gyro);

        btStart = (Button) findViewById(R.id.bt_start);
        btStart.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                sm.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI);
                sm.registerListener(listener, giroscope, SensorManager.SENSOR_DELAY_UI);
            }
        });

        btStop = (Button) findViewById(R.id.bt_stop);
        btStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sm.unregisterListener(listener);
            }
        });
    }

    private void writeFile(long timestamp, String nomeArq, float[] values, String diretory) throws IOException {
        File fileExt = new File(diretory, nomeArq);
        fileExt.getParentFile().mkdirs();

        FileOutputStream fosExt = null;
        fosExt = new FileOutputStream(fileExt,true);

        String line = String.valueOf(timestamp)+ "," + String.valueOf(values[0]) + "," + String.valueOf(values[1]) + "," + String.valueOf(values[2]) + "," + itemSelected + "\n";
        fosExt.write( line.getBytes() );
        fosExt.flush();
        fosExt.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
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

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart(){
        super.onStart();
        Log.d( "DEVELOPER", "......Mobile: Start! ");
        apiClient.connect();
    }

    //@Override
    //protected void onStop(){
     //   Log.d( "DEVELOPER", "......Mobile: Stop! ");
      //  if (apiClient != null && apiClient.isConnected()){
       //     apiClient.disconnect();
        //}
        //super.onStop();
    //}

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        Log.d( "DEVELOPER", "......Mobile-Wear: Connected! ");
        mWearAccelerometer.setText("Wear Listenner...");
        mWearGyroscope.setText("Wear Listenner...");
        Wearable.MessageApi.addListener(apiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

    }

    @Override
    public void onMessageReceived(final MessageEvent message) {
        //mStatusTextView.setText("Recebeu mensagem " + new String(message.getData()));

        String data = null;
        //long timestamp = 0;
        final float[] valuesSensor = new float[] { 0, 0, 0, 0 };

        String[] arrayData = new String(message.getData()).split(",");

        long timestamp = Long.parseLong(arrayData[0].trim());
        valuesSensor[0] = Float.parseFloat(arrayData[0]);
        valuesSensor[1] = Float.parseFloat(arrayData[1]);
        valuesSensor[2] = Float.parseFloat(arrayData[2]);
        valuesSensor[3] = Float.parseFloat(arrayData[3]);


        createDiretory(TAG_WEAR_FALLSMETER, timestamp);
        final long finalTimestamp = timestamp;

        if(message.getPath().equalsIgnoreCase(TAG_WEAR_FALLSMETER + WEAR_ACCELEROMETER)) {
            final String nameWearFileAcc = "accelerometer.csv";
            data = "111,";
            new ServletPostAsyncTask().execute(new Pair<Context, String>(this, new String(message.getData()).concat(",111")));
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    Log.d("DEVELOPER", "......Wear: WOW! " + new String(message.getData()).concat(",111"));
                    try {
                        writeFile(finalTimestamp, nameWearFileAcc, valuesSensor, dirAppWear);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            mWearAccelerometer.setText("Wear accelerometer: \n" + new String(message.getData()));

        }else if(message.getPath().equalsIgnoreCase(TAG_WEAR_FALLSMETER + WEAR_GYROSCOPE)){
            final String nameWearFileGir = "gyroscope.csv";
            data = "222,";
            new ServletPostAsyncTask().execute(new Pair<Context, String>(this, new String(message.getData()).concat(",222")));
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    Log.d("DEVELOPER", "......Wear: WOW! " + new String(message.getData()).concat(",222"));
                    try {
                        writeFile(finalTimestamp, nameWearFileGir, valuesSensor, dirAppWear);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            mWearGyroscope.setText("Wear gyroscope: \n" + new String(message.getData()));

        }

    }

    private void createDiretory(String tagWearFallsMeter, long timestamp) {
        File folder = null;
        if(!isCreatedWearDiretory){

            // dirApp = Environment.getExternalStoragePublicDirectory(Environment. DIRECTORY_DOWNLOADS) + "/"+timestamp+"/";
            dirAppWear = Environment.getExternalStorageDirectory() + "/" + tagWearFallsMeter + "/" + timestamp + "/";
            //folder = new File(Environment.getExternalStorageDirectory() + "/"+timestamp+"/");
            folder = new File(Environment.getExternalStorageDirectory() + "/" + tagWearFallsMeter + "/" + timestamp + "/");
            if (!folder.exists()) {
                folder.mkdirs();
                isCreatedWearDiretory = true;
            }
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        MultiDex.install(this);
    }
}
