package com.example.jorraai;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;

public class ResultActivity extends AppCompatActivity {

    ImageView resultImage, backBtn;
    Button downloadBtn;
    Bitmap resultBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        resultImage = findViewById(R.id.resultImage);
        downloadBtn = findViewById(R.id.downloadBtn);
        backBtn = findViewById(R.id.backbtn);  // Back button

        // Get the image file path from the intent
        String imagePath = getIntent().getStringExtra("imagePath");

        if (imagePath != null) {
            // Decode the image from file
            resultBitmap = BitmapFactory.decodeFile(imagePath);
            resultImage.setImageBitmap(resultBitmap);
        } else {
            Toast.makeText(this, "No image received", Toast.LENGTH_SHORT).show();
        }

        // Back button click: go back to UploadActivity
        backBtn.setOnClickListener(v -> finish());

        // Download button click
        downloadBtn.setOnClickListener(v -> {
            if (resultBitmap != null) {
                saveImageToGallery(this, resultBitmap);
            } else {
                Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveImageToGallery(Context context, Bitmap bitmap) {
        String filename = "JORRA_AI_" + System.currentTimeMillis() + ".jpg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JorraAI"); // Save in Pictures/JorraAI folder

        try {
            // Step 1: insert into MediaStore
            android.net.Uri uri = context.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                // Step 2: open output stream and write the bitmap
                OutputStream out = context.getContentResolver().openOutputStream(uri);
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    out.close();
                    Toast.makeText(context, "Image saved to gallery!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, "Failed to get output stream", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(context, "Failed to create new MediaStore record", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show();
        }
    }

}
