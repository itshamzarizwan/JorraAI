package com.example.jorraai;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// Google / Firebase
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    // UI
    private EditText emailEditText, passwordEditText;
    private MaterialButton loginButton, registerButton, googleButton;
    private ProgressBar progressBar;

    // Networking
    private static final String BASE_URL = "https://try-on.docwyn.com";
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    // Firebase / Google
    private static final String TAG = "LoginActivity-Google";
    private FirebaseAuth mAuth;
    private GoogleSignInClient googleClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Log.d("LoginActivity-Google", "webClientId=" + getString(R.string.default_web_client_id));

        int playCode = com.google.android.gms.common.GoogleApiAvailability
                .getInstance().isGooglePlayServicesAvailable(this);
        Log.d("LoginActivity-Google", "PlayServices status=" + playCode); // should be SUCCESS(0)

        // ---- find views ----
        emailEditText    = findViewById(R.id.email);
        passwordEditText = findViewById(R.id.password);
        loginButton      = findViewById(R.id.loginBtn);
        registerButton   = findViewById(R.id.registerBtn);
        googleButton     = findViewById(R.id.googleSignInBtn);
        progressBar      = findViewById(R.id.progressBar);

        // ---- email/password listeners (unchanged) ----
        loginButton.setOnClickListener(v -> authenticate());
        registerButton.setOnClickListener(v -> signup());

        // ---- Firebase / Google sign-in setup ----
        mAuth = FirebaseAuth.getInstance();

        FirebaseOptions opts = FirebaseApp.getInstance().getOptions();
        Log.d("FB-CHECK",
                "projectId=" + opts.getProjectId()
                        + " | applicationId(gmp)=" + opts.getApplicationId()
                        + " | apiKey=" + opts.getApiKey());

        Log.d("FB-CHECK", "default_web_client_id=" + getString(R.string.default_web_client_id));

        // default_web_client_id must come from google-services.json (OAuth client type "Web")
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handleGoogleResult
        );

        // IMPORTANT: This does not read email/password at all.
        googleButton.setOnClickListener(v -> {
            setLoading(true);
            // signOut first so the chooser always appears (helps avoid “canceled” edge cases)
            googleClient.signOut().addOnCompleteListener(t -> {
                Intent signInIntent = googleClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            });
        });
    }

    // If user already has a valid backend token, skip login
    @Override
    protected void onStart() {
        super.onStart();
        String token = SessionManager.getToken(this);
        if (token != null && !token.isEmpty()) validateToken(token);
    }

    // ----------------- Email / Password flows (unchanged) -----------------

    private void setLoading(boolean loading) {
        if (progressBar != null) progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loginButton != null) loginButton.setEnabled(!loading);
        if (registerButton != null) registerButton.setEnabled(!loading);
        if (googleButton != null) googleButton.setEnabled(!loading);
        if (emailEditText != null) emailEditText.setEnabled(!loading);
        if (passwordEditText != null) passwordEditText.setEnabled(!loading);
    }

    private void signup() {
        String username = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(username)) { emailEditText.setError("Username required"); return; }
        if (TextUtils.isEmpty(password)) { passwordEditText.setError("Password required"); return; }

        setLoading(true);

        RequestBody form = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build();

        Request req = new Request.Builder()
                .url(BASE_URL + "/signup")
                .post(form)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> { setLoading(false); toast("Signup failed: " + e.getMessage()); });
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> setLoading(false));

                if (!response.isSuccessful()) {
                    runOnUiThread(() -> toast("Signup error: " + response.code()));
                    return;
                }
                try {
                    JSONObject json = new JSONObject(body);
                    String token = json.getString("token");
                    int tryOns = json.getInt("try_ons");
                    SessionManager.saveSession(LoginActivity.this, token, tryOns);
                    runOnUiThread(() -> { toast("Signup successful! Try-ons: " + tryOns); openUpload(); });
                } catch (Exception ex) {
                    runOnUiThread(() -> toast("Parse error"));
                }
            }
        });
    }

    private void authenticate() {
        String username = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(username)) { emailEditText.setError("Username required"); return; }
        if (TextUtils.isEmpty(password)) { passwordEditText.setError("Password required"); return; }

        setLoading(true);

        RequestBody form = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build();

        Request req = new Request.Builder()
                .url(BASE_URL + "/authenticate")
                .post(form)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> { setLoading(false); toast("Login failed: " + e.getMessage()); });
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> setLoading(false));

                if (!response.isSuccessful()) {
                    runOnUiThread(() -> toast("Login error: " + response.code()));
                    return;
                }
                try {
                    JSONObject json = new JSONObject(body);
                    String token = json.getString("token");
                    int tryOns = json.getInt("try_ons");
                    SessionManager.saveSession(LoginActivity.this, token, tryOns);
                    runOnUiThread(() -> { toast("Login successful! Try-ons: " + tryOns); openUpload(); });
                } catch (Exception ex) {
                    runOnUiThread(() -> toast("Parse error"));
                }
            }
        });
    }

    // ----------------- Google / Firebase flow (email/password not required) -----------------

    private void handleGoogleResult(ActivityResult result) {
        // Some OEMs return RESULT_CANCELED even when data has an error; try parse if data != null
        Intent data = result.getData();
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);

        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null) {
                setLoading(false);
                toast("Google sign-in failed");
                Log.w(TAG, "account == null");
                return;
            }
            String idToken = account.getIdToken();
            Log.d(TAG, "Google email=" + account.getEmail() + " idTokenNull=" + (idToken == null));
            if (idToken == null) {
                setLoading(false);
                toast("No ID token. Check Web client ID & SHA.");
                Log.e(TAG, "idToken == null (default_web_client_id / SHA mismatch?)");
                return;
            }
            firebaseAuthWithGoogle(idToken);

        } catch (ApiException e) {
            setLoading(false);
            int code = e.getStatusCode();
            String name = CommonStatusCodes.getStatusCodeString(code);
            toast("Google sign-in failed: " + name + " (" + code + ")");
            Log.e(TAG, "Google sign-in failed code=" + code + " name=" + name, e);
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        toast("Firebase auth failed");
                        Log.e(TAG, "Firebase signInWithCredential failed", task.getException());
                        return;
                    }
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        setLoading(false);
                        toast("No Firebase user");
                        Log.e(TAG, "Firebase user is null");
                        return;
                    }
                    String email = user.getEmail();
                    String uid = user.getUid();
                    if (email == null || email.trim().isEmpty()) {
                        setLoading(false);
                        toast("No email on Google account");
                        return;
                    }
                    // Use a deterministic password so your existing backend can auth this Google user
                    String derivedPassword = "GOOG:" + uid + ":v1";

                    // Backend unchanged: try authenticate first; if 401, then signup.
                    backendAuthenticate(email.trim(), derivedPassword);
                });
    }

    private void backendAuthenticate(String usernameEmail, String password) {
        RequestBody form = new FormBody.Builder()
                .add("username", usernameEmail)
                .add("password", password)
                .build();

        Request req = new Request.Builder()
                .url(BASE_URL + "/authenticate")
                .post(form)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    toast("Login failed: " + e.getMessage());
                });
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(body);
                        String token = json.getString("token");
                        int tryOns = json.getInt("try_ons");
                        SessionManager.saveSession(LoginActivity.this, token, tryOns);
                        runOnUiThread(() -> {
                            setLoading(false);
                            toast("Logged in with Google");
                            openUpload();
                        });
                    } catch (Exception ex) {
                        runOnUiThread(() -> { setLoading(false); toast("Parse error"); });
                    }
                } else if (response.code() == 401) {
                    backendSignup(usernameEmail, password);
                } else {
                    runOnUiThread(() -> { setLoading(false); toast("Login error: " + response.code()); });
                }
            }
        });
    }

    private void backendSignup(String usernameEmail, String password) {
        RequestBody form = new FormBody.Builder()
                .add("username", usernameEmail)
                .add("password", password)
                .build();

        Request req = new Request.Builder()
                .url(BASE_URL + "/signup")
                .post(form)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> { setLoading(false); toast("Signup failed: " + e.getMessage()); });
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(body);
                        String token = json.getString("token");
                        int tryOns = json.getInt("try_ons");
                        SessionManager.saveSession(LoginActivity.this, token, tryOns);
                        runOnUiThread(() -> {
                            setLoading(false);
                            toast("Signed up with Google");
                            openUpload();
                        });
                    } catch (Exception ex) {
                        runOnUiThread(() -> { setLoading(false); toast("Parse error"); });
                    }
                } else if (response.code() == 400) {
                    // Username already exists → try authenticate once more
                    backendAuthenticate(usernameEmail, password);
                } else {
                    runOnUiThread(() -> { setLoading(false); toast("Signup error: " + response.code()); });
                }
            }
        });
    }

    // ----------------- Utilities -----------------

    private void openUpload() {
        Intent i = new Intent(LoginActivity.this, UploadActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void validateToken(String token) {
        Request req = new Request.Builder()
                .url(BASE_URL + "/me")
                .header("Authorization", "Bearer " + token)
                .get()
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { /* ignore; leave on login */ }
            @Override public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) runOnUiThread(LoginActivity.this::openUpload);
                else SessionManager.clear(LoginActivity.this);
            }
        });
    }
}
