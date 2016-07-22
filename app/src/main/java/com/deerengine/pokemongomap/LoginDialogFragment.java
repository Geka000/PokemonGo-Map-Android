package com.deerengine.pokemongomap;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class LoginDialogFragment extends DialogFragment{
    private EditText mEditTextPassword;
    private EditText mEditTextLogin;
    private Settings mSettings;

    public static LoginDialogFragment newInstance() {
        Bundle args = new Bundle();
        LoginDialogFragment fragment = new LoginDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettings = new Settings(getContext());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setPositiveButton(R.string.dialog_ok, null);
        builder.setNegativeButton(R.string.dialog_cancel, null);
        LayoutInflater inflatter = getActivity().getLayoutInflater();
        View root = inflatter.inflate(R.layout.dialog_login,null);
        mEditTextLogin = (EditText) root.findViewById(R.id.editText_login);
        mEditTextPassword = (EditText) root.findViewById(R.id.editText_password);
        String login = mSettings.getLogin();
        String password = mSettings.getPassword();
        if (!TextUtils.isEmpty(login))
            mEditTextLogin.setText(login);
        if (!TextUtils.isEmpty(password))
            mEditTextPassword.setText(password);
        builder.setView(root);
        builder.setCancelable(true);
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        final AlertDialog d = (AlertDialog)getDialog();
        if(d != null) {
            Button positive = d.getButton(Dialog.BUTTON_POSITIVE);
            positive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (TextUtils.isEmpty(mEditTextLogin.getText())) {
                        mEditTextLogin.setError(getString(R.string.dialog_field_required));
                        return;
                    }
                    if (TextUtils.isEmpty(mEditTextPassword.getText())) {
                        mEditTextPassword.setError(getString(R.string.dialog_field_required));
                        return;
                    }
                    mSettings.setLogin(mEditTextLogin.getText().toString());
                    mSettings.setPassword(mEditTextPassword.getText().toString());
                    dismiss();
                }
            });
        }
    }
}
