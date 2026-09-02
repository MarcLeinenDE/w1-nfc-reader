package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;

/**
 * Shared visible product navigation for the Material 3 surface.
 *
 * All five normal product destinations expose one consistent Material navigation drawer.
 * Dashboard page selection is handled directly; no hidden secondary navigation surface remains.
 */
final class ProductDrawerNavigation {
    private static final int NAV_OVERVIEW = 0x8101;
    private static final int NAV_HISTORY = 0x8102;
    private static final int NAV_STATS = 0x8103;
    private static final int DRAWER_DETAILS = 0x8301;
    private static final int DRAWER_SETTINGS = 0x8302;

    // Used only while moving from a secondary product Activity back to the dashboard. CLEAR_TOP
    // recreates the dashboard, so the requested page is consumed when its drawer is attached.
    private static int pendingDashboardNavItem;

    private ProductDrawerNavigation() { }

    static void attach(MaterialBaseActivity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() != 1) return;

        View productRoot = content.getChildAt(0);
        MaterialToolbar toolbar = findFirst(productRoot, MaterialToolbar.class);
        if (toolbar == null) return;

        ProductDashboardActivity dashboard = activity instanceof ProductDashboardActivity
                ? (ProductDashboardActivity) activity : null;

        toolbar.getMenu().clear();
        toolbar.setTitle(R.string.app_name);
        toolbar.setSubtitle((CharSequence) null);
        toolbar.setNavigationIcon(R.drawable.ic_m3_menu);
        toolbar.setNavigationContentDescription(R.string.m3_open_navigation);

        content.removeView(productRoot);
        DrawerLayout drawerLayout = new DrawerLayout(activity);
        drawerLayout.setId(View.generateViewId());
        int onSurface = MaterialUi.color(activity,
                com.google.android.material.R.attr.colorOnSurface, 0xFF202124);
        drawerLayout.setScrimColor(ColorUtils.setAlphaComponent(onSurface, 0x52));
        drawerLayout.setDrawerElevation(MaterialUi.dp(activity, 16));
        content.addView(drawerLayout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        DrawerLayout.LayoutParams productParams = new DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        drawerLayout.addView(productRoot, productParams);

        NavigationView navigationView = new NavigationView(activity);
        navigationView.setId(View.generateViewId());
        navigationView.setBackgroundColor(MaterialUi.color(activity,
                com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF));
        int availableWidth = activity.getResources().getDisplayMetrics().widthPixels - MaterialUi.dp(activity, 56);
        int drawerWidth = Math.min(MaterialUi.dp(activity, 320),
                Math.max(MaterialUi.dp(activity, 240), availableWidth));
        DrawerLayout.LayoutParams drawerParams = new DrawerLayout.LayoutParams(
                drawerWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        drawerParams.gravity = GravityCompat.START;
        drawerLayout.addView(navigationView, drawerParams);

        addHeader(activity, navigationView);
        buildMenu(navigationView.getMenu());
        navigationView.getMenu().setGroupCheckable(1, true, true);
        navigationView.getMenu().setGroupCheckable(2, true, true);

        if (dashboard != null && isDashboardNav(pendingDashboardNavItem)) {
            dashboard.selectNavigationItem(pendingDashboardNavItem);
            pendingDashboardNavItem = 0;
        }
        syncCheckedItem(activity, navigationView);
        applyDrawerInsets(navigationView);

        toolbar.setNavigationOnClickListener(view -> drawerLayout.openDrawer(GravityCompat.START, true));
        final Runnable[] afterDrawerClose = new Runnable[1];

        OnBackPressedCallback drawerBack = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                drawerLayout.closeDrawer(GravityCompat.START, true);
            }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, drawerBack);
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override public void onDrawerOpened(View drawerView) {
                syncCheckedItem(activity, navigationView);
                drawerBack.setEnabled(true);
            }

            @Override public void onDrawerClosed(View drawerView) {
                drawerBack.setEnabled(false);
                Runnable pending = afterDrawerClose[0];
                afterDrawerClose[0] = null;
                if (pending != null) drawerLayout.post(pending);
            }
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (isDashboardNav(id)) {
                navigationView.setCheckedItem(id);
                if (dashboard != null) {
                    closeThenRun(drawerLayout, afterDrawerClose,
                            () -> dashboard.selectNavigationItem(id));
                } else {
                    closeThenRun(drawerLayout, afterDrawerClose,
                            () -> openDashboard(activity, id));
                }
                return true;
            }
            if (id == DRAWER_DETAILS) {
                if (activity instanceof MeterDetailsActivity) {
                    drawerLayout.closeDrawer(GravityCompat.START, true);
                } else {
                    closeThenRun(drawerLayout, afterDrawerClose,
                            () -> openSecondary(activity, MeterDetailsActivity.class));
                }
                return true;
            }
            if (id == DRAWER_SETTINGS) {
                if (activity instanceof SettingsActivity) {
                    drawerLayout.closeDrawer(GravityCompat.START, true);
                } else {
                    closeThenRun(drawerLayout, afterDrawerClose,
                            () -> openSecondary(activity, SettingsActivity.class));
                }
                return true;
            }
            return false;
        });
    }

    private static void closeThenRun(DrawerLayout drawerLayout, Runnable[] afterDrawerClose,
                                     Runnable action) {
        afterDrawerClose[0] = action;
        drawerLayout.closeDrawer(GravityCompat.START, true);
    }

    private static boolean isDashboardNav(int id) {
        return id == NAV_OVERVIEW || id == NAV_HISTORY || id == NAV_STATS;
    }

    private static void buildMenu(Menu menu) {
        menu.add(1, NAV_OVERVIEW, 0, R.string.m3_nav_overview)
                .setIcon(R.drawable.ic_m3_water).setCheckable(true);
        menu.add(1, NAV_HISTORY, 1, R.string.m3_nav_history)
                .setIcon(R.drawable.ic_m3_history).setCheckable(true);
        menu.add(1, NAV_STATS, 2, R.string.m3_nav_statistics)
                .setIcon(R.drawable.ic_m3_statistics).setCheckable(true);
        menu.add(2, DRAWER_DETAILS, 10, R.string.m3_meter_details)
                .setIcon(R.drawable.ic_m3_info).setCheckable(true);
        menu.add(2, DRAWER_SETTINGS, 11, R.string.m3_settings)
                .setIcon(R.drawable.ic_m3_settings).setCheckable(true);
    }

    private static void addHeader(MaterialBaseActivity activity, NavigationView navigationView) {
        LinearLayout header = MaterialUi.vertical(activity);
        int side = MaterialUi.dp(activity, 24);
        header.setPadding(side, MaterialUi.dp(activity, 24), side, MaterialUi.dp(activity, 18));

        TextView title = MaterialUi.title(activity, activity.getString(R.string.app_name));
        title.setTextSize(22f);
        header.addView(title);

        TextView subtitle = MaterialUi.body(activity, activity.getString(R.string.m3_app_subtitle));
        subtitle.setPadding(0, MaterialUi.dp(activity, 6), 0, 0);
        header.addView(subtitle);
        navigationView.addHeaderView(header);
    }

    private static void openDashboard(MaterialBaseActivity activity, int navItemId) {
        pendingDashboardNavItem = navItemId;
        Intent intent = new Intent(activity, ProductDashboardActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        activity.finish();
    }

    private static void openSecondary(MaterialBaseActivity activity,
                                      Class<? extends MaterialBaseActivity> target) {
        Intent intent = new Intent(activity, target)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.finish();
    }

    private static void applyDrawerInsets(NavigationView navigationView) {
        final int startLeft = navigationView.getPaddingLeft();
        final int startTop = navigationView.getPaddingTop();
        final int startRight = navigationView.getPaddingRight();
        final int startBottom = navigationView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(startLeft + bars.left, startTop + bars.top,
                    startRight + bars.right, startBottom + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(navigationView);
    }

    private static void syncCheckedItem(MaterialBaseActivity activity,
                                        NavigationView navigationView) {
        if (activity instanceof MeterDetailsActivity) {
            navigationView.setCheckedItem(DRAWER_DETAILS);
            return;
        }
        if (activity instanceof SettingsActivity) {
            navigationView.setCheckedItem(DRAWER_SETTINGS);
            return;
        }
        if (activity instanceof ProductDashboardActivity) {
            navigationView.setCheckedItem(
                    ((ProductDashboardActivity) activity).currentNavigationItemId());
            return;
        }
        navigationView.setCheckedItem(NAV_OVERVIEW);
    }

    private static <T extends View> T findFirst(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            T found = findFirst(group.getChildAt(index), type);
            if (found != null) return found;
        }
        return null;
    }
}
