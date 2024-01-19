package com.example.birlestirme;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
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
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;

public class MainActivity2 extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_PICK_IMAGE = 2;
    private Interpreter tflite;
    private TensorImage inputImageBuffer;
    private TensorBuffer[] outputBuffers;
    private Spinner spinnerTarget;
    private String[] toLanguages = { "English", "Turkish", "Arabic", "German", "French", "Spanish", "Czech", "Russian",
            "Hindi", "Portuguese"};
    private Map<Integer, String> labelMap = new HashMap<>();
    private Uri photoUri;
    private String currentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        Button ikinciActivity = findViewById(R.id.metinCevirisi);
        ikinciActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity2.this,CeviriEkrani.class);
                startActivity(intent);
            }
        });
        Button gorselCeviri = findViewById(R.id.gorselCeviri);
        gorselCeviri.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity2.this,GorselCeviriEkrani.class);
                startActivity(intent);
            }
        });

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



        inputImageBuffer = new TensorImage(DataType.UINT8);
        inputImageBuffer.load(Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888));

        outputBuffers = new TensorBuffer[4];
        for (int i = 0; i < 4; i++) {
            outputBuffers[i] = TensorBuffer.createFixedSize(tflite.getOutputTensor(i).shape(), DataType.FLOAT32);
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

    private void showMediaOptionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity2.this);
        builder.setTitle("Choose an option")
                .setItems(new CharSequence[]{"Take Photo", "Choose from Gallery"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                                    File photoFile = null;
                                    try {
                                        photoFile = createImageFile();
                                    } catch (IOException ex) {
                                        // Error occurred while creating the File
                                        ex.printStackTrace();
                                    }
                                    // Continue only if the File was successfully created
                                    if (photoFile != null) {
                                        photoUri = FileProvider.getUriForFile(MainActivity2.this,
                                                "com.example.birlestirme.fileprovider",
                                                photoFile);
                                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                                        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                                    }
                                }
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

    /*private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }*/

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        // Get the directory for the user's public pictures directory.
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        // Create the File object
        File imageFile = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = imageFile.getAbsolutePath();

        Log.d("CreateImageFile", "Image file created successfully: " + currentPhotoPath);

        return imageFile;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Bitmap bitmap = null;
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), photoUri);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else if (requestCode == REQUEST_PICK_IMAGE) {
                Uri selectedImage = data.getData();
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (bitmap != null) {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                preprocessAndRunModel(bitmap);
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