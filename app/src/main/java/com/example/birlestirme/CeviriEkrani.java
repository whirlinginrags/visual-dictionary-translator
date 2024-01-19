package com.example.birlestirme;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;

import java.util.List;
import java.util.Locale;

public class CeviriEkrani extends AppCompatActivity {

    private static final int SPEECH_REQUEST_CODE = 0;
    private static final int RECORD_AUDIO_REQUEST_CODE = 1;

    private Spinner spinnerLeft, spinnerRight;
    private TextInputEditText editSource;
    private TextView cevirilmis;
    private String[] fromLanguages = { "English", "Turkish", "Arabic", "German", "French", "Spanish", "Czech", "Russian",
            "Hindi", "Portuguese"};
    private String[] toLanguages = { "Turkish", "English", "Arabic", "German", "French", "Spanish", "Czech", "Russian",
            "Hindi", "Portuguese"};

    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ceviri_ekrani);

        spinnerLeft = findViewById(R.id.spinnerLeft);
        spinnerRight = findViewById(R.id.spinnerRight);
        editSource = findViewById(R.id.editSource);
        cevirilmis = findViewById(R.id.cevirilmis);

        ImageView micButton = findViewById(R.id.micButton);
        micButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //  RECORD_AUDIO izni verilmiş mi diye kontrol et
                if (ContextCompat.checkSelfPermission(CeviriEkrani.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    // eğer izin verilmemişse daha önce,şimdi izin iste
                    ActivityCompat.requestPermissions(CeviriEkrani.this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_REQUEST_CODE);
                } else {
                    // eğer izin verilmişse Ses algılamaya başla
                    startSpeechRecognition();
                }
            }
        });

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    tts.setLanguage(new Locale(getLanguageCode(spinnerRight.getSelectedItem().toString())));
                }
            }
        });

        ArrayAdapter fromAdapter = new ArrayAdapter(this, R.layout.spinner_item, fromLanguages);
        fromAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLeft.setAdapter(fromAdapter);

        spinnerRight = findViewById(R.id.spinnerRight);
        ArrayAdapter toAdapter = new ArrayAdapter(this,R.layout.spinner_item,toLanguages);
        toAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRight.setAdapter(toAdapter);

        spinnerRight.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int result = tts.setLanguage(new Locale(getLanguageCode(spinnerRight.getSelectedItem().toString())));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "This Language is not supported");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        findViewById(R.id.cevirButonu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                translateUserInput();
            }
        });

        ImageView imageView = findViewById(R.id.geriTusu);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed(); // Bu, bir önceki sayfaya dönmek için kullanılan bir metoddur
            }
        });


    }
    private void translateUserInput() {
        if (editSource.getText() != null && !editSource.getText().toString().isEmpty()) {
            translate();
        } else {
            // Kullanıcı tarafından metin girilmediği durumda bir uyarı .
            Toast.makeText(CeviriEkrani.this, "Lütfen bir metin girin.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);
            editSource.setText(spokenText);
            translate();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition();
            }
        }
    }

    private void translate() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                TranslateOptions options = TranslateOptions.newBuilder()
                        .setApiKey("AIzaSyD-SAT330kx-iD6WA824-Q-KhErgKvEhHM")
                        .build();
                Translate translate = options.getService();
                final Translation translation =
                        translate.translate(editSource.getText().toString(),
                                Translate.TranslateOption.sourceLanguage(getLanguageCode(spinnerLeft.getSelectedItem().toString())),
                                Translate.TranslateOption.targetLanguage(getLanguageCode(spinnerRight.getSelectedItem().toString())));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        cevirilmis.setText(translation.getTranslatedText());
                        tts.speak(translation.getTranslatedText(), TextToSpeech.QUEUE_FLUSH, null);
                    }
                });
            }
        }).start();
    }

    private String getLanguageCode(String language) {
        switch (language) {
            case "English":
                return "en";
            case "Turkish":
                return "tr";
            case "Arabic":
                return "ar";
            case "German":
                return "de";
            case "French":
                return "fr";
            case "Spanish":
                return "es";
            case "Czech":
                return "cs";
            case "Russian":
                return "ru";
            case "Hindi":
                return "hi";
            case "Portuguese":
                return "pt";
            default:
                return "en";
        }
    }
}