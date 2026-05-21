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
            btnLogin.setText("Tạo Tài Khoản");
            tvToggleHint.setText("Đã có tài khoản?");
            btnToggleMode.setText("Đăng nhập");
            tvSubtitle.setText("Tạo tài khoản mới để đồng bộ dữ liệu");
            layoutConfirmPassword.setVisibility(View.VISIBLE);
            btnForgotPassword.setVisibility(View.GONE);
        } else {
            btnLogin.setText("Đăng Nhập");
            tvToggleHint.setText("Chưa có tài khoản?");
            btnToggleMode.setText("Đăng ký");
            tvSubtitle.setText("Đăng nhập để đồng bộ lịch sử của bạn");
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
                            showError("Lỗi dữ liệu tài khoản");
                            return;
                        }
                        // Bước 2: Đăng nhập Firebase Auth bằng email ẩn
                        signInWithEmail(email, password);
                    } else {
                        setLoading(false);
                        showError("Tên đăng nhập không tồn tại");
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
                            "Đăng nhập thành công!"
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
                        showError("Tên đăng nhập đã được sử dụng");
                    } else {
                        // Bước 2: Tạo account Firebase Auth bằng email ẩn
                        String fakeEmail = usernameLower + FAKE_EMAIL_DOMAIN;
                        createFirebaseAccount(usernameLower, fakeEmail, password);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Không thể kiểm tra tài khoản: " + e.getMessage());
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
                                        "Tạo tài khoản thành công!"
                                );
                                goToMain();
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                // Account đã tạo trên Auth nhưng Firestore fail
                                // Vẫn cho vào app, lần sau sẽ retry
                                UIUtils.showSuccessSnackbar(
                                        findViewById(android.R.id.content),
                                        "Tạo tài khoản thành công!"
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
            showError("Hãy nhập tên đăng nhập trước");
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
                                                "Tính năng đặt lại mật khẩu chưa hỗ trợ với username. Vui lòng liên hệ admin."
                                        );
                                    })
                                    .addOnFailureListener(e -> {
                                        setLoading(false);
                                        showError("Không thể gửi email reset: " + e.getMessage());
                                    });
                        }
                    } else {
                        setLoading(false);
                        showError("Tên đăng nhập không tồn tại");
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Lỗi kết nối: " + e.getMessage());
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
            showError("Vui lòng nhập tên đăng nhập");
            return false;
        }
        if (username.length() < 3) {
            showError("Tên đăng nhập phải có ít nhất 3 ký tự");
            return false;
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            showError("Tên đăng nhập chỉ được chứa chữ, số và dấu _");
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            showError("Vui lòng nhập mật khẩu");
            return false;
        }
        hideError();
        return true;
    }

    private boolean validateRegister(String username, String password, String confirmPassword) {
        if (!validateLogin(username, password)) return false;

        if (password.length() < 6) {
            showError("Mật khẩu phải có ít nhất 6 ký tự");
            return false;
        }
        if (!password.equals(confirmPassword)) {
            showError("Mật khẩu nhập lại không khớp");
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
        if (raw == null) return "Đã xảy ra lỗi";
        if (raw.contains("no user record") || raw.contains("user-not-found"))
            return "Tên đăng nhập không tồn tại";
        if (raw.contains("password is invalid") || raw.contains("wrong-password")
                || raw.contains("INVALID_LOGIN_CREDENTIALS"))
            return "Sai mật khẩu";
        if (raw.contains("email address is already in use") || raw.contains("email-already-in-use"))
            return "Tên đăng nhập đã được sử dụng";
        if (raw.contains("network"))
            return "Không có kết nối mạng";
        if (raw.contains("too-many-requests"))
            return "Quá nhiều lần thử. Hãy thử lại sau.";
        if (raw.contains("WEAK_PASSWORD") || raw.contains("weak-password"))
            return "Mật khẩu quá yếu, cần ít nhất 6 ký tự";
        return raw;
    }
}
