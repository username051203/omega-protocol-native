package com.omega.protocol.ui.schedule;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.widget.GridLayout;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.omega.protocol.R;
import com.omega.protocol.adapter.ScheduleItemAdapter;
import com.omega.protocol.engine.RivalEngine;
import com.omega.protocol.model.*;
import com.omega.protocol.viewmodel.ScheduleViewModel;
import com.google.android.material.tabs.TabLayout;
import androidx.recyclerview.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ScheduleFragment extends Fragment {

    private ScheduleViewModel vm;
    private ScheduleItemAdapter poolAdapter;
    private RecyclerView rvPool;
    private TextView tvMissionDate, tvMissionSummary, tvCalMonth, tvSelectedDay;
    private GridLayout calGrid;
    private TabLayout tabs;
    private View panelMission, panelPool;
    private Button btnAutoGen, btnAssignSelected;

    private String selectedDay = null;
    private String calYear;
    private int calMonth;
    private ScheduleViewModel.ScheduleState currentState;

    private static final DateTimeFormatter FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISP = DateTimeFormatter.ofPattern("MMM yyyy");

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, ViewGroup c, Bundle s) {
        return i.inflate(R.layout.fragment_schedule, c, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        bindViews(v);
        setupTabs();
        setupPool();

        LocalDate now = LocalDate.now();
        calYear  = String.valueOf(now.getYear());
        calMonth = now.getMonthValue();

        vm = new ViewModelProvider(this).get(ScheduleViewModel.class);
        vm.getState().observe(getViewLifecycleOwner(), state -> {
            currentState = state;
            renderCalendar(state);
            renderMission(state);
            renderPool(state);
        });
        vm.load();

        v.findViewById(R.id.btnCalPrev).setOnClickListener(x -> { prevMonth(); renderCalendar(currentState); });
        v.findViewById(R.id.btnCalNext).setOnClickListener(x -> { nextMonth(); renderCalendar(currentState); });
        btnAutoGen.setOnClickListener(x -> vm.autoGenerate(() ->
            Toast.makeText(requireContext(), "Schedule regenerated", Toast.LENGTH_SHORT).show()));
        btnAssignSelected.setOnClickListener(x -> assignPoolToSelected());
    }

    @Override public void onResume() { super.onResume(); vm.load(); }

    private void bindViews(View v) {
        tvMissionDate    = v.findViewById(R.id.tvMissionDate);
        tvMissionSummary = v.findViewById(R.id.tvMissionSummary);
        tvCalMonth       = v.findViewById(R.id.tvCalMonth);
        tvSelectedDay    = v.findViewById(R.id.tvSelectedDay);
        calGrid          = v.findViewById(R.id.calGrid);
        tabs             = v.findViewById(R.id.schedTabs);
        panelMission     = v.findViewById(R.id.panelMission);
        panelPool        = v.findViewById(R.id.panelPool);
        rvPool           = v.findViewById(R.id.rvPool);
        btnAutoGen       = v.findViewById(R.id.btnAutoGen);
        btnAssignSelected= v.findViewById(R.id.btnAssignSelected);
    }

    private void setupTabs() {
        tabs.addTab(tabs.newTab().setText("Daily Mission"));
        tabs.addTab(tabs.newTab().setText("Pool / Assign"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) {
                panelMission.setVisibility(t.getPosition() == 0 ? View.VISIBLE : View.GONE);
                panelPool.setVisibility(t.getPosition() == 1 ? View.VISIBLE : View.GONE);
            }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });
    }

    private void setupPool() {
        poolAdapter = new ScheduleItemAdapter();
        rvPool.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvPool.setAdapter(poolAdapter);
        poolAdapter.setListener(new ScheduleItemAdapter.Listener() {
            @Override public void onAssign(ScheduleItem item) {
                if (selectedDay == null) {
                    Toast.makeText(requireContext(), "Tap a day on the calendar first", Toast.LENGTH_SHORT).show();
                    return;
                }
                vm.assignToDay(item.id, selectedDay, () ->
                    Toast.makeText(requireContext(), "Added to " + selectedDay, Toast.LENGTH_SHORT).show());
            }
            @Override public void onDelete(ScheduleItem item) { vm.removeItem(item.id, null); }
        });
    }

    // ── Calendar ──────────────────────────────────────────
    private void renderCalendar(ScheduleViewModel.ScheduleState state) {
        if (state == null || !isAdded()) return;
        LocalDate first = LocalDate.of(Integer.parseInt(calYear), calMonth, 1);
        tvCalMonth.setText(first.format(DISP));
        calGrid.removeAllViews();
        calGrid.setColumnCount(7);

        // Day-of-week headers
        String[] dow = {"Su","Mo","Tu","We","Th","Fr","Sa"};
        for (String d : dow) {
            TextView hdr = makeCalCell(d, 0xFF8890c0, false, false, false);
            hdr.setTextSize(9); calGrid.addView(hdr);
        }

        int firstDow = first.getDayOfWeek().getValue() % 7; // Sun=0
        for (int i = 0; i < firstDow; i++) calGrid.addView(makeCalCell("", 0, false, false, false));

        String todayStr = RivalEngine.today();
        int daysInMonth = first.lengthOfMonth();
        for (int d = 1; d <= daysInMonth; d++) {
            String ds = String.format(Locale.US, "%s-%02d-%02d", calYear, calMonth, d);
            boolean isToday    = ds.equals(todayStr);
            boolean isSel      = ds.equals(selectedDay);
            boolean hasItems   = state.scheduleMap.containsKey(ds) && !state.scheduleMap.get(ds).isEmpty();
            int textColor      = isToday ? 0xFFffcb47 : 0xFFe8eaff;
            TextView cell      = makeCalCell(String.valueOf(d), textColor, isSel, isToday, hasItems);
            final String fds   = ds;
            cell.setOnClickListener(v -> { selectedDay = fds; tvSelectedDay.setText("Selected: " + fds); renderCalendar(currentState); });
            calGrid.addView(cell);
        }
    }

    private TextView makeCalCell(String text, int color, boolean selected, boolean today, boolean hasItems) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(11);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0,12,0,12);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0; lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        tv.setLayoutParams(lp);
        if (selected)   tv.setBackgroundColor(0xFF7c6fff);
        else if (today) tv.setBackgroundColor(0x33ffcb47);
        else            tv.setBackgroundColor(0xFF161929);
        if (hasItems)   tv.setCompoundDrawablePadding(0); // dot indicator via textColor change
        return tv;
    }

    private void prevMonth() { calMonth--; if (calMonth < 1) { calMonth = 12; calYear = String.valueOf(Integer.parseInt(calYear)-1); } }
    private void nextMonth() { calMonth++; if (calMonth > 12){ calMonth = 1;  calYear = String.valueOf(Integer.parseInt(calYear)+1); } }

    // ── Mission ───────────────────────────────────────────
    private void renderMission(ScheduleViewModel.ScheduleState state) {
        if (state == null || !isAdded()) return;
        String today = state.today;
        tvMissionDate.setText(today);
        List<String> ids = state.scheduleMap.getOrDefault(today, Collections.emptyList());
        int total = 0, done = 0;
        for (String id : ids) {
            ScheduleItem it = null;
            for (ScheduleItem x : state.allItems) if (x.id.equals(id)) { it=x; break; }
            if (it != null) { total += it.totalCount(); done += it.doneCount(); }
        }
        tvMissionSummary.setText(done + " / " + total + " passes done · " + ids.size() + " topics");
    }

    // ── Pool ──────────────────────────────────────────────
    private void renderPool(ScheduleViewModel.ScheduleState state) {
        if (state == null || !isAdded()) return;
        // Items not yet assigned to any future day
        Set<String> futureAssigned = new HashSet<>();
        String today = state.today;
        for (Map.Entry<String,List<String>> e : state.scheduleMap.entrySet())
            if (e.getKey().compareTo(today) >= 0)
                futureAssigned.addAll(e.getValue());

        List<ScheduleItem> pool = new ArrayList<>();
        for (ScheduleItem it : state.allItems)
            if (!it.isComplete() && !futureAssigned.contains(it.id)) pool.add(it);

        // Build name maps from subjects
        Map<String,String> tNames = new HashMap<>(), cNames = new HashMap<>();
        for (Subject s : state.subjects)
            for (Chapter c : s.chapters) {
                cNames.put(c.id, c.name);
                for (Topic t : c.topics) tNames.put(t.id, t.name);
            }
        poolAdapter.setData(pool, tNames, cNames);
    }

    private void assignPoolToSelected() {
        if (selectedDay == null) {
            Toast.makeText(requireContext(), "Select a day on the calendar first", Toast.LENGTH_SHORT).show();
            return;
        }
        vm.autoGenerate(() -> Toast.makeText(requireContext(), "Auto-filled from selected day", Toast.LENGTH_SHORT).show());
    }
}
