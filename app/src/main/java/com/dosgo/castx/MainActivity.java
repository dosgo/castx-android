package com.dosgo.castx;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.RadioGroup;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity
        implements RadioGroup.OnCheckedChangeListener  {

    private Fragment castxFragment;
    private Fragment scrcpyClientFragment;
    private Fragment h264PlayerFragment;
    private Fragment currentFragment;
    private RadioGroup tabBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化Fragment实例
        castxFragment = new CastxFragment();
        scrcpyClientFragment = new ScrcpyClientFragment();
        h264PlayerFragment = new H264PlayerFragment();
        currentFragment = castxFragment;


        // 默认加载首页Fragment
        loadFragment(castxFragment);
        tabBar = findViewById(R.id.tab_bar);
        tabBar.setOnCheckedChangeListener(this);
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

        System.out.println("onNavigationItemSelected:"+checkedId);
        if (checkedId  == R.id.castx_server && currentFragment != castxFragment) {
            loadFragment(castxFragment);
            currentFragment = castxFragment;
            return ;
        } else if (checkedId == R.id.scrcpy_client && currentFragment != scrcpyClientFragment) {
            loadFragment(scrcpyClientFragment);
            currentFragment = scrcpyClientFragment;
            return ;
        } else if (checkedId == R.id.castx_client && currentFragment != h264PlayerFragment) {
            loadFragment(h264PlayerFragment);
            currentFragment = h264PlayerFragment;
            return ;
        }
        return ;
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();


        // 如果fragment已经添加过，显示它，否则添加新的
        if (fragment.isAdded()) {
            transaction.show(fragment);
        } else {
            transaction.add(R.id.fragment_container, fragment);
        }

        // 隐藏其他fragments
        if (castxFragment != null && castxFragment != fragment && castxFragment.isAdded()) {
            transaction.hide(castxFragment);
        }
        if (scrcpyClientFragment != null && scrcpyClientFragment != fragment && scrcpyClientFragment.isAdded()) {
            transaction.hide(scrcpyClientFragment);
        }
        if (h264PlayerFragment != null && h264PlayerFragment != fragment && h264PlayerFragment.isAdded()) {
            transaction.hide(h264PlayerFragment);
        }

        transaction.commit();
    }
}