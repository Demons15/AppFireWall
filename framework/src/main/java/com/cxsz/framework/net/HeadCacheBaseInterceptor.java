package com.cxsz.framework.net;

import android.content.Context;

import com.cxsz.framework.constant.KeyConstants;
import com.cxsz.framework.tool.SpUtil;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by cxsz-dev03 on 2017/12/5.
 */

public class HeadCacheBaseInterceptor implements Interceptor {


    private Context context;

    public HeadCacheBaseInterceptor(Context context) {
        this.context = context;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        String userId = SpUtil.getString(context, KeyConstants.NET_USER_ID);
        String access_token = SpUtil.getString(context, KeyConstants.NET_TOKEN);
        Request.Builder requestBuilder = originalRequest.newBuilder()
                .addHeader(KeyConstants.NET_USER_ID, userId)
                .addHeader(KeyConstants.NET_TOKEN, access_token)
                .method(originalRequest.method(), originalRequest.body());
        Request request = requestBuilder.build();
        return chain.proceed(request);
    }
}
