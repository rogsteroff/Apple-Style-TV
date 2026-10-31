package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Apple Style TV quick access: real app shortcuts, not artwork baked into the wallpaper.
 * The four compact slots follow Lampa, YouTube, Yandex Browser and VK.
 * HAPP uses the same confirmed package as the remote assistant shortcut.
 */
public final class AppleQuickAccessView extends LinearLayout {
    private static final String[] TITLES = {"Лампа", "YouTube", "Яндекс", "ВКонтакте"};
    private static final String[] PACKAGES = {
            "top.rootu.lampa", "com.google.android.youtube.tv", "com.yandex.browser.tv", "com.vk.tv"
    };
    private final LinearLayout[] cards = new LinearLayout[4];
    private final ImageView[] icons = new ImageView[4];
    private final TextView[] titles = new TextView[4];
    private final Intent[] launches = new Intent[4];

    public AppleQuickAccessView(Context context) { this(context, null); }

    public AppleQuickAccessView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        for (int i = 0; i < TITLES.length; i++) {
            final int index = i;
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(5), dp(5), dp(5), dp(4));
            card.setFocusable(true);
            card.setClickable(true);
            card.setBackground(background(false));
            card.setNextFocusDownId(R.id.grid);
            card.setContentDescription(TITLES[i]);
            card.setOnClickListener(v -> open(index));
            card.setOnFocusChangeListener((v, focused) -> {
                v.setBackground(background(focused));
                v.animate().scaleX(focused ? 1.045f : 1f)
                        .scaleY(focused ? 1.045f : 1f).setDuration(130).start();
            });

            ImageView icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageResource(android.R.drawable.sym_def_app_icon);
            card.addView(icon, new LayoutParams(dp(28), dp(28)));

            TextView label = new TextView(context);
            label.setText(TITLES[i]);
            label.setTextColor(Color.WHITE);
            label.setTextSize(12);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dp(3);
            card.addView(label, labelParams);

            // Compact ~105dp cards instead of stretching across the entire TV width.
            LayoutParams cardParams = new LayoutParams(dp(105), dp(64));
            if (i > 0) cardParams.leftMargin = dp(8);
            addView(card, cardParams);
            cards[i] = card;
            icons[i] = icon;
            titles[i] = label;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refresh();
    }

    public void refresh() {
        PackageManager pm = getContext().getPackageManager();
        for (int i = 0; i < cards.length; i++) {
            Intent intent = PACKAGES[i].isEmpty() ? null : launchIntent(pm, PACKAGES[i]);
            if (intent == null) intent = findByLabel(pm, i);
            launches[i] = intent;
            cards[i].setEnabled(intent != null);
            cards[i].setFocusable(intent != null);
            cards[i].setAlpha(intent != null ? 1f : .45f);
            if (intent != null) {
                try {
                    ResolveInfo match = pm.resolveActivity(intent, 0);
                    Drawable icon = match == null ? null : match.loadIcon(pm);
                    if (icon == null && intent.getComponent() != null) {
                        icon = pm.getActivityIcon(intent.getComponent());
                    }
                    if (icon != null) icons[i].setImageDrawable(icon);
                } catch (Exception ignored) { }
            }
        }
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
        return intent;
    }

    static Intent findByLabel(PackageManager pm, int slot) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Intent fallbackQuery = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(query, 0));
        apps.addAll(pm.queryIntentActivities(fallbackQuery, 0));
        for (ResolveInfo app : apps) {
            if (app.activityInfo == null) continue;
            String label = String.valueOf(app.loadLabel(pm)).toLowerCase(Locale.ROOT);
            String pkg = app.activityInfo.packageName;
            boolean match;
            switch (slot) {
                case 0: match = label.contains("lampa") || label.contains("лампа"); break;
                case 1: match = label.contains("youtube") || label.contains("ютуб"); break;
                case 2: match = pkg.equals("com.yandex.browser.tv") || label.contains("яндекс") && label.contains("браузер") || label.contains("yandex browser"); break;
                default: match = label.contains("вконтакте") || label.equals("vk") || label.equals("vk video"); break;
            }
            if (!match) continue;
            Intent intent = launchIntent(pm, pkg);
            if (intent != null) return intent;
        }
        return null;
    }

    private void open(int index) {
        if (launches[index] == null) return;
        try {
            Intent intent = new Intent(launches[index]);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Exception ignored) { }
    }

    private GradientDrawable background(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(focused ? 0xA04E648B : 0x91303C58);
        d.setCornerRadius(dp(18));
        d.setStroke(dp(focused ? 2 : 1), focused ? 0xFF82B5FF : 0x66DDE9FF);
        return d;
    }

    private int dp(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
