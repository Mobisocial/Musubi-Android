/*
 * Copyright 2012 The Stanford MobiSocial Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mobisocial.musubi.ui.widget;

import mobisocial.musubi.R;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.actionbarsherlock.internal.widget.ActionBarView;

/**
 * @see ActionBarView
 *
 */
public class ActionBarLite extends RelativeLayout {
    private final Context mContext;
    private final View mHomeAsUpView;
    private final ViewGroup mHomeLayout;
    //private final ActionMenuItem mLogoNavItem;

    private ViewGroup mCustomBar;

    private final CharSequence mTitle;
    private final TextView mTitleLayout;

    private ImageView mIconView;
    private Drawable mLogo;
    private Drawable mIcon;

    private boolean mIsConstructing;

    private final CharSequence mSubtitle;
    private final TextView mSubtitleLayout;

    private final FrameLayout mCustomView;
    private final Spinner mSpinner;

    private final LinearLayout mTabsView;
    private final ViewGroup mTabViewContainer;

    public ActionBarLite(Context context) {
        this(context, null);
    }

    public ActionBarLite(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActionBarLite(final Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mIsConstructing = true;

        LayoutInflater.from(context).inflate(R.layout.action_bar_lite, this, true);
        setBackgroundColor(Color.WHITE);

        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SherlockTheme, defStyle, 0);
        final ApplicationInfo appInfo = context.getApplicationInfo();
        final PackageManager pm = context.getPackageManager();

        //// TITLE ////

        mTitleLayout = (TextView)findViewById(R.id.abs__action_bar_title);

        //Try to load title style from the theme
        final int titleTextStyle = a.getResourceId(R.styleable.SherlockTheme_abTitleTextStyle, 0);
        if (titleTextStyle != 0) {
            mTitleLayout.setTextAppearance(context, titleTextStyle);
        }

        //Try to load title from the theme
        mTitle = a.getString(R.styleable.SherlockTheme_abTitle);
        if (mTitle != null) {
            setTitle(mTitle);
        }


        //// SUBTITLE ////

        mSubtitleLayout = (TextView)findViewById(R.id.abs__action_bar_subtitle);

        //Try to load subtitle style from the theme
        final int subtitleTextStyle = a.getResourceId(R.styleable.SherlockTheme_abSubtitleTextStyle, 0);
        if (subtitleTextStyle != 0) {
            mSubtitleLayout.setTextAppearance(context, subtitleTextStyle);
        }

        //Try to load subtitle from theme
        mSubtitle = a.getString(R.styleable.SherlockTheme_abSubtitle);
        if (mSubtitle != null) {
            setSubtitle(mSubtitle);
        }

      /// HOME ////

        mHomeLayout = (ViewGroup)findViewById(R.id.abs__home_wrapper);
        final int homeLayoutResource = a.getResourceId(R.styleable.SherlockTheme_abHomeLayout, R.layout.action_bar_home_lite);
        LayoutInflater.from(context).inflate(homeLayoutResource, mHomeLayout, true);

        //Try to load the logo from the theme
        mLogo = a.getDrawable(R.styleable.SherlockTheme_abLogo);
        /*
        if ((mLogo == null) && (context instanceof Activity)) {
            //LOGO LOADING DOES NOT WORK
            //SEE: http://stackoverflow.com/questions/6105504/load-activity-and-or-application-logo-programmatically-from-manifest
            //SEE: https://groups.google.com/forum/#!topic/android-developers/UFR4l0ZwJWc
        }
        */

        //Try to load the icon from the theme
        mIcon = a.getDrawable(R.styleable.SherlockTheme_abIcon);
        if ((mIcon == null) && (context instanceof Activity)) {
            mIcon = appInfo.loadIcon(pm);
        }

        mHomeAsUpView = findViewById(R.id.abs__up);
        mIconView = (ImageView)findViewById(R.id.abs__home);

        //// NAVIGATION ////

        mSpinner = (Spinner)findViewById(R.id.abs__nav_list);

        mTabsView = (LinearLayout)findViewById(R.id.abs__nav_tabs);
        mTabViewContainer = (ViewGroup)findViewById(R.id.abs__nav_tabs_layout);

      //Reduce, Reuse, Recycle indeed!
        a.recycle();
        mIsConstructing = false;

        //// CUSTOM VIEW ////

        mCustomView = (FrameLayout)findViewById(R.id.abs__custom);

        /// CUSTOM BAR ///
        mCustomBar = (LinearLayout)findViewById(R.id.abs__nav_tabs);

        reloadDisplay();
    }

    private void reloadDisplay() {
        if (mIsConstructing) {
            return; //Do not run if we are in the constructor
        }

        final boolean isStandard = true;
        final boolean isList = false;
        final boolean isTab = false;
        final boolean isTabUnderAb = false;
        final boolean hasSubtitle = false;
        final boolean displayHome = true;
        final boolean displayHomeAsUp = false;
        final boolean displayTitle = true;
        final boolean displayCustom = false;
        final boolean displayLogo = false;

        mHomeLayout.setVisibility(displayHome ? View.VISIBLE : View.GONE);
        if (displayHome) {
            if (mHomeAsUpView != null) {
                mHomeAsUpView.setVisibility(displayHomeAsUp ? View.VISIBLE : View.GONE);
            }
            if (mIconView != null) {
                mIconView.setImageDrawable(displayLogo ? mLogo : mIcon);
            }
        }

        //Only show list if we are in list navigation and there are list items
        mSpinner.setVisibility(isList ? View.VISIBLE : View.GONE);

        // Show tabs if in tabs navigation mode.
        // This LinearLayout is re-used as CustomBar.
        /*mTabsView.setVisibility(isTab ? View.VISIBLE : View.GONE);
        if (mTabViewContainer != null) {
            mTabViewContainer.setVisibility(isTab ? View.VISIBLE : View.GONE);
        }*/

        //Show title view if we are not in list navigation, not showing custom
        //view, and the show title flag is true
        mTitleLayout.setVisibility((isStandard || isTabUnderAb) && !displayCustom && displayTitle ? View.VISIBLE : View.GONE);
        //Show subtitle view if we are not in list navigation, not showing
        //custom view, show title flag is true, and a subtitle is set
        mSubtitleLayout.setVisibility((isStandard || isTabUnderAb) && !displayCustom && displayTitle && hasSubtitle ? View.VISIBLE : View.GONE);
        //Show custom view if we are not in list navigation and showing custom
        //flag is set
        mCustomView.setVisibility(isStandard && displayCustom ? View.VISIBLE : View.GONE);
    }

    public void setTitle(CharSequence title) {
        mTitleLayout.setText((title == null) ? "" : title);
    }

    public void setTitle(int resId) {
        mTitleLayout.setText(resId);
    }

    public void setSubtitle(CharSequence subtitle) {
        mSubtitleLayout.setText((subtitle == null) ? "" : subtitle);
        reloadDisplay();
    }

    public ViewGroup getCustomBar() {
        return mCustomBar;
    }
}
