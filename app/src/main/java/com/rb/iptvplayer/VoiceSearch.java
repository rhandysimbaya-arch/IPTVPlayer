package com.rb.iptvplayer;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.ArrayList;

/** Microphone search using the phone's built-in speech recognizer (no extra permission needed). */
public class VoiceSearch {

    public interface Callback {
        void onResult(String text);
    }

    private final ComponentActivity activity;
    private final ActivityResultLauncher<Intent> launcher;

    /** Create this while the activity is being set up (before it is started). */
    public VoiceSearch(ComponentActivity activity, final Callback cb) {
        this.activity = activity;
        this.launcher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        ArrayList<String> list =
                                result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (list != null && !list.isEmpty()) cb.onResult(list.get(0).trim());
                    }
                });
    }

    public void start() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a channel name");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            launcher.launch(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "Voice search isn't available on this device",
                    Toast.LENGTH_LONG).show();
        }
    }
}
