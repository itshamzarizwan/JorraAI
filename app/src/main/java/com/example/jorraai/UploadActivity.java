package com.example.jorraai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View; // ← added so we can hide the try-ons label
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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

// If you added Google sign-in earlier, this signs out Firebase too (optional but recommended)
import com.google.firebase.auth.FirebaseAuth;

public class UploadActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 200;
    RecyclerView recyclerView;
    ImageAdapter adapter;
    List<Item> hairstyleList;
    TextView supportedFormatText, tryOnsText;
    Button selectFileBtn, submitBtn, logoutBtn;   // ← added logoutBtn
    ImageView imagePreview, uploadIcon;
    boolean isImageSelected = false;

    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    private Bitmap selfieBitmap;
    private int selectedHairstyleResId = -1;

    private static final String BASE_URL = "https://try-on.docwyn.com";
    private static final String APPLY_URL = BASE_URL + "/apply-hairstyle/"; // trailing slash matters
    private static final String ME_URL = BASE_URL + "/me";

    private OkHttpClient http;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        // Build a client that always injects Authorization if present
        http = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .addNetworkInterceptor(chain -> {
                    Request original = chain.request();
                    String t = SessionManager.getToken(UploadActivity.this);
                    if (t != null && !t.isEmpty()) {
                        Request withAuth = original.newBuilder()
                                .header("Authorization", "Bearer " + t)
                                .build();
                        return chain.proceed(withAuth);
                    }
                    return chain.proceed(original);
                })
                .build();

        recyclerView = findViewById(R.id.recyclerViewHairstyles);
        selectFileBtn = findViewById(R.id.selectFileBtn);
        submitBtn = findViewById(R.id.submitformbtn);
        logoutBtn = findViewById(R.id.logoutBtn);           // ← findViewById
        imagePreview = findViewById(R.id.imagePreview);
        uploadIcon = findViewById(R.id.uploadIcon);
        supportedFormatText = findViewById(R.id.supportedFormatText);
        //tryOnsText = findViewById(R.id.tryOnsText);

        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        setupHairstyles();
        setupLaunchers();

        // ===== Disable try-ons quota UI =====
        // int tryOns = SessionManager.getTryOns(this);
        // updateTryOnsLabel(tryOns);
        if (tryOnsText != null) tryOnsText.setVisibility(View.GONE);
        // ====================================

        selectFileBtn.setOnClickListener(v -> {
            if (!isImageSelected) showImagePickerDialog();
            else removeImage();
        });

        submitBtn.setOnClickListener(v -> handleSubmit());

        // ← logout handler: clear session + (optional) Firebase sign out + go to LoginActivity
        if (logoutBtn != null) {
            logoutBtn.setOnClickListener(v -> {
                try {
                    FirebaseAuth.getInstance().signOut(); // safe even if not signed in
                } catch (Throwable ignored) {}
                SessionManager.clear(UploadActivity.this);

                Toast.makeText(UploadActivity.this, "Logged out", Toast.LENGTH_SHORT).show();

                Intent i = new Intent(UploadActivity.this, LoginActivity.class);
                // Clear back stack so user can’t navigate back into Upload after logout
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            });
        }
    }

    private void updateTryOnsLabel(int tryOns) {
        // No-op while quota is disabled
        if (tryOnsText != null) {
            tryOnsText.setVisibility(View.GONE);
        }
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

        // ===== Disable try-ons quota check =====
        // int tryOns = SessionManager.getTryOns(this);
        // if (tryOns <= 0) {
        //     Toast.makeText(this, "No try-ons left.", Toast.LENGTH_LONG).show();
        //     return;
        // }
        // =======================================

        sendToBackend(selfieBitmap, selectedHairstyleResId);
    }

    private void sendToBackend(Bitmap selfieBitmap, int hairstyleResId) {
        submitBtn.setEnabled(false);
        submitBtn.setText("Uploading...");

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

        String token = SessionManager.getToken(this);
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        Request request = new Request.Builder()
                .url(APPLY_URL) // keep trailing slash
                .header("Authorization", "Bearer " + token)
                .post(requestBody)
                .build();

        http.newCall(request).enqueue(new Callback() {
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

                    if (response.code() == 403) {
                        // With unlimited try-ons this shouldn't happen, but keep a friendly message.
                        Toast.makeText(UploadActivity.this, "Access denied (403).", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!response.isSuccessful() || imageBytes == null) {
                        Toast.makeText(UploadActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // ===== Remove decrement and label update =====
                    // int current = SessionManager.getTryOns(UploadActivity.this);
                    // int updated = Math.max(0, current - 1);
                    // SessionManager.setTryOns(UploadActivity.this, updated);
                    // updateTryOnsLabel(updated);
                    // ============================================

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
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                                != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                        } else {
                            cameraLauncher.launch(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
                        }
                    } else {
                        galleryLauncher.launch(new Intent(Intent.ACTION_PICK,
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
                    }
                })
                .show();
    }

    private void showImage(Bitmap bitmap) {
        selfieBitmap = bitmap;
        imagePreview.setImageBitmap(bitmap);
        imagePreview.setVisibility(ImageView.VISIBLE);
        uploadIcon.setVisibility(ImageView.GONE);
        supportedFormatText.setVisibility(TextView.GONE);
        selectFileBtn.setText("Remove");
        isImageSelected = true;
    }

    private void removeImage() {
        selfieBitmap = null;
        imagePreview.setImageBitmap(null);
        imagePreview.setVisibility(ImageView.GONE);
        uploadIcon.setVisibility(ImageView.VISIBLE);
        supportedFormatText.setVisibility(TextView.VISIBLE);
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
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
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
