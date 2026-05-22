package dat.nguyenvan.smarthandwritingai;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    /** Domain ảo dùng nội bộ – user chỉ thấy username, không biết email */
    private static final String FAKE_EMAIL_DOMAIN = "@smarthandwriting.app";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextInputEditText etUsername, etPassword, etConfirmPassword;
    private TextInputLayout layoutConfirmPassword;
    private MaterialButton btnLogin, btnToggleMode, btnForgotPassword, btnSkip;
    private TextView tvError, tvToggleHint, tvSubtitle;
    private ProgressBar progressAuth;

    /** true = đang ở chế độ Đăng ký, false = Đăng nhập */
    private boolean isRegisterMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Nếu đã đăng nhập rồi thì bypass thẳng vào app
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);
        initViews();
        setupListeners();
        updateUI(); // hiển thị đúng trạng thái login/register
    }

    private void initViews() {
        etUsername          = findViewById(R.id.et_username);
        etPassword          = findViewById(R.id.et_password);
        etConfirmPassword   = findViewById(R.id.et_confirm_password);
        layoutConfirmPassword = findViewById(R.id.layout_confirm_password);
        btnLogin            = findViewById(R.id.btn_login);
        btnToggleMode       = findViewById(R.id.btn_toggle_mode);
        btnForgotPassword   = findViewById(R.id.btn_forgot_password);
        btnSkip             = findViewById(R.id.btn_skip_login);
        tvError             = findViewById(R.id.tv_auth_error);
        tvToggleHint        = findViewById(R.id.tv_toggle_hint);
        tvSubtitle          = findViewById(R.id.tv_login_subtitle);
        progressAuth        = findViewById(R.id.progress_auth);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> {
            if (isRegisterMode) register();
            else login();
        });

        btnToggleMode.setOnClickListener(v -> {
            isRegisterMode = !isRegisterMode;
            hideError();
            updateUI();
        });

        btnSkip.setOnClickListener(v -> goToMain());

        btnForgotPassword.setOnClickListener(v -> sendPasswordReset());
    }

    /** Cập nhật giao diện khi chuyển đổi Login ↔ Register */
    private void updateUI() {
        if (isRegisterMode) {
            btnLogin.setText(getString(R.string.login_btn_register));
            tvToggleHint.setText(getString(R.string.login_toggle_hint_register));
            btnToggleMode.setText(getString(R.string.login_btn_toggle_login));
            tvSubtitle.setText(getString(R.string.login_subtitle_register));
            layoutConfirmPassword.setVisibility(View.VISIBLE);
            btnForgotPassword.setVisibility(View.GONE);
        } else {
            btnLogin.setText(getString(R.string.login_btn_login));
            tvToggleHint.setText(getString(R.string.login_toggle_hint));
            btnToggleMode.setText(getString(R.string.login_btn_toggle_register));
            tvSubtitle.setText(getString(R.string.login_subtitle));
            layoutConfirmPassword.setVisibility(View.GONE);
            btnForgotPassword.setVisibility(View.VISIBLE);
        }
    }

    // ── Login ────────────────────────────────────────────────────────────────
    private void login() {
        String username = getUsername();
        String password = getPassword();
        if (!validateLogin(username, password)) return;

        setLoading(true);

        // Bước 1: Tra Firestore xem username có tồn tại không
        db.collection("users").document(username.toLowerCase()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String email = doc.getString("email");
                        if (email == null) {
                            setLoading(false);
                            showError(getString(R.string.err_account_data));
                            return;
                        }
                        // Bước 2: Đăng nhập Firebase Auth bằng email ẩn
                        signInWithEmail(email, password);
                    } else {
                        setLoading(false);
                        showError(getString(R.string.err_username_not_exist));
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    // Nếu offline, thử đăng nhập trực tiếp bằng email ẩn
                    String fallbackEmail = username.toLowerCase() + FAKE_EMAIL_DOMAIN;
                    signInWithEmail(fallbackEmail, password);
                });
    }

    private void signInWithEmail(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    setLoading(false);
                    UIUtils.showSuccessSnackbar(
                            findViewById(android.R.id.content),
                            getString(R.string.msg_login_success)
                    );
                    goToMain();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(friendlyError(e.getMessage()));
                });
    }

    // ── Register ─────────────────────────────────────────────────────────────
    private void register() {
        String username = getUsername();
        String password = getPassword();
        String confirmPassword = getConfirmPassword();

        if (!validateRegister(username, password, confirmPassword)) return;

        setLoading(true);

        // Bước 1: Kiểm tra username đã tồn tại chưa (trên Firestore)
        String usernameLower = username.toLowerCase();
        db.collection("users").document(usernameLower).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        setLoading(false);
                        showError(getString(R.string.err_username_taken));
                    } else {
                        // Bước 2: Tạo account Firebase Auth bằng email ẩn
                        String fakeEmail = usernameLower + FAKE_EMAIL_DOMAIN;
                        createFirebaseAccount(usernameLower, fakeEmail, password);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(getString(R.string.err_check_account_failed, e.getMessage()));
                });
    }

    private void createFirebaseAccount(String username, String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    // Bước 3: Lưu mapping username → email vào Firestore
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("username", username);
                    userData.put("email", email);
                    userData.put("createdAt", System.currentTimeMillis());

                    db.collection("users").document(username)
                            .set(userData)
                            .addOnSuccessListener(unused -> {
                                setLoading(false);
                                UIUtils.showSuccessSnackbar(
                                        findViewById(android.R.id.content),
                                        getString(R.string.msg_register_success)
                                );
                                goToMain();
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                // Account đã tạo trên Auth nhưng Firestore fail
                                // Vẫn cho vào app, lần sau sẽ retry
                                UIUtils.showSuccessSnackbar(
                                        findViewById(android.R.id.content),
                                        getString(R.string.msg_register_success)
                                );
                                goToMain();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(friendlyError(e.getMessage()));
                });
    }

    // ── Password Reset ────────────────────────────────────────────────────────
    private void sendPasswordReset() {
        String username = getUsername();
        if (TextUtils.isEmpty(username)) {
            showError(getString(R.string.err_enter_username_first));
            return;
        }

        setLoading(true);
        db.collection("users").document(username.toLowerCase()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String email = doc.getString("email");
                        if (email != null) {
                            mAuth.sendPasswordResetEmail(email)
                                     .addOnSuccessListener(unused -> {
                                         setLoading(false);
                                         UIUtils.showSuccessSnackbar(
                                                 findViewById(android.R.id.content),
                                                 getString(R.string.err_reset_password_unsupported)
                                         );
                                     })
                                     .addOnFailureListener(e -> {
                                         setLoading(false);
                                         showError(getString(R.string.err_send_reset_failed, e.getMessage()));
                                     });
                        }
                    } else {
                        setLoading(false);
                        showError(getString(R.string.err_username_not_exist));
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(getString(R.string.err_connection_failed, e.getMessage()));
                });
    }

    // ── Navigation ────────────────────────────────────────────────────────────
    private void goToMain() {
        // Always show onboarding animations before entering the app
        Intent intent = new Intent(this, OnboardingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String getUsername() {
        return etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
    }

    private String getPassword() {
        return etPassword.getText() != null ? etPassword.getText().toString() : "";
    }

    private String getConfirmPassword() {
        return etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";
    }

    private boolean validateLogin(String username, String password) {
        if (TextUtils.isEmpty(username)) {
            showError(getString(R.string.err_enter_username));
            return false;
        }
        if (username.length() < 3) {
            showError(getString(R.string.err_username_too_short));
            return false;
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            showError(getString(R.string.err_username_invalid_chars));
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            showError(getString(R.string.err_enter_password));
            return false;
        }
        hideError();
        return true;
    }

    private boolean validateRegister(String username, String password, String confirmPassword) {
        if (!validateLogin(username, password)) return false;

        if (password.length() < 6) {
            showError(getString(R.string.err_password_too_short));
            return false;
        }
        if (!password.equals(confirmPassword)) {
            showError(getString(R.string.err_password_mismatch));
            return false;
        }
        hideError();
        return true;
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    private void setLoading(boolean loading) {
        progressAuth.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnToggleMode.setEnabled(!loading);
        btnSkip.setEnabled(!loading);
    }

    /**
     * Chuyển lỗi Firebase (tiếng Anh) thành thông báo thân thiện tiếng Việt.
     */
    private String friendlyError(String raw) {
        if (raw == null) return getString(R.string.err_unknown);
        if (raw.contains("no user record") || raw.contains("user-not-found"))
            return getString(R.string.err_username_not_exist);
        if (raw.contains("password is invalid") || raw.contains("wrong-password")
                || raw.contains("INVALID_LOGIN_CREDENTIALS"))
            return getString(R.string.err_wrong_password);
        if (raw.contains("email address is already in use") || raw.contains("email-already-in-use"))
            return getString(R.string.err_username_taken);
        if (raw.contains("network"))
            return getString(R.string.err_connection_failed, raw);
        if (raw.contains("too-many-requests"))
            return getString(R.string.err_too_many_attempts);
        if (raw.contains("WEAK_PASSWORD") || raw.contains("weak-password"))
            return getString(R.string.err_password_too_short);
        return raw;
    }
}
