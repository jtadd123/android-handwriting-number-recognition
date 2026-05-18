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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin, btnRegister, btnForgotPassword, btnSkip;
    private TextView tvError;
    private ProgressBar progressAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Nếu đã đăng nhập rồi thì bypass thẳng vào app
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);
        initViews();
        setupListeners();
    }

    private void initViews() {
        etEmail       = findViewById(R.id.et_email);
        etPassword    = findViewById(R.id.et_password);
        btnLogin      = findViewById(R.id.btn_login);
        btnRegister   = findViewById(R.id.btn_register);
        btnForgotPassword = findViewById(R.id.btn_forgot_password);
        btnSkip       = findViewById(R.id.btn_skip_login);
        tvError       = findViewById(R.id.tv_auth_error);
        progressAuth  = findViewById(R.id.progress_auth);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> login());
        btnRegister.setOnClickListener(v -> register());
        btnSkip.setOnClickListener(v -> goToMain());
        btnForgotPassword.setOnClickListener(v -> sendPasswordReset());
    }

    // ── Login ────────────────────────────────────────────────────────────────
    private void login() {
        String email    = getEmail();
        String password = getPassword();
        if (!validate(email, password)) return;

        setLoading(true);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    setLoading(false);
                    UIUtils.showSuccessSnackbar(
                            findViewById(android.R.id.content),
                            "Đăng nhập thành công! Xin chào " + result.getUser().getEmail()
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
        String email    = getEmail();
        String password = getPassword();
        if (!validate(email, password)) return;

        if (password.length() < 6) {
            showError("Mật khẩu phải có ít nhất 6 ký tự");
            return;
        }

        setLoading(true);
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    setLoading(false);
                    UIUtils.showSuccessSnackbar(
                            findViewById(android.R.id.content),
                            "Tạo tài khoản thành công! Đã đăng nhập."
                    );
                    goToMain();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(friendlyError(e.getMessage()));
                });
    }

    // ── Password Reset ────────────────────────────────────────────────────────
    private void sendPasswordReset() {
        String email = getEmail();
        if (TextUtils.isEmpty(email)) {
            showError("Hãy nhập email trước");
            return;
        }
        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused ->
                        UIUtils.showSuccessSnackbar(
                                findViewById(android.R.id.content),
                                "Email đặt lại mật khẩu đã được gửi!"
                        ))
                .addOnFailureListener(e ->
                        showError("Không thể gửi email: " + e.getMessage()));
    }

    // ── Navigation ────────────────────────────────────────────────────────────
    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String getEmail() {
        return etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
    }

    private String getPassword() {
        return etPassword.getText() != null ? etPassword.getText().toString() : "";
    }

    private boolean validate(String email, String password) {
        if (TextUtils.isEmpty(email)) {
            showError("Vui lòng nhập email");
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Email không hợp lệ");
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            showError("Vui lòng nhập mật khẩu");
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
        btnRegister.setEnabled(!loading);
    }

    /**
     * Chuyển lỗi Firebase (tiếng Anh) thành thông báo thân thiện tiếng Việt.
     */
    private String friendlyError(String raw) {
        if (raw == null) return "Đã xảy ra lỗi";
        if (raw.contains("no user record") || raw.contains("user-not-found"))
            return "Email chưa được đăng ký";
        if (raw.contains("password is invalid") || raw.contains("wrong-password"))
            return "Sai mật khẩu";
        if (raw.contains("email address is already in use") || raw.contains("email-already-in-use"))
            return "Email này đã được đăng ký";
        if (raw.contains("network"))
            return "Không có kết nối mạng";
        if (raw.contains("too-many-requests"))
            return "Quá nhiều lần thử. Hãy thử lại sau.";
        return raw;
    }
}
