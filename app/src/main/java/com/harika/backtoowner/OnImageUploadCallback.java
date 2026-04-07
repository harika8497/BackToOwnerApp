package com.harika.backtoowner;

public interface OnImageUploadCallback {
    void onSuccess(String imageUrl);

    void onFailure();
}
