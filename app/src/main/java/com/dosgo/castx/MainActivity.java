package com.dosgo.castx;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;
import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity
        implements BottomNavigationView.OnNavigationItemSelectedListener {

    private BottomNavigationView bottomNavigation;
    private Fragment homeFragment;
    private Fragment searchFragment;
    private Fragment profileFragment;
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化Fragment实例
        homeFragment = new CastxFragment();
        searchFragment = new CastxFragment();
        profileFragment = new CastxFragment();
        currentFragment = homeFragment;


        // 默认加载首页Fragment
        loadFragment(homeFragment);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.castx_server && currentFragment != homeFragment) {
            loadFragment(homeFragment);
            currentFragment = homeFragment;
            return true;
        } else if (itemId == R.id.scrcpy_client && currentFragment != searchFragment) {
            loadFragment(searchFragment);
            currentFragment = searchFragment;
            return true;
        } else if (itemId == R.id.castx_client && currentFragment != profileFragment) {
            loadFragment(profileFragment);
            currentFragment = profileFragment;
            return true;
        }
        return false;
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
        if (homeFragment != null && homeFragment != fragment && homeFragment.isAdded()) {
            transaction.hide(homeFragment);
        }
        if (searchFragment != null && searchFragment != fragment && searchFragment.isAdded()) {
            transaction.hide(searchFragment);
        }
        if (profileFragment != null && profileFragment != fragment && profileFragment.isAdded()) {
            transaction.hide(profileFragment);
        }

        transaction.commit();
    }
}