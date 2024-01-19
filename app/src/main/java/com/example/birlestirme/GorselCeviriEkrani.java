package com.example.birlestirme;


import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GorselCeviriEkrani extends AppCompatActivity {

    Button galeriyiAc;
    ImageView imageView;
    private static final int REQUEST_PICK_IMAGE = 2;
    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private Interpreter tflite;
    private TensorImage inputImageBuffer;
    private TensorBuffer[] outputBuffers;
    private Map<Integer, String> labelMap = new HashMap<>();
    private Uri photoUri;
    private String currentPhotoPath;
    private Spinner spinnerTarget;
    private String[] toLanguages = { "English", "Turkish", "Arabic", "German", "French", "Spanish", "Czech", "Russian",
            "Hindi", "Portuguese"};

    private static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 1;
    private TextToSpeech tts;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gorsel_ceviri_ekrani);

        imageView = findViewById(R.id.imageView);

        galeriyiAc = findViewById(R.id.galeriyiAc);
        galeriyiAc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMediaOptionsDialog();
            }
        });

        ImageView imageView = findViewById(R.id.geriTusu);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed(); // Bu, bir önceki sayfaya dönmek için kullanılan bir metoddur
            }
        });

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(new Locale(getLanguageCode(spinnerTarget.getSelectedItem().toString())));

                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "This Language is not supported");
                    } else {
                        tts.speak("TextToSpeech is initialized and the language is supported.", TextToSpeech.QUEUE_FLUSH, null);
                    }
                } else {
                    Log.e("TTS", "Initialization Failed!");
                }
            }
        });

        requestPermissions();

        try {
            tflite = new Interpreter(loadModelFile());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            loadLabels();
        } catch (IOException e) {
            e.printStackTrace();
        }


        spinnerTarget = findViewById(R.id.spinnerTarget);
        ArrayAdapter toAdapter = new ArrayAdapter(this,R.layout.spinner_item,toLanguages);
        toAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTarget.setAdapter(toAdapter);

        spinnerTarget.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int result = tts.setLanguage(new Locale(getLanguageCode(spinnerTarget.getSelectedItem().toString())));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "This Language is not supported");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });





        inputImageBuffer = new TensorImage(DataType.UINT8);
        inputImageBuffer.load(Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888));

        outputBuffers = new TensorBuffer[4];
        for (int i = 0; i < 4; i++) {
            outputBuffers[i] = TensorBuffer.createFixedSize(tflite.getOutputTensor(i).shape(), DataType.FLOAT32);
        }
    }


    private void showMediaOptionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(GorselCeviriEkrani.this);
        builder.setTitle("Choose an option")
                .setItems(new CharSequence[]{"Take Photo", "Choose from Gallery"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                dispatchTakePictureIntent();
                                break;
                            case 1:
                                Intent pickPhotoIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                                startActivityForResult(pickPhotoIntent, REQUEST_PICK_IMAGE);
                                break;
                        }
                    }
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_ID_MULTIPLE_PERMISSIONS: {
                Map<String, Integer> perms = new HashMap<>();
                perms.put(Manifest.permission.CAMERA, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.READ_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                if (grantResults.length > 0) {
                    for (int i = 0; i < permissions.length; i++)
                        perms.put(permissions[i], grantResults[i]);
                    if (perms.get(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            && perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                            && perms.get(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        // All permissions are granted
                    } else {
                        // Some permissions are not granted. Ask again.
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)
                                || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                            new AlertDialog.Builder(this)
                                    .setMessage("Need permissions")
                                    .setPositiveButton("Allow", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            requestPermissions();
                                        }
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .create()
                                    .show();
                        } else {
                            Toast.makeText(this, "Go to settings and enable permissions", Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }
        }
    }

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(permission);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), REQUEST_ID_MULTIPLE_PERMISSIONS);
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private Bitmap resizeBitmap(Bitmap bitmap, int width, int height) {
        return Bitmap.createScaledBitmap(bitmap, width, height, false);
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1 * 320 * 320 * 3 * 4);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[320 * 320];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < 320; ++i) {
            for (int j = 0; j < 320; ++j) {
                final int val = intValues[pixel++];
                byteBuffer.put((byte) ((val >> 16) & 0xFF));
                byteBuffer.put((byte) ((val >> 8) & 0xFF));
                byteBuffer.put((byte) (val & 0xFF));
            }
        }
        return byteBuffer;
    }

    // Call this method when you want to take a photo
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                // Handle the error
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.birlestirme.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    // This method creates a file for the photo
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Bitmap bitmap = null;
            if (requestCode == REQUEST_PICK_IMAGE) {
                Uri selectedImage = data.getData();
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (requestCode == REQUEST_IMAGE_CAPTURE) {
                File file = new File(currentPhotoPath);
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            }

            if (bitmap != null) {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                preprocessAndRunModel(bitmap);
            }
        }

        if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImageUri);
                    imageView.setImageBitmap(bitmap);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void preprocessAndRunModel(Bitmap bitmap) {
        // Convert the bitmap to RGB and resize it
        Bitmap rgbBitmap = convertBitmapToRGB(bitmap);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(rgbBitmap, 320, 320, true);

        // Load the resized bitmap into the inputImageBuffer
        inputImageBuffer.load(resizedBitmap);

        Map<Integer, Object> outputMap = new HashMap<>();
        for (int i = 0; i < 4; i++) {
            outputMap.put(i, outputBuffers[i].getBuffer().rewind());
        }

        // Run the model
        tflite.runForMultipleInputsOutputs(new Object[]{inputImageBuffer.getBuffer()}, outputMap);

        float[] outputLocations = outputBuffers[0].getFloatArray();
        float[] outputClasses = outputBuffers[1].getFloatArray();
        float[] outputScores = outputBuffers[2].getFloatArray();
        float[] numDetections = outputBuffers[3].getFloatArray();

        int numDetectionsInt = Math.round(numDetections[0]);
        Map<String, Integer> objectFrequencyMap = new HashMap<>();
        for (int i = 0; i < numDetectionsInt; i++) {
            int classLabel = Math.round(outputClasses[i]);
            String objectName = labelMap.get(classLabel);
            if (objectName != null) {
                Log.d("Object Detection", "Detected object: " + objectName);
                objectFrequencyMap.put(objectName, objectFrequencyMap.getOrDefault(objectName, 0) + 1);
            } else {
                Log.d("Object Detection", "No mapping found for class label: " + classLabel);
            }
        }

        // Find the most frequent object
        String mostFrequentObject = null;
        int maxFrequency = 0;
        for (Map.Entry<String, Integer> entry : objectFrequencyMap.entrySet()) {
            if (entry.getValue() > maxFrequency) {
                mostFrequentObject = entry.getKey();
                maxFrequency = entry.getValue();
            }
        }

        final String finalMostFrequentObject = mostFrequentObject;
        if (finalMostFrequentObject != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final String translatedObjectName;
                    if (getLanguageCode(spinnerTarget.getSelectedItem().toString()).equals("en")) {
                        translatedObjectName = finalMostFrequentObject;
                    } else {
                        translatedObjectName = translate(finalMostFrequentObject);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            displayTranslatedText(translatedObjectName);
                        }
                    });
                }
            }).start();
        }
    }
    private Bitmap convertBitmapToRGB(Bitmap bitmap) {
        Bitmap rgbBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(rgbBitmap);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[] {
                1, 0, 0, 0, 0,
                0, 1, 0, 0, 0,
                0, 0, 1, 0, 0,
                0, 0, 0, 0, 255
        })));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return rgbBitmap;
    }

    private String translate(final String text) {
        String targetLanguage = getLanguageCode(spinnerTarget.getSelectedItem().toString());
        TranslateOptions options = TranslateOptions.newBuilder()
                .setApiKey("AIzaSyD-SAT330kx-iD6WA824-Q-KhErgKvEhHM")
                .build();
        Translate translate = options.getService();
        final Translation translation =
                translate.translate(text,
                        Translate.TranslateOption.sourceLanguage("en"),
                        Translate.TranslateOption.targetLanguage(targetLanguage));
        return translation.getTranslatedText();
    }

    private void displayTranslatedText(String translatedText) {
        TextView textView = (TextView) findViewById(R.id.translatedText);
        textView.setText(translatedText);
        tts.speak(translatedText, TextToSpeech.QUEUE_FLUSH, null);

    }

    private void loadLabels() throws IOException {
        try (InputStream is = getAssets().open("labels.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                labelMap.put(index++, line);
            }
        }
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