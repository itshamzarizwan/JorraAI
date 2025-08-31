package com.example.jorraai;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 200;
    RecyclerView recyclerView;
    ImageAdapter adapter;
    List<Item> hairstyleList;
    TextView supportedFormatText;
    Button selectFileBtn, submitBtn;
    ImageView imagePreview, uploadIcon;
    boolean isImageSelected = false;

    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    private Bitmap selfieBitmap;
    private int selectedHairstyleResId = -1;


    private final String BACKEND_URL = " https://4fa3e4863c5f.ngrok-free.app/apply-hairstyle/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);


        recyclerView = findViewById(R.id.recyclerViewHairstyles);
        selectFileBtn = findViewById(R.id.selectFileBtn);
        submitBtn = findViewById(R.id.submitformbtn);
        imagePreview = findViewById(R.id.imagePreview);
        uploadIcon = findViewById(R.id.uploadIcon);
        supportedFormatText = findViewById(R.id.supportedFormatText);

        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        setupHairstyles();
        setupLaunchers();

        selectFileBtn.setOnClickListener(v -> {
            if (!isImageSelected) showImagePickerDialog();
            else removeImage();
        });

        submitBtn.setOnClickListener(v -> handleSubmit());

    }

    private void setupHairstyles() {
        hairstyleList = new ArrayList<>();
        hairstyleList.add(new Item("Artistic Braids", R.drawable.artisticbraids));
        hairstyleList.add(new Item("Bubble Braids", R.drawable.bubblebraids));
        hairstyleList.add(new Item("Butterfly Locs", R.drawable.butterflylocs));
        hairstyleList.add(new Item("Feed in Braids", R.drawable.feedinbraids));
        hairstyleList.add(new Item("French Curly Braids", R.drawable.frenchcurlybraids));
        hairstyleList.add(new Item("Fulani Braids", R.drawable.fulanibraids));
        hairstyleList.add(new Item("Goddess Locs", R.drawable.goddesslocs));
        hairstyleList.add(new Item("Hair Takedown", R.drawable.hairtakedown));
        hairstyleList.add(new Item("Large Boho Braids", R.drawable.largebohobraids));
        hairstyleList.add(new Item("Large Braid", R.drawable.largebraid));
        hairstyleList.add(new Item("Locs Retwist and Style", R.drawable.locsretwistandstyle));
        hairstyleList.add(new Item("Medium Boho Braids", R.drawable.mediumbohobraids));
        hairstyleList.add(new Item("Medium Braids", R.drawable.mediumbraids));
        hairstyleList.add(new Item("Natural Hair Cornrow", R.drawable.naturalhaircornrow));
        hairstyleList.add(new Item("Nu Locs", R.drawable.nulocs));
        hairstyleList.add(new Item("River Locs", R.drawable.riverlocs));
        hairstyleList.add(new Item("Shampoo and Conditioner", R.drawable.shampooandcondition));
        hairstyleList.add(new Item("Short Twist w/ Brazilian Wool", R.drawable.shorttwistbrazilianwool));
        hairstyleList.add(new Item("Silk Press", R.drawable.silkpress));
        hairstyleList.add(new Item("Small Braids", R.drawable.smallbraids));
        hairstyleList.add(new Item("Small Boho Braids", R.drawable.smallbohobraids));
        hairstyleList.add(new Item("Steam Treatment", R.drawable.steamtreament));
        hairstyleList.add(new Item("Tanavoho Style", R.drawable.tanavohostyle));
        hairstyleList.add(new Item("Venus Braids", R.drawable.venusbraids));
        hairstyleList.add(new Item("Weave Install Revamp", R.drawable.weaveinstallrevamp));

        adapter = new ImageAdapter(hairstyleList, item -> {
            selectedHairstyleResId = item.getImageRes();
            Toast.makeText(this, "Selected: " + item.getName(), Toast.LENGTH_SHORT).show();
        });
        recyclerView.setAdapter(adapter);
    }

    private void setupLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri selectedImage = result.getData().getData();
                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                            showImage(bitmap);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Bitmap photo = (Bitmap) result.getData().getExtras().get("data");
                        if (photo != null) showImage(photo);
                    }
                }
        );
    }

    private void handleSubmit() {
        if (!isImageSelected) {
            Toast.makeText(this, "Please upload a subject photo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedHairstyleResId == -1) {
            Toast.makeText(this, "Please select a hairstyle", Toast.LENGTH_SHORT).show();
            return;
        }
        sendToBackend(selfieBitmap, selectedHairstyleResId);
    }

    private void sendToBackend(Bitmap selfieBitmap, int hairstyleResId) {
        submitBtn.setEnabled(false);
        submitBtn.setText("Uploading...");

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();

        Bitmap resizedSelfie = resizeBitmap(selfieBitmap, 512);
        Bitmap hairstyleBitmap = BitmapFactory.decodeResource(getResources(), hairstyleResId);
        Bitmap resizedHairstyle = resizeBitmap(hairstyleBitmap, 512);

        byte[] selfieBytes = bitmapToByteArray(resizedSelfie);
        byte[] hairstyleBytes = bitmapToByteArray(resizedHairstyle);

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("selfie", "selfie.jpg", RequestBody.create(selfieBytes, MediaType.parse("image/jpeg")))
                .addFormDataPart("hairstyle", "hairstyle.jpg", RequestBody.create(hairstyleBytes, MediaType.parse("image/jpeg")))
                .build();

        Request request = new Request.Builder()
                .url(BACKEND_URL)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(UploadActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    submitBtn.setEnabled(true);
                    submitBtn.setText("Submit");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                byte[] imageBytes = response.body() != null ? response.body().bytes() : null;
                runOnUiThread(() -> {
                    submitBtn.setEnabled(true);
                    submitBtn.setText("Submit");

                    if (!response.isSuccessful() || imageBytes == null) {
                        Toast.makeText(UploadActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String imagePath = saveImageToCache(imageBytes);
                    if (imagePath != null) {
                        Intent intent = new Intent(UploadActivity.this, ResultActivity.class);
                        intent.putExtra("imagePath", imagePath);
                        startActivity(intent);
                    } else {
                        Toast.makeText(UploadActivity.this, "Failed to save image", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showImagePickerDialog() {
        String[] options = {"Camera", "Gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Select Option")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Camera selected
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                                != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                        } else {
                            cameraLauncher.launch(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
                        }
                    } else {
                        // Gallery selected
                        galleryLauncher.launch(new Intent(Intent.ACTION_PICK,
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
                    }
                })
                .show();
    }


    private void showImage(Bitmap bitmap) {
        selfieBitmap = bitmap;
        imagePreview.setImageBitmap(bitmap);
        imagePreview.setVisibility(View.VISIBLE);
        uploadIcon.setVisibility(View.GONE);
        supportedFormatText.setVisibility(View.GONE);
        selectFileBtn.setText("Remove");
        isImageSelected = true;
    }

    private void removeImage() {
        selfieBitmap = null;
        imagePreview.setImageBitmap(null);
        imagePreview.setVisibility(View.GONE);
        uploadIcon.setVisibility(View.VISIBLE);
        supportedFormatText.setVisibility(View.VISIBLE);
        selectFileBtn.setText("Select File");
        isImageSelected = false;
    }
    @Override

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cameraLauncher.launch(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
            } else {
                Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bitmap resizeBitmap(Bitmap original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();
        float ratio = (float) width / height;

        int newWidth = maxSize;
        int newHeight = maxSize;

        if (ratio > 1) newHeight = (int) (maxSize / ratio);
        else newWidth = (int) (maxSize * ratio);

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }

    private byte[] bitmapToByteArray(Bitmap bitmap) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos);
        return bos.toByteArray();
    }

    private String saveImageToCache(byte[] imageBytes) {
        try {
            File cacheDir = getCacheDir();
            File file = new File(cacheDir, "result.jpg");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(imageBytes);
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
