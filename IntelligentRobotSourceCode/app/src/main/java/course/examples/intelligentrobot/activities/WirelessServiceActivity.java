package course.examples.intelligentrobot.activities;

import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.MediaPlayer;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import course.examples.intelligentrobot.R;

public class WirelessServiceActivity extends LifecycleLoggingActivity implements TextToSpeech.OnInitListener {
    private final String TAG = WirelessServiceActivity.class.getSimpleName();

   //USB communication
    private static UsbSerialPort sPort = null;
    private SerialInputOutputManager mSerialIoManager;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private UsbManager mUsbManager = null;
    private String buffer = new String();

   //General
    private TextView t_ConnectionStatus;
    private TextView t_serviceStatus;
    private TextView t_phoneStatus;
    private Handler m_ConnectionStatusTextHandler = new Handler();
    private Handler m_serviceStatusTextHandler = new Handler();
    private Handler m_phoneStatusTextHandler = new Handler();
    private boolean TTSready = false;
    private MediaPlayer mediaPlayer;

    //Network robot Service
    NsdServiceInfo serviceInfo = null;
    ServerSocket mServerSocket = null;
    int mLocalPort = 0;
    NsdManager.RegistrationListener mRegistrationListener = null;
    NsdManager mNsdManager = null;
    Thread serviceThread = null;
    Thread serverThread = null;
    private boolean clientForcedClose = false;
    private PrintWriter socketOutput = null;

    //TTS fields
    private int MY_DATA_CHECK_CODE = 1234;
    private TextToSpeech mTts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drive_service);


        t_ConnectionStatus = (TextView) findViewById(R.id.textView_robo_status);
        t_serviceStatus = (TextView) findViewById(R.id.textView_service_status);
        t_phoneStatus = (TextView) findViewById(R.id.textView_phoneConnection);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        Button button_refresh = (Button) findViewById(R.id.button_Refresh);
        button_refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // serial connection
                refreshDeviceList();
                openPort();
                // socket and services
                tearDown();
                initializeSocketService();
            }
        });

        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, MY_DATA_CHECK_CODE);


    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
    }

    @Override
    protected void onStart(){
        // set up serial connection
        refreshDeviceList();
        openPort();
        // setup socket and service
        initializeSocketService();
        super.onStart();
    }

    @Override
    protected void onStop() {
        // close socket and services
        tearDown();
        // close serial connection
        closePort();
        super.onStop();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_drive, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }

        return super.onOptionsItemSelected(item);
    }

    private void initializeSocketService(){
        Log.d(TAG, "initializeSocketService()");
        mNsdManager = (NsdManager) this.getSystemService(Context.NSD_SERVICE);
        serviceThread = new Thread(new DriveServiceThread() );
        serviceThread.start();
    }

    private void tearDown(){
        Log.d(TAG, "tearDown()");
        clientForcedClose = false;
        if(this.serverThread != null) {
            if(serverThread.isInterrupted()){
                // client called End of stream
            }
            else{
                this.serverThread.interrupt();
            }
            this.serverThread = null;
        }
        if(mRegistrationListener != null){
            mNsdManager.unregisterService(mRegistrationListener);
            mRegistrationListener = null;
        }
        if(mServerSocket != null){
            try {
                mServerSocket.close();
                mServerSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(this.serviceThread != null) {
            this.serviceThread.interrupt();
            this.serviceThread = null;
        }
    }

    // Service methods
    class DriveServiceThread implements Runnable {
        @Override
        public void run(){
            Log.d(TAG, "DriveServiceThread run()");
            Socket socket = null;
            try {
                mServerSocket = new ServerSocket(0);
            } catch (IOException e) {
                e.printStackTrace();
                mLocalPort = 0;
            }
            mLocalPort = mServerSocket.getLocalPort();
            setClientStatusText("Client to Connect Port " + String.valueOf(mLocalPort));
            registerDriveService(mLocalPort);
            while(!Thread.currentThread().isInterrupted()){
                Log.d(TAG, "DriveServiceThread run() while");
                try{
                    Log.d(TAG, "Waiting for client in run() before mServerSocket.accept()");
                    socket = mServerSocket.accept();
                    Log.d(TAG, "Waiting for client in run() after mServerSocket.accept()");
                    serverThread = new Thread(new ServerCommsClientThread(socket) );
                    serverThread.start();
                    setClientStatusText("Client connected!");
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    class ServerCommsClientThread implements Runnable {
        private Socket serverSocket;
        private BufferedReader input;
        public ServerCommsClientThread(Socket serverSocket) {
            Log.d(TAG, "ServerCommsclientThread()");
            this.serverSocket = serverSocket;
            try {
                this.input = new BufferedReader(new InputStreamReader(this.serverSocket.getInputStream()));
                OutputStream out = this.serverSocket.getOutputStream();
                socketOutput = new PrintWriter(out);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void run() {
            Log.d(TAG, "ServerCommsclientThread run()");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if(input.ready()) {
                        String read = input.readLine();
                        if(read.compareTo("EOS") == 0 || read==null ){
                            // end of stream
                            setClientStatusText("Client disconnected!");
                            clientForcedClose = true;
                            Thread.currentThread().interrupt();
                        }
                        else if(read!=null && read.charAt(0)=='$')
                        {
                            //speak the phrase...
                            speakTTS(read.substring(1));
                        }
                        else if(read != null) {
                            runCommand(read);
                            //setClientStatusText("Received: " + read);
                        }
                    }
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }

            if(!clientForcedClose){
                //server side close
                //server sends EOS
                send_EOS();
            }
            // close socket
            try {
                this.serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(clientForcedClose) {
                //close
                tearDown();
                // reopen
                initializeSocketService();
                clientForcedClose = false;
            }
        }
    }

    public boolean registerDriveService(int port) {
        Log.d(TAG, "registerDriveService()");
        serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("SmartBot");
        serviceInfo.setServiceType("_http._udp.");
        serviceInfo.setPort(port);

        initializeRegistrationListener();
        if (mRegistrationListener != null){
            mNsdManager.registerService(
                    serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
            return true;
        }
        else{
            setServiceStatusText("Listener Failed!");
            return false;
        }
    }

    public void initializeRegistrationListener() {
        Log.d(TAG, "initializeRegistrationListener()");
        mRegistrationListener = new NsdManager.RegistrationListener() {

            @Override
            public void onServiceRegistered(NsdServiceInfo info) {
                // Save the service name.  Android may have changed it in order to
                // resolve a conflict, so update the name you initially requested
                // with the name Android actually used.
                String mServiceName = serviceInfo.getServiceName();
                setServiceStatusText(mServiceName+ " Registered");
                Log.d(TAG, "onServiceRegistered() registered as " + mServiceName);

            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.d(TAG, "onRegistrationFailed()");

                // Registration failed!  Put debugging code here to determine why.
                String mServiceName = serviceInfo.getServiceName();
                setServiceStatusText(mServiceName + " Registration Failed!");
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
                Log.d(TAG, "onServiceUnregistered()");

                // Service has been unregistered.  This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.
                String mServiceName = serviceInfo.getServiceName();
                setServiceStatusText(mServiceName + " Unregistered Successfully!");
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.d(TAG, "onUnregistrationFailed()");
                // Unregistration failed.  Put debugging code here to determine why.
                String mServiceName = serviceInfo.getServiceName();
                setServiceStatusText(mServiceName + " Unregisteration Failed!");
            }
        };
    }

    //    Serial Comms Methods
    private final SerialInputOutputManager.Listener mSerial_IO_Listener =
        new SerialInputOutputManager.Listener() {

            @Override
            public void onRunError(Exception e) {
                Log.d(TAG, "Runner stopped.");
            }

            @Override
            public void onNewData(final byte[] data) {
                WirelessServiceActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        WirelessServiceActivity.this.updateReceivedData(data);
                    }
                });
            }
        };

    private void updateReceivedData(byte[] data) {
        String newData = new String(data, 0, data.length);
        String delimit = "\r\n";
        buffer = buffer.concat(newData);
        int endPos = buffer.indexOf(delimit);

        if (endPos >= 0) {
            String msg = buffer.substring(0, endPos);
            if (msg.contains("Ack")) {
                //show nothing
            } else {
                if(socketOutput != null){
                    socketOutput.println(msg);
                    socketOutput.flush();
                }
            }
            // remove read message from buffer
            buffer = buffer.substring(endPos + delimit.length(), buffer.length());
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mSerial_IO_Listener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void refreshDeviceList() {
        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

        final List<UsbSerialPort> result = new ArrayList<UsbSerialPort>();
        for (final UsbSerialDriver driver : drivers) {
            final List<UsbSerialPort> ports = driver.getPorts();
            Log.d(TAG, String.format("+ %s: %s port%s",
                    driver, Integer.valueOf(ports.size()), ports.size() == 1 ? "" : "s"));
            result.addAll(ports);
        }

        UsbSerialDriver driver = null;
        UsbDevice device = null;

        if (result.size() > 0) {
            sPort = result.get(0);
            driver = sPort.getDriver();
            device = driver.getDevice();
            String title = String.format("Vendor %s Product %s",
                    HexDump.toHexString((short) device.getVendorId()),
                    HexDump.toHexString((short) device.getProductId()));
            String subtitle = driver.getClass().getSimpleName();
            setConnectionStatusText(title + subtitle);
        } else {
            sPort = null;
            setConnectionStatusText("No Port Found!");
        }
    }

    private void openPort() {
        if (sPort == null) {
            setConnectionStatusText(" No serial device.");
        } else {
            mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            UsbDevice device = sPort.getDriver().getDevice();
            UsbDeviceConnection connection = mUsbManager.openDevice(device);
            if (connection == null) {
                setConnectionStatusText(" Opening device failed");
                return;
            }
            try {
                sPort.open(connection);
                sPort.setParameters(19200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                setConnectionStatusText("Error opening device: " + e.getMessage());
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            }
            setConnectionStatusText("Serial device: " + sPort.getClass().getSimpleName());
        }
        onDeviceStateChange();
    }

    private void closePort() {
        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;
        }
    }

    private void send_L(int speed) {
        if(sPort!=null){
            String cmd = "L";
            cmd = cmd.concat(String.valueOf(speed)+"\r\n");
            try {
                sPort.write(cmd.getBytes(), 100);
                Log.d(TAG, "send_L() sent left!");
            }catch(Exception ioE){
                Log.e(TAG, ioE.getMessage());
            }
        }
    }

    private void send_R(int speed) {
        if(sPort != null){
            String cmd = "R";
            cmd = cmd.concat(String.valueOf(speed)+"\r\n");
            try {
                sPort.write(cmd.getBytes(), 100);
                Log.d(TAG, "send_R() sent right!");
            }catch(Exception ioE){
                Log.e(TAG, ioE.getMessage());
            }
        }
    }

    private void send_C() {
        if(sPort != null){
            String cmd = "C" + "\r\n";
            try {
                sPort.write(cmd.getBytes(), 100);
            }catch(Exception ioE){
                Log.e(TAG, ioE.getMessage());
            }
        }
    }

    private void send_EOS(){
        if(socketOutput != null){
            socketOutput.println("EOS");
            socketOutput.flush();
        }
    }

    public void setConnectionStatusText(String txt){
        final String msg = txt;
        m_ConnectionStatusTextHandler.post(new Runnable() {
            @Override
            public void run() {
                t_ConnectionStatus.setText(msg);
            }
        });
    }


    public void setServiceStatusText(String txt){
        final String msg = txt;
        m_serviceStatusTextHandler.post(new Runnable() {
            @Override
            public void run() {
                t_serviceStatus.setText(msg);
            }
        });
    }

    public void setClientStatusText(String txt){
        final String msg = txt;
        m_phoneStatusTextHandler.post(new Runnable() {
            @Override
            public void run() {
                t_phoneStatus.setText(msg);
            }
        });
    }

    void runCommand(String cmd){
        if(cmd.contains("R")){
            String paramStr = cmd.substring(1).trim();
            if(!paramStr.isEmpty()){
                int param = Integer.parseInt(paramStr);
                send_R(param);
            }
        }
        else if(cmd.contains("L")){
            String paramStr = cmd.substring(1).trim();
            if(!paramStr.isEmpty()){
                int param = Integer.parseInt(paramStr);
                send_L(param);
            }
        }
        else if(cmd.compareTo("C")==0){
            send_C();
        }
    }

    public void speakTTS(String speakString)
    {
        if(speakString == null)
        {
            speakString = "null, null";
        }

        Log.d(TAG, "speakTTS() speakstring: " + speakString);

        if(mTts == null)
        {
            Log.d(TAG, "speakTTS() mTts null");
        }

        Log.d(TAG, "speakTTS() charAt(0) == '*': " + (speakString.charAt(0)=='*'));

        if(speakString.charAt(0)=='*')
        {
            Log.d(TAG, "speakTTS() charAt(0) == '*'");
            //   ImageView pic = (ImageView) findViewById(R.id.robotFace);
            //   pic.setImageResource(R.drawable.sadpuppy);
            puppyDance();

            try{
                mediaPlayer = MediaPlayer.create(this, R.raw.puppy);
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                      //maybe make a puppy thread??
                      //and then change the picture back?
                      //  send_L(0);
                      //  send_R(0);
                        Log.d(TAG, "speakTTS(), mediaPlayer ended");

                    }
                });
                mediaPlayer.start();
                Log.d(TAG, "speakTTS(), mediaPlayer started");
            }catch(Exception e)
            {
                Log.e(TAG,""+e);
                Log.d(TAG, "speakTTS(), error in media player: " + e);
            }

        }
        else if(TTSready)
        {
            mTts.speak(speakString, TextToSpeech.QUEUE_FLUSH, null);
          //  speechTextView.setText("Response: " + speakString);
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        if (requestCode == MY_DATA_CHECK_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // success, create the TTS instance
                Log.d(TAG, "onActivityResult() mTts created");
                mTts = new TextToSpeech(this, this);

            } else {
                // missing data, install it
                Log.d(TAG,"onActivityResult() mTts failed, installed new intent");
                Intent installIntent = new Intent();
                installIntent.setAction(
                        TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
    }

    @Override
    public void onInit(int status)
    {
        mTts.setLanguage(Locale.ENGLISH);
        TTSready = true;
    }

    public void puppyDance()
    {
        long timeOffset = 0;
        m_phoneStatusTextHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "speakTTS() move right post delayed");
                //right
                send_L(400);
                send_R(-400);
            }
        }, timeOffset+=1000);

        m_phoneStatusTextHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "speakTTS() stop post delayed");

                //left
                send_L(-400);
                send_R(400);
            }
        }, timeOffset+=1000);

        m_phoneStatusTextHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "speakTTS() stop post delayed");

                //left
                send_L(400);
                send_R(400);
            }
        }, timeOffset+=1000);

        m_phoneStatusTextHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "speakTTS() stop post delayed");

                //left
                send_L(-400);
                send_R(-400);
            }
        }, timeOffset+=1000);

        m_phoneStatusTextHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "speakTTS() stop post delayed");

                //left
                send_L(0);
                send_R(0);
            }
        }, timeOffset+=1000);
    }



}
