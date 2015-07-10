package course.examples.intelligentrobot.helperClasses;

import android.util.Log;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;

import course.examples.intelligentrobot.activities.SpeechRecognitionRemote;

/**
 * Created by brianrhindress on 6/17/15.
 */
public class decisionMaker {

    private static final String TAG = decisionMaker.class.getSimpleName();
    private int state;
    private String color = null;
    private String name = null;
    private FileOutputStream outputStream;
    private BufferedReader bufferedReader;
    private String fileName = null;
    private SpeechRecognitionRemote parent;
    private boolean nav = false;

    public decisionMaker(FileOutputStream stream, BufferedReader buffer, String file, SpeechRecognitionRemote context)
    {
        state = 0;
        outputStream = stream;
        bufferedReader = buffer;
        fileName = file;
        parent = context;
    }

    public decisionMaker()
    {
        this(null,null,null,null);
    }

    public String getStringTTS(String spokenWords)
    {
        String toTTS = "Error in choosing phrase";
        String[] split = null;
        if(spokenWords != null)
        {
            spokenWords = spokenWords.toLowerCase();
            split = spokenWords.split(" ");
        }

        if(spokenWords != null && spokenWords.contains("restart"))
        {
            restart();
        }
        else if(spokenWords !=null && spokenWords.contains("bye"))
        {
            state = 5;
        }

        Log.d(TAG, "getStringTTS() spokenWords: " + spokenWords);

        switch(state) {
            case 0: //initial greeting
                toTTS = "Hi, my name is Intro-bot.  What’s your name?";
                state = 1;
                break;
            case 1: //after asking name
                for (int i = 0; i < split.length; i++) {
                    if (!split[i].equals("i") &&
                            !split[i].equals("am") &&
                            !split[i].equals("i’m") &&
                            !split[i].equals("i'm") &&
                            !split[i].equals("name") &&
                            !split[i].equals("is") &&
                            !split[i].equals("my")){

                        Log.d(TAG, "getStringTTS() split[i]: " + split[i]);
                        Log.d(TAG, "getStringTTS() !split[i].equals(my): " + !split[i].equals("my"));
                        Log.d(TAG, "getStringTTS() !split[i].equals(i’m): " + !split[i].equals("i’m"));

                        name = split[i];
                        break;
                    }
                }
                if(searchName()==true)
                {
                    toTTS = name + "!  Great to see you again, you " + color + " lover.  How’s it hanging my man?";
                    state = 4;
                }
                else
                {
                    toTTS = "Nice to meet you, " + name + ".  What is your favorite color?";
                    state = 2;
                }

                break;
            case 2:
                for (int i = 0; i < split.length; i++) {
                    if (!split[i].equals("favorite") &&
                            !split[i].equals("color") &&
                            !split[i].equals("is") &&
                            !split[i].equals("my") &&
                            !split[i].equals("i") &&
                            !split[i].equals("like") &&
                            !split[i].equals("the"))
                    {
                        color = split[i];
                        break;
                    }
                }
                createProfile();
                toTTS = color + " is a terrible color!";
                state = 3;
                //break; go straight into base case
            case 3:
                toTTS = toTTS + " So, what can I help you with today?";
                state = 4;
                break;
            case 4:
                int i;
                for (i = 0; i < split.length; i++) {

                    if (split[i].equals("sad") ||
                            split[i].equals("puppy")){
                        //TODO puppy state
                        toTTS = "*puppy state";
                        state = 4;
                        break;
                    }
                    else if (split[i].equals("wing") ||
                            split[i].equals("alone") ||
                            split[i].equals("lonely")){
                        //TODO wing man state

                        toTTS = "Need a wing man? ";
                        String friend = findFriend();
                        if(friend == null)
                            toTTS = toTTS + "Haaaave ya met Ted?";
                        else
                            toTTS = toTTS + friend;
                        state = 4;
                        break;
                    }
                    else if (split[i].equals("lost") ||
                            split[i].equals("find")){
                        //TODO navigation state
                        toTTS = "1Navigation mode activated!";
                        nav = true;
                        state = 6;
                        break;
                    }
                    else if (split[i].equals("bye") ||
                            split[i].equals("goodbye")){
                        //TODO goodbye state
                        toTTS = "Until next time, " + name + ". See ya later skater!";
                        break;
                    }
                }

                if(i==split.length && nav != true) {
                    toTTS = "I’m sorry, I didn’t catch that, " + name + ".  Can you say something intelligible?";
                    state = 4;
                }
                break;
            case 5:
                toTTS = "Until next time, " + name + ". See ya later skater!";
                break;
            case 6: //navigation state
                toTTS = "I hope you found what you were looking for.  Need anything else?";
                state = 4;
                nav = false;
                break;
        }
        return toTTS;
    }

    private boolean searchName()
    {
        String readIn;
        Log.d(TAG, "searchName() start");
        try{
            while(bufferedReader.ready())
            {
                Log.d(TAG, "searchName() bufferedReader ready");
                readIn = bufferedReader.readLine();
                Log.d(TAG, "searchName() read from file: " + readIn);

                if(readIn.contains(name))
               {
                   Log.d(TAG, "searchName() found profile!");
                   String[] profile  = readIn.split(" ");
                   color = profile[1];
                   bufferedReader.close();
                   return true;
               }
            }
        }catch(Exception e)
        {
            e.printStackTrace();
            Log.d(TAG, "searchName() caught exception: " + e);

        }
        return false;
    }

    private void createProfile()
    {
        Log.d(TAG, "createProfile() start");
        try {
            if(color == null)
            {
                color = "null";
            }
            outputStream.write((name + " " + color + "\n").getBytes());
            outputStream.close();
            Log.d(TAG, "createProfile() wrote & closed file: " + (name + " " + color) + " AKA:" + (name + " " + color).getBytes());
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
            Log.d(TAG, "createProfile() caught exception: " + e);

        }
    }

    private String findFriend() {
        BufferedReader buff = parent.getBufferedReader(fileName);
        String readIn;
        Log.d(TAG, "searchName() start");
        try {
            while (buff.ready()) {
                Log.d(TAG, "searchName() bufferedReader ready");
                readIn = buff.readLine();
                Log.d(TAG, "searchName() read from file: " + readIn);

                if (!readIn.contains(name)) {
                    Log.d(TAG, "searchName() found another profile!");
                    String[] profile = readIn.split(" ");
                    bufferedReader.close();
                    return "Let me introduce you to "+ profile[0]+".  He's tall, tan, and beautiful.  He even likes " +
                            "the color "+ profile[1];
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "searchName() caught exception: " + e);
        }

        return null;

    }

    private void restart()
    {
        bufferedReader = parent.getBufferedReader(fileName);
        name = null;
        color = null;
        state = 0;
    }
}


