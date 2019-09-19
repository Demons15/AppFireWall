package com.cxsz.framework.base;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.cxsz.framework.binding.ViewBinder;
import me.yokeyword.fragmentation.SupportActivity;

/**
 * Created by llf on 2017/3/1.
 * 基础的Activity
 */

public abstract class BaseActivity extends SupportActivity {
    protected static final String TAG = "BaseActivity";
    protected Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutId());
        ViewBinder.bind(this);
        context = getApplicationContext();
        this.initView(savedInstanceState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        //当模式为singleTop和SingleInstance会回调到这里
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    //获取布局
    protected abstract int getLayoutId();

    //初始化布局和监听
    protected abstract void initView(Bundle savedInstanceState);
}
