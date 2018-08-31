package in.raji.billstack;

import android.app.ProgressDialog;
import android.content.Context;

public class ProgressDialogUtility {

    static ProgressDialog progressDialog;

    public static void showProgressDialog(Context context) {
        if (progressDialog == null)
            progressDialog = new ProgressDialog(context);
        cancelProgressDialog();
        progressDialog.setMessage("Please Wait....");
        try {
            progressDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void cancelProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing())
            progressDialog.dismiss();
    }
}
