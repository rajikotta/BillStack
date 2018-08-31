package in.raji.billstack;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v17.leanback.app.OnboardingSupportFragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class TipScreen extends OnboardingSupportFragment {
    public static String COMPLETED_ONBOARDING_PREF_NAME = "COMPLETED_ONBOARDING_PREF_NAME";

    @Override
    protected int getPageCount() {
        return 1;
    }

    @Override
    protected CharSequence getPageTitle(int pageIndex) {
        return getString(R.string.app_name);
    }

    @Override
    protected CharSequence getPageDescription(int pageIndex) {
        return "Ver 1.0";
    }

    @Nullable
    @Override
    protected View onCreateBackgroundView(LayoutInflater inflater, ViewGroup container) {
        View bgView = new View(getActivity());
        bgView.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
        return bgView;
    }

    @Nullable
    @Override
    protected View onCreateContentView(LayoutInflater inflater, ViewGroup container) {
        TextView mContentView = new TextView(getActivity());
        mContentView.setPadding(64, 128, 64, 32);
        mContentView.setText(getString(R.string.tip_screen_content));
        mContentView.setGravity(Gravity.CENTER_HORIZONTAL);
        return mContentView;
    }

    @Nullable
    @Override
    protected View onCreateForegroundView(LayoutInflater inflater, ViewGroup container) {
        return null;
    }

    @Override
    protected void onFinishFragment() {
        super.onFinishFragment();
        // User has seen OnboardingFragment, so mark our SharedPreferences
        // flag as completed so that we don't show our OnboardingFragment
        // the next time the user launches the app.
        SharedPreferences.Editor sharedPreferencesEditor =
                PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        sharedPreferencesEditor.putBoolean(
                COMPLETED_ONBOARDING_PREF_NAME, true);
        sharedPreferencesEditor.apply();
        getActivity().finish();
    }
}
