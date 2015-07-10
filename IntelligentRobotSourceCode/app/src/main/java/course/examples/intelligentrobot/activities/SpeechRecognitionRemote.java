
/* ====================================================================
 * Copyright (c) 2014 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package course.examples.intelligentrobot.activities;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

import course.examples.intelligentrobot.R;
import course.examples.intelligentrobot.helperClasses.decisionMaker;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

public class SpeechRecognitionRemote extends LifecycleLoggingActivity implements
        RecognitionListener {
    private String TAG = SpeechRecognitionRemote.class.getSimpleName();

    //Networking client fields
    private NsdManager mNsdManager = null;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdManager.ResolveListener mResolveListener = null;
    private NsdServiceInfo mService;
    private Socket mSocket = null;
    private String mServiceName = "SmartBot";
    private PrintWriter mOutput = null;
    private android.os.Handler mConnectionHandler =  new android.os.Handler();
    private final int CONNECTION_INTERVAL = 250; // msec
    private Runnable mConnectionCheck = null;
    private Object mConnectionLock = new Object();
    private boolean mConnectionDone = false;

    private Thread clientCommsThread = null;
    private boolean serverForcedClose = false;

    //General fields
    private Button recordButton;
    private Button button_refresh;
    private TextView speechTextView;
    private static final String grammarSearch = "digits";
    private static final String navSearch = "nav";
    private decisionMaker responseCalculator;
    private TextView t_ConnectionStatus;
    private TextView t_leftEnc_Text;
    private TextView t_rightEnc_Text;
    private android.os.Handler m_StatusTextHandler = new android.os.Handler();
    private android.os.Handler m_LeftEncTextHandler = new android.os.Handler();
    private android.os.Handler m_RightEncTextHandler = new android.os.Handler();
    private boolean networkReady = false;
    private boolean speechRecognizerReady = false;
    private android.os.Handler UIHandler = new Handler();
    private FileOutputStream outputStream = null;
    private String mode = "talk";



    //Sphinx fields
    private SpeechRecognizer recognizer;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        setupUI();
        setupDecisionMaker();
        setupSphinx();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(recognizer!= null)
        {
            recognizer.cancel();
            recognizer.shutdown();

        }
    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr();
        speechTextView.setText(text);

        /*
        //TODO ???
        if (text.equals(KEYPHRASE))
            switchSearch(MENU_SEARCH);
        else if (text.equals(DIGITS_SEARCH))
            switchSearch(DIGITS_SEARCH);
        else if (text.equals(PHONE_SEARCH))
            switchSearch(PHONE_SEARCH);
        else if (text.equals(FORECAST_SEARCH))
            switchSearch(FORECAST_SEARCH);
        //else
           // ((TextView) findViewById(R.id.result_text)).setText(text);
           */
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {

        String speakString = "null";
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            speechTextView.setText(text);

            text = text.toLowerCase();
            Log.d(TAG, "onResult(): " + text);

            if(!mode.equals("talk")) {
                //TODO make right/left commands reality...
                Log.d(TAG, "onResult() inside navigation: " + text);

                    if(text.contains("right"))
                    {
                        send_R(-400);
                        send_L(400);
                        Log.d(TAG, "onResult() commanded right");
                    }
                    else if(text.contains("left")) {
                        send_R(400);
                        send_L(-400);
                        Log.d(TAG, "onResult() commanded left");
                    }
                    else if(text.contains("forward"))
                    {
                        send_R(400);
                        send_L(400);
                    }
                    else if(text.contains("back")) {

                        send_R(-400);
                        send_L(-400);
                    }
                    else if(text.contains("picture")) {

                        //todo
                    }
                    else if(text.contains("stop")) {
                        send_R(0);
                        send_L(0);
                    }
                switchSearch(navSearch);
            }
            else
            {
                speakString = responseCalculator.getStringTTS(text);
                sendTTS(speakString);
            }
        }

        //speechTextView.setText("Response: " + speakString);

    }

    @Override
    public void onBeginningOfSpeech() {
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech");
        recognizer.stop();
        if(mode.equals("talk"))
        {
            recordButton.setEnabled(true);
        }
        //speechTextView.setText();
    }

    private void switchSearch(String searchName) {

        recognizer.stop();
        recognizer.startListening(searchName);

        // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
      /*  if (searchName.equals(KWS_SEARCH))
            recognizer.startListening(searchName);
        else
            recognizer.startListening(searchName, 10000);
            */

        //String caption = getResources().getString(captions.get(searchName));
        //((TextView) findViewById(R.id.caption_text)).setText(caption);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "robotV3.dic"))

                        // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .setRawLogDir(assetsDir)

                        // Threshold to tune for keyphrase to balance between false alarms and misses
                .setKeywordThreshold(1e-45f)

                        // Use context-independent phonetic search, context-dependent is too slow for mobile
                .setBoolean("-allphone_ci", true)

                .getRecognizer();
        recognizer.addListener(this);

        /** In your application you might not need to add all those searches.
         * They are added here for demonstration. You can leave just one.
         */


        // Create grammar-based search for selection between demos
        File languageModel = new File(assetsDir, "robotV4.dmp");
        recognizer.addNgramSearch(grammarSearch, languageModel);

        // Create grammar-based search for selection between demos
        File navigationModel = new File(assetsDir, "nav.dmp");
        recognizer.addNgramSearch(navSearch, navigationModel);

    }

    @Override
    public void onError(Exception error) {
        //((TextView) findViewById(R.id.caption_text)).setText(error.getMessage());
    }

    @Override
    public void onTimeout() {
        //switchSearch(KWS_SEARCH);
    }

    /*
        GUI record pushed
     */
    public void recordPushed(View v)
    {
        if(mode.equals("nav"))
        {
            recordButton.setText("Stop navigation mode");
            mode = "nav1";
            switchSearch(navSearch);
        }
        else if(mode.equals("nav1"))
        {
            send_L(0);
            send_R(0);
            recordButton.setEnabled(false);
            recordButton.setText("Record");
            mode = "talk";
            sendTTS(responseCalculator.getStringTTS(null));
            UIHandler.postDelayed(new Runnable(){

                @Override
                public void run()
                {
                    recordButton.setEnabled(true);
                }
            },500);

        }
        else
        {
            recordButton.setEnabled(false);
            switchSearch(grammarSearch);
        }
    }

    public void sendTTS(String speakString)
    {
        //send message w/ $ as first character

        if(speakString.charAt(0) == '1')
        {
            //TODO SWTICH TO NAV STATE
            recordButton.setText("Start navigation mode");
            mode = "nav";
            speakString = speakString.substring(1);
        }

        if(mOutput!=null){
            mOutput.println("$"+speakString);
            mOutput.flush();
        }
    }



    private void setupSphinx()
    {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(SpeechRecognitionRemote.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    speechTextView.setText("Failed to init recognizer " + result);
                } else {
                    speechRecognizerReady = true;
                    if(speechRecognizerReady && networkReady)
                    {
                        UIHandler.post(new Runnable() {
                            @Override
                            public void run()
                            {
                                sendTTS(responseCalculator.getStringTTS(null));
                                recordButton.setEnabled(true);
                                speechRecognizerReady = false;
                            }
                        });
                    }
                }
            }
        }.execute();
    }

    private void setupUI()
    {
        setContentView(R.layout.activity_speech_recognition);
        recordButton = (Button) findViewById(R.id.button);
        speechTextView = (TextView) findViewById(R.id.textView);
        button_refresh = (Button) findViewById(R.id.refresh);
        button_refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                send_EOS();
                tearDown();
                initialiseClient();
            }
        });

        t_ConnectionStatus = (TextView) findViewById(R.id.textView_status);
        t_leftEnc_Text = (TextView) findViewById(R.id.textView_leftEnc);
        t_rightEnc_Text = (TextView) findViewById(R.id.textView_rightEnc);
    }

    @Override
    protected void onStart(){
        initialiseClient();
        super.onStart();
    }

    @Override
    protected void onStop() {
        tearDown();
        super.onStop();
    }

    private void initialiseClient(){
        Log.d(TAG, "initialiseClient()");
        initializeConnectionCheck();
        mNsdManager = (NsdManager) this.getSystemService(Context.NSD_SERVICE);
        initializeDiscoveryListener();
        mNsdManager.discoverServices(
                "_http._udp.", NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    private void tearDown(){
        Log.d(TAG, "tearDown()");
        cancelConnectionCheck();

        if(clientCommsThread!=null){
            if(clientCommsThread.isInterrupted()){
                // server called End of stream
            }
            else{
                this.clientCommsThread.interrupt();
            }
            clientCommsThread = null;
        }
        if (mResolveListener != null) {
            mResolveListener = null;
        }
        if (mDiscoveryListener != null){
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
            mDiscoveryListener = null;
        }
    }

    public void initializeDiscoveryListener() {
        Log.d(TAG, "initialiseDiscoveryListener()");
        // Instantiate a new DiscoveryListener
        mDiscoveryListener = new NsdManager.DiscoveryListener() {

            //  Called as soon as service discovery begins.
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                // A service was found!  Do something with it.
                Log.d(TAG, "Service discovery success" + service);
                if (!service.getServiceType().equals("_http._udp.")) {
                    // Service type is the string containing the protocol and
                    // transport layer for this service.
                    Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
                    setStatusText("Unknown Service Type: " + service.getServiceType());
                } else if (service.getServiceName().contains(mServiceName)){
                    initializeResolveListener();
                    mNsdManager.resolveService(service, mResolveListener);
                    setStatusText("Resolving service: " + service.getServiceType());
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.d(TAG, "onServiceLost()");
                Log.e(TAG, "Service lost" + service);
                setStatusText("Service lost: " + service);
                tearDown();
                initialiseClient();
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
                Log.d(TAG, "onDiscoveryStopped()");

            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
                setStatusText("Discovery failed!");
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }
        };
    }

    public void initializeResolveListener() {
        Log.d(TAG, "initializeResolveListener()");
        mResolveListener = new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Called when the resolve fails.  Use the error code to debug.
                Log.e(TAG, "Resolve failed" + errorCode);
                setStatusText("Resolve failed");
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.e(TAG, "Resolve Succeeded. " + serviceInfo);
                if (serviceInfo.getServiceName().contains(mServiceName)) {
                    mService = serviceInfo;
                    int port = mService.getPort();
                    InetAddress host = mService.getHost();
                    try {
                        mSocket = new Socket(host, port);
                        clientCommsThread = new Thread(new ClientCommsThread(mSocket));
                        clientCommsThread.start();
                        setStatusText("Resolved:" + mService.getServiceName() +
                                "\n Host:" + host.toString() + " Port:" + Integer.toString(port));
                        mConnectionCheck.run();


                    } catch (IOException e) {
                        e.printStackTrace();
                        mSocket = null;
                    }
                }
                return;
            }
        };
    }

    class ClientCommsThread implements Runnable {
        private Socket clientSocket;
        private BufferedReader input;

        public ClientCommsThread(Socket clientSocket){
            Log.d(TAG, "ClientCommsThread()");
            this.clientSocket = clientSocket;
            try {
                this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
                OutputStream out = this.clientSocket.getOutputStream();
                mOutput = new PrintWriter(out);

                networkReady = true;
                if(speechRecognizerReady && networkReady)
                {
                    //when ready to record...
                    UIHandler.post(new Runnable() {
                        @Override
                        public void run()
                        {
                            sendTTS(responseCalculator.getStringTTS(null));
                            recordButton.setEnabled(true);
                            speechRecognizerReady = false;
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void run(){
            Log.d(TAG, "ClientCommsThread run()");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if(input.ready()) {
                        String read = input.readLine();
                        if(read.compareTo("EOS") == 0 || read==null ){
                            // end of stream
                            setStatusText("Server disconnected!");
                            serverForcedClose = true;
                            Thread.currentThread().interrupt();
                        }
                        else if(read != null) {
                            receivedCommand(read);
                        }
                    }
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }

            UIHandler.post(new Runnable() {
                @Override
                public void run()
                {
                    recordButton.setEnabled(false);
                }
            });

            if(!serverForcedClose){
                //client side close
                //client sends EOS
                send_EOS();
            }
            // close socket
            try {
                this.clientSocket.shutdownInput();
                this.clientSocket.shutdownOutput();
                this.clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(serverForcedClose) {
                //close
                tearDown();
                // reopen
                initialiseClient();
                serverForcedClose = false;
            }
        }
    }

    private void initializeConnectionCheck(){
        synchronized (mConnectionLock) {
            mConnectionDone = false;
        }
        mConnectionCheck =
                new Runnable() {
                    @Override
                    public void run() {
                        send_C();
                        synchronized (mConnectionLock) {
                            if(!mConnectionDone)
                                mConnectionHandler.postDelayed(this, CONNECTION_INTERVAL);
                        }
                    }
                };
    }

    private void cancelConnectionCheck(){
        synchronized (mConnectionLock) {
            mConnectionDone = true;
        }
    }

    public void receivedCommand(String msg){
        final String cmd = msg;
        if (msg.contains("R")) {
            m_RightEncTextHandler.post(new Runnable() {
                @Override
                public void run() {
                    t_rightEnc_Text.setText(cmd);
                    t_rightEnc_Text.invalidate();
                }
            });
        }
        if (msg.contains("L")) {
            m_LeftEncTextHandler.post(new Runnable() {
                @Override
                public void run() {
                    t_leftEnc_Text.setText(cmd);
                    t_leftEnc_Text.invalidate();
                }
            });
        }
    }

    public void send_EOS(){
        if(mOutput!=null){
            mOutput.println("EOS");
            mOutput.flush();
        }
    }

    public void send_L(int spd){
        if(mOutput!=null){
            String cmd = "L" + String.valueOf(spd);
            mOutput.println(cmd);
            mOutput.flush();
            Log.d(TAG, "send_L() wrote");

        }
    }

    public void send_R(int spd){
        if(mOutput!=null){
            String cmd = "R" + String.valueOf(spd);
            mOutput.println(cmd);
            mOutput.flush();
            Log.d(TAG, "send_R() wrote");

        }
    }

    public void send_C(){
        if(mOutput!=null){
            String cmd = "C";
            mOutput.println(cmd);
            mOutput.flush();
        }
    }

    public void setStatusText(String txt){
        final String msg = txt;
        m_StatusTextHandler.post(new Runnable() {
            @Override
            public void run() {
                t_ConnectionStatus.setText(msg);
            }
        });
    }

    public void setupDecisionMaker()
    {

        File direct = getFilesDir();
        File file = new File(direct, "people2");
        boolean deleted = file.delete();

        // WRITE: Create/append a file in the Internal Storage
        String fileName = "people3";
        String content = "hello world";
        String dir = getFilesDir().getAbsolutePath();
        Log.d(TAG, "setupDecisionMaker(), files path: "+dir);

        try {
            outputStream = openFileOutput(fileName, Context.MODE_APPEND);
            //  outputStream.write(content.getBytes());
            //  outputStream.close();

            Log.d(TAG, "setupDecisionMaker(), outputStream created");
        } catch (Exception e) {
            e.printStackTrace();
        }

        //READ:
        BufferedReader bufferedReader = getBufferedReader(fileName);

        //initialize decisionMaker
        responseCalculator = new decisionMaker(outputStream, bufferedReader, fileName, this);

    }

    public BufferedReader getBufferedReader(String filename)
    {
        BufferedReader buffer = null;
        try{
            InputStream inputStream = openFileInput(filename);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            buffer = new BufferedReader(inputStreamReader);
         //   Log.d(TAG, "setupDecisionMaker(), inputStream created");

            //inputStream.close();

        }catch (Exception e){
            e.printStackTrace();
        }

        return buffer;
    }
}