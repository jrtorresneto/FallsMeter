package com.example.jtorres.fallsmeter.utils;

import android.os.StrictMode;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;


public class WearableListener extends WearableListenerService {

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        if (messageEvent.getPath().equals("/message_path")) {
            final String message = new String(messageEvent.getData());
            Log.v("myTag", "Message path received on watch is: " + messageEvent.getPath());
            Log.v("myTag", "Message received on watch is: " + message);

            Log.d( "DEVELOPER", "......WOW: Send Message!");
            //run(message);
        }
        else {
            super.onMessageReceived(messageEvent);
        }
    }

    private Socket socket;
    private String hostname = "lasdpc.icmc.usp.br";
    private int port = 2023;


    public void run(String msg) {
        try {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);


            this.socket = new Socket(this.hostname, this.port);
            if (!this.socket.isClosed()) {

                DataInputStream reader = new DataInputStream(this.socket.getInputStream());
                DataOutputStream writer = new DataOutputStream(this.socket.getOutputStream());

//                Comunicator.sendString(writer, "heartSensor");
                Comunicator.sendString(writer, "logicSensor");

                Comunicator.sendString(writer, msg);

//                mStatusTextView.setText(Comunicator.recvString(reader));
                Log.d("myTag", Comunicator.recvString(reader));

            }

            this.socket.close();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
