package dat.nguyenvan.smarthandwritingai;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

public class UIUtils {

    public static void showSuccessSnackbar(View view, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(Color.parseColor("#FF00E676"));
        snackbar.setTextColor(Color.BLACK);
        snackbar.show();
    }

    public static void showErrorSnackbar(View view, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(Color.parseColor("#FFFF5252"));
        snackbar.setTextColor(Color.WHITE);
        snackbar.show();
    }

    public static void showWarningSnackbar(View view, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(Color.parseColor("#FFFFD740"));
        snackbar.setTextColor(Color.BLACK);
        snackbar.show();
    }
}
