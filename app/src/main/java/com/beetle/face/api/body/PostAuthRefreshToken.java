package com.beetle.face.api.body;

import com.google.gson.annotations.SerializedName;

/**
 * Created by tsung on 10/10/14.
 */
public class PostAuthRefreshToken {
    @SerializedName("refresh_token")
    public String refreshToken;
}
