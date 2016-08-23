/*
   For step-by-step instructions on connecting your Android application to this backend module,
   see "App Engine Java Servlet Module" template documentation at
   https://github.com/GoogleCloudPlatform/gradle-appengine-templates/tree/master/HelloWorld
*/

package com.example.jtorres.myapplication.backend;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.servlet.http.*;

public class MyServlet extends HttpServlet {

    private static final String TAG_FALLSMETER = "FallsMeter/Wear/";
    private boolean isCreated = false;

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("text/plain");
        resp.getWriter().println("Please use the form to POST to this url");
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String msg = req.getParameter("data");
        resp.setContentType("text/plain");
        if(msg == null) {
            resp.getWriter().println("Please enter a name");
        }
        resp.getWriter().println("Data: " + msg);
        writeFile(msg);
    }

    private void writeFile(String msg) throws IOException {

        String nomeArq  = "";
        File folder = null;

        String[] s = msg.split(",");
        if(s[4].equals("111")){
            nomeArq = "wear_accelerecometer.cvs";
        } else if(s[4].equals("111")){
            nomeArq = "wear_gyroscope.cvs";
        }

        if(!isCreated){
            // dirApp = Environment.getExternalStoragePublicDirectory(Environment. DIRECTORY_DOWNLOADS) + "/"+timestamp+"/";

            //dirApp = Environment.getExternalStorageDirectory() + "/" + appName + "/"+timestamp+"/";
            //folder = new File(Environment.getExternalStorageDirectory() + "/"+timestamp+"/");
            folder = new File(TAG_FALLSMETER +s[0]+"/");
            if (!folder.exists()) {
                folder.mkdirs();
                isCreated = true;
            }
        }



        File fileExt = new File(TAG_FALLSMETER +s[0]+"/", nomeArq);
        fileExt.getParentFile().mkdirs();

        FileOutputStream fosExt = null;
        fosExt = new FileOutputStream(fileExt,true);

        //String line = String.valueOf(timestamp)+ "," + String.valueOf(values[0]) + "," + String.valueOf(values[1]) + "," + String.valueOf(values[2]) + "," + itemSelected + "\n";
        fosExt.write( msg.getBytes() );
        fosExt.flush();
        fosExt.close();
    }
}
