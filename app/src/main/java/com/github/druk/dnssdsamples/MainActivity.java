package com.github.druk.dnssdsamples;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.druk.rx2dnssd.BonjourService;
import com.github.druk.rx2dnssd.Rx2Dnssd;
import com.github.druk.rx2dnssd.Rx2DnssdEmbedded;

import java.util.Objects;
import java.util.Set;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


public class MainActivity extends AppCompatActivity {

    private Rx2Dnssd rxDnssd;
    @Nullable
    private Disposable browseDisposable;
    @Nullable
    private Disposable registerDisposable;

    private ServiceAdapter mServiceAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rxDnssd = new Rx2DnssdEmbedded(this);

        findViewById(R.id.check_threads).setOnClickListener(v -> {
            /*
             *   当我们在执行browse操作时，当所有的服务都被发现并且超时时间耗尽（默认60秒）时，应该只有5个线程在活动：
             *   - main
             *   - NsdManager
             *   - Thread #<n> (it's DNSSD browse thread)
             *   - RxIoScheduler-1 (rx possibly can create more or less threads, in my case was 2)
             *   - RxIoScheduler-2
             */

            // 记录当前活动线程的数量
            Log.i("Thread", "Thread count " + Thread.activeCount() + ":");

            // 获取所有活动线程的集合
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            for (Thread thread : threadSet) {
                // We only interested in main group（主线程组）
                if (Objects.requireNonNull(thread.getThreadGroup()).getName().equals("main")) {
                    Log.v("Thread", thread.getName());
                }
            }
        });

        findViewById(R.id.register).setOnClickListener(v -> {
            if (registerDisposable == null) {
                register((Button) v);
            }
            else {
                unregister((Button) v);
            }
        });

        findViewById(R.id.browse).setOnClickListener(v -> {
            if (browseDisposable == null) {
                ((TextView) v).setText(R.string.browse_stop);
                findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
                startBrowse();
            } else {
                ((TextView) v).setText(R.string.browse_start);
                findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
                stopBrowse();
                mServiceAdapter.clear();
            }
        });

        mServiceAdapter = new ServiceAdapter(this);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mServiceAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (browseDisposable == null) {
            ((TextView) findViewById(R.id.browse)).setText(R.string.browse_stop);
            findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
            startBrowse();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (browseDisposable != null) {
            ((TextView) findViewById(R.id.browse)).setText(R.string.browse_start);
            findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
            stopBrowse();
            mServiceAdapter.clear();
        }
    }

    private void startBrowse() {
        Log.i("TAG", "start browse");
        // 使用Rx2Dnssd提供的browse()方法来开始Bonjour服务的浏览。这是一个异步操作，使用RxJava的Observable来处理结果。
        browseDisposable = rxDnssd.browse("_rxdnssd._tcp", "local.")
                .compose(rxDnssd.resolve())                 // 使用resolve()方法来解析Bonjour服务的IP地址和端口号
                .compose(rxDnssd.queryIPRecords())          // 使用queryIPRecords()方法来查询Bonjour服务的IP地址
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bonjourService -> {              // 订阅浏览操作的结果，包括成功时的回调(bonjourService)和错误时的回调(throwable)。
                    Log.d("TAG", bonjourService.toString());
                    if (bonjourService.isLost()) {
                        mServiceAdapter.remove(bonjourService);
                    } else {
                        mServiceAdapter.add(bonjourService);
                    }
                }, throwable -> Log.e("TAG", "error", throwable));
    }

    private void stopBrowse() {
        Log.d("TAG", "Stop browsing");
        if (browseDisposable != null) {
            browseDisposable.dispose();
        }
        browseDisposable = null;
    }

    private void register(final Button button) {
        Log.i("TAG", "register");
        button.setEnabled(false);
        BonjourService bs = new BonjourService.Builder(0, 0, Build.DEVICE, "_rxdnssd._tcp", null).port(123).build();
        // 异步操作，使用Rx2Dnssd提供的register方法来注册Bonjour服务
        registerDisposable = rxDnssd.register(bs)
                .subscribeOn(Schedulers.io())               // 指定注册操作在 IO 线程执行，以防止在主线程上执行耗时的网络操作。
                .observeOn(AndroidSchedulers.mainThread())  // 指定结果的处理发生在主线程，以便在 UI 上更新界面。
                .subscribe(bonjourService -> {              // 订阅注册操作的结果，包括成功时的回调(bonjourService)和错误时的回调(throwable)。
                    Log.i("TAG", "Register successfully " + bonjourService.toString());
                    button.setEnabled(true);
                    button.setText(R.string.unregister);
                    Toast.makeText(MainActivity.this, "Rgstrd " + Build.DEVICE, Toast.LENGTH_SHORT).show();
                }, throwable -> {
                    Log.e("TAG", "error", throwable);
                    button.setEnabled(true);
                });
    }

    private void unregister(final Button button) {
        Log.d("TAG", "unregister");
        if (registerDisposable != null) {
            registerDisposable.dispose();
        }
        registerDisposable = null;
        button.setText(R.string.register);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (browseDisposable != null) {
            browseDisposable.dispose();
        }
        if (registerDisposable != null) {
            registerDisposable.dispose();
        }
    }
}
