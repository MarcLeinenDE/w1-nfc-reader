package de.marcleinen.engineeringlab.qalcosonic;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Compact period/custom-range chooser shared by History and Statistics. */
final class HistoryRangePickerUi {
    private HistoryRangePickerUi() { }

    static void show(MaterialBaseActivity activity,
                     HistoryPeriodNavigator navigator,
                     boolean allowScaleSelection,
                     Runnable changed) {
        if (activity == null || navigator == null || changed == null) return;
        if (allowScaleSelection) {
            CharSequence[] labels = {
                    activity.getString(R.string.v2_period_day),
                    activity.getString(R.string.v2_period_month),
                    activity.getString(R.string.v2_period_year),
                    activity.getString(R.string.v2_custom_range),
                    activity.getString(R.string.v2_all_periods)
            };
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.v2_choose_period)
                    .setItems(labels, (dialog, which) -> {
                        if (which == 0) selectCalendar(activity, navigator,
                                HistoryPeriodNavigator.Scale.DAY, changed);
                        else if (which == 1) selectCalendar(activity, navigator,
                                HistoryPeriodNavigator.Scale.MONTH, changed);
                        else if (which == 2) selectCalendar(activity, navigator,
                                HistoryPeriodNavigator.Scale.YEAR, changed);
                        else if (which == 3) showCustomRange(activity, navigator, changed);
                        else {
                            navigator.setAllPeriods(true);
                            changed.run();
                        }
                    })
                    .show();
            return;
        }

        CharSequence[] labels = {
                activity.getString(R.string.v2_choose_calendar_period),
                activity.getString(R.string.v2_custom_range),
                activity.getString(R.string.v2_all_periods)
        };
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.v2_choose_period)
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) showDatePicker(activity, navigator, changed);
                    else if (which == 1) showCustomRange(activity, navigator, changed);
                    else {
                        navigator.setAllPeriods(true);
                        changed.run();
                    }
                })
                .show();
    }

    private static void selectCalendar(MaterialBaseActivity activity,
                                       HistoryPeriodNavigator navigator,
                                       HistoryPeriodNavigator.Scale scale,
                                       Runnable changed) {
        navigator.selectScale(scale);
        showDatePicker(activity, navigator, changed);
    }

    private static void showDatePicker(MaterialBaseActivity activity,
                                       HistoryPeriodNavigator navigator,
                                       Runnable changed) {
        new DatePickerDialog(activity, (view, year, month, day) -> {
            navigator.setDate(year, month, day);
            changed.run();
        }, navigator.year(), navigator.month0(), navigator.day()).show();
    }

    private static void showCustomRange(MaterialBaseActivity activity,
                                        HistoryPeriodNavigator navigator,
                                        Runnable changed) {
        long now = System.currentTimeMillis();
        long defaultStart = navigator.customRange()
                ? navigator.customStartMs() : now - 24L * 60L * 60L * 1000L;
        long defaultEnd = navigator.customRange() ? navigator.customEndMs() : now;
        final long[] start = {minute(defaultStart)};
        final long[] end = {minute(defaultEnd)};

        LinearLayout content = MaterialUi.vertical(activity);
        int pad = MaterialUi.dp(activity, 8);
        content.setPadding(pad, pad, pad, 0);
        MaterialButton from = new MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        MaterialButton to = new MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        refreshRangeButtons(activity, from, to, start[0], end[0]);
        from.setOnClickListener(v -> pickDateTime(activity, start[0], value -> {
            start[0] = value;
            refreshRangeButtons(activity, from, to, start[0], end[0]);
        }));
        to.setOnClickListener(v -> pickDateTime(activity, end[0], value -> {
            end[0] = value;
            refreshRangeButtons(activity, from, to, start[0], end[0]);
        }));
        content.addView(from, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams toLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        toLp.topMargin = MaterialUi.dp(activity, 8);
        content.addView(to, toLp);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.v2_custom_range)
                .setView(content)
                .setNegativeButton(R.string.m3_cancel, null)
                .setPositiveButton(R.string.v2_apply, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (end[0] <= start[0]) {
                        to.setError(activity.getString(R.string.v2_range_end_after_start));
                        return;
                    }
                    navigator.setCustomRange(start[0], end[0]);
                    dialog.dismiss();
                    changed.run();
                }));
        dialog.show();
    }

    private static void pickDateTime(MaterialBaseActivity activity, long initial,
                                     LongConsumer selected) {
        Calendar value = Calendar.getInstance();
        value.setTimeInMillis(initial);
        new DatePickerDialog(activity, (dateView, year, month, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.clear();
            picked.set(year, month, day,
                    value.get(Calendar.HOUR_OF_DAY), value.get(Calendar.MINUTE), 0);
            new TimePickerDialog(activity, (timeView, hour, minute) -> {
                picked.set(Calendar.HOUR_OF_DAY, hour);
                picked.set(Calendar.MINUTE, minute);
                selected.accept(picked.getTimeInMillis());
            }, value.get(Calendar.HOUR_OF_DAY), value.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(activity)).show();
        }, value.get(Calendar.YEAR), value.get(Calendar.MONTH),
                value.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static void refreshRangeButtons(MaterialBaseActivity activity,
                                            MaterialButton from, MaterialButton to,
                                            long start, long end) {
        Locale locale = activity.getResources().getConfiguration().getLocales().get(0);
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale);
        from.setText(activity.getString(R.string.v2_range_from, format.format(new Date(start))));
        to.setText(activity.getString(R.string.v2_range_to, format.format(new Date(end))));
        to.setError(null);
    }

    private static long minute(long value) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(value);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private interface LongConsumer {
        void accept(long value);
    }
}
