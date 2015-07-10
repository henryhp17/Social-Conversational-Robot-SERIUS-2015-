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

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Locale;

import course.examples.intelligentrobot.R;
import course.examples.intelligentrobot.helperClasses.decisionMaker;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

public class SpeechRecognitionLocal extends Activity implements
        RecognitionListener, TextToSpeech.OnInitListener {
    private String TAG = SpeechRecognitionLocal.class.getSimpleName();

    //Networking client fields
    private NsdManager mNsdManager = null;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdManager.ResolveListener mResolveListener = null;
    private NsdServiceInfo mService;
    private Socket mSocket = null;


    private Button recordButton;
    private TextView speechTextView;
    private static final String grammarSearch = "digits";
    private decisionMaker responseCalculator = new decisionMaker();

    private int MY_DATA_CHECK_CODE = 1234;
    private TextToSpeech mTts;

    /* Named searches allow to quickly reconfigure the decoder */
   /* private static final String KWS_SEARCH = "wakeup";
    private static final String FORECAST_SEARCH = "forecast";
    private static final String DIGITS_SEARCH = "digits";
    private static final String PHONE_SEARCH = "phones";
    private static final String MENU_SEARCH = "menu";
*/
    /* Keyword we are looking for to activate menu */
    //  private static final String KEYPHRASE = "oh mighty computer";

    private SpeechRecognizer recognizer;
    //  private HashMap<String, Integer> captions;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        setContentView(R.layout.activity_speech_recognition);

        //For UI
        recordButton = (Button) findViewById(R.id.button);
        speechTextView = (TextView) findViewById(R.id.textView);


        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async tas
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(SpeechRecognitionLocal.this);
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
                    Intent checkIntent = new Intent();
                    checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
                    startActivityForResult(checkIntent, MY_DATA_CHECK_CODE);
                }
            }
        }.execute();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        recognizer.cancel();
        recognizer.shutdown();
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

            speakString = responseCalculator.getStringTTS(text);
        }

        speakTTS(speakString);
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
        recordButton.setEnabled(true);
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
                .setDictionary(new File(assetsDir, "robotV2.dic"))

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
        File languageModel = new File(assetsDir, "robotV2.dmp");
        recognizer.addNgramSearch(grammarSearch, languageModel);

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
        recordButton.setEnabled(false);
        switchSearch(grammarSearch);
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

        if(speakString.charAt(0)=='*')
        {
            //TODO action for when puppy noise happens... ??
            setContentView(R.layout.puppy);
            try{
                MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.puppy);
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        setContentView(R.layout.activity_speech_recognition);
                    }
                });
                mediaPlayer.start();
                Log.d(TAG, "speakTTS(), mediaPlayer started");
            }catch(Exception e)
            {
                Log.e(TAG,""+e);
                Log.d(TAG, "speakTTS(), error: " + e);
            }

        }
        else
        {
            mTts.speak(speakString, TextToSpeech.QUEUE_FLUSH, null);
            speechTextView.setText("Response: " + speakString);
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
        mTts.speak("this is a test", TextToSpeech.QUEUE_FLUSH,null); //TODO WHY THIS HERE?  DOES IT SPEAK?

        //when ready to record...
        speakTTS(responseCalculator.getStringTTS(null));
        recordButton.setEnabled(true);
    }

}
