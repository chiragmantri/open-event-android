package org.fossasia.openevent.activities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.fossasia.openevent.R;
import org.fossasia.openevent.adapters.SpeakersListAdapter;
import org.fossasia.openevent.data.Session;
import org.fossasia.openevent.data.Speaker;
import org.fossasia.openevent.dbutils.DbSingleton;
import org.fossasia.openevent.receivers.NotificationAlarmReceiver;
import org.fossasia.openevent.utils.ConstantStrings;
import org.fossasia.openevent.utils.ISO8601Date;
import org.fossasia.openevent.widget.BookmarkWidgetProvider;

import java.util.Calendar;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import timber.log.Timber;

/**
 * User: MananWason
 * Date: 08-07-2015
 */
public class SessionDetailActivity extends BaseActivity {
    private static final String TAG = "Session Detail";

    private SpeakersListAdapter adapter;

    private Session session;

    private String timings;

    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.title_session) TextView text_title;
    @BindView(R.id.subtitle_session) TextView text_subtitle;
    @BindView(R.id.tv_time) TextView text_time;
    @BindView(R.id.track) TextView text_track;
    @BindView(R.id.tv_location) TextView text_room1;
    @BindView(R.id.tv_abstract_text) TextView summary;
    @BindView(R.id.tv_description) TextView descrip;
    @BindView(R.id.list_speakerss) RecyclerView speakersRecyclerView;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sessions_detail);

        ButterKnife.bind(this);

        DbSingleton dbSingleton = DbSingleton.getInstance();
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        final String title = getIntent().getStringExtra(ConstantStrings.SESSION);
        String trackName = getIntent().getStringExtra(ConstantStrings.TRACK);
        Timber.tag(TAG).d(title);

        final List<Speaker> speakers = dbSingleton.getSpeakersbySessionName(title);
        session = dbSingleton.getSessionbySessionname(title);

        text_room1.setText((dbSingleton.getMicrolocationById(session.getMicrolocation().getId())).getName());

        text_title.setText(title);
        text_subtitle.setText(session.getSubtitle());
        text_track.setText(trackName);

        String start = ISO8601Date.getTimeZoneDateString(ISO8601Date.getDateObject(session.getStartTime()));
        String end = ISO8601Date.getTimeZoneDateString(ISO8601Date.getDateObject(session.getEndTime()));

        if (TextUtils.isEmpty(start) && TextUtils.isEmpty(end)) {
            text_time.setText(R.string.time_not_specified);
        } else {
            timings = start + " - " + end;
            text_time.setText(timings);
            text_time.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_INSERT);
                    intent.setType("vnd.android.cursor.item/event");
                    intent.putExtra(CalendarContract.Events.TITLE, title);
                    intent.putExtra(CalendarContract.Events.DESCRIPTION, session.getDescription());
                    intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ISO8601Date.getDateObject(session.getStartTime()).getTime());
                    intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,
                            ISO8601Date.getDateObject(session.getEndTime()).getTime());
                    startActivity(intent);

                }
            });
        }
        summary.setText(session.getSummary());

        Spanned result;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            result = Html.fromHtml(session.getDescription(),Html.FROM_HTML_MODE_LEGACY);
        } else {
            result = Html.fromHtml(session.getDescription());
        }
        descrip.setText(result);

        adapter = new SpeakersListAdapter(speakers, this);

        speakersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        speakersRecyclerView.setAdapter(adapter);
        adapter.setOnClickListener(new SpeakersListAdapter.SetOnClickListener() {
            @Override
            public void onItemClick(int position, View view) {

                Speaker model = adapter.getItem(position);
                String speakerName = model.getName();
                Intent intent = new Intent(getApplicationContext(), SpeakersActivity.class);
                intent.putExtra(Speaker.SPEAKER, speakerName);
                startActivity(intent);
            }
        });
        speakersRecyclerView.setItemAnimator(new DefaultItemAnimator());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.bookmark_status:
                DbSingleton dbSingleton = DbSingleton.getInstance();
                if (dbSingleton.isBookmarked(session.getId())) {
                    Timber.tag(TAG).d("Bookmark Removed");
                    dbSingleton.deleteBookmarks(session.getId());
                    item.setIcon(R.drawable.ic_bookmark_outline_white_24dp);
                } else {
                    Timber.tag(TAG).d("Bookmarked");
                    dbSingleton.addBookmarks(session.getId());
                    item.setIcon(R.drawable.ic_bookmark_white_24dp);
                    createNotification();
                }
                sendBroadcast(new Intent(BookmarkWidgetProvider.ACTION_UPDATE));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_session_detail, menu);
        DbSingleton dbSingleton = DbSingleton.getInstance();
        MenuItem item = menu.findItem(R.id.bookmark_status);
        if (dbSingleton.isBookmarked(session.getId())) {
            Timber.tag(TAG).d("Bookmarked");
            item.setIcon(R.drawable.ic_bookmark_white_24dp);
        } else {
            Timber.tag(TAG).d("Bookmark Removed");
            item.setIcon(R.drawable.ic_bookmark_outline_white_24dp);
        }
        return super.onCreateOptionsMenu(menu);
    }

    public void createNotification() {

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(ISO8601Date.getTimeZoneDate(ISO8601Date.getDateObject(session.getStartTime())));

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        Integer pref_result = Integer.parseInt(sharedPrefs.getString("notification", "10"));
        if (pref_result.equals(1)) {
            calendar.add(Calendar.HOUR, -1);
        } else if (pref_result.equals(12)) {
            calendar.add(Calendar.HOUR, -12);
        } else {
            calendar.add(Calendar.MINUTE, -10);
        }
        Intent myIntent = new Intent(this, NotificationAlarmReceiver.class);
        myIntent.putExtra(ConstantStrings.SESSION, session.getId());
        myIntent.putExtra(ConstantStrings.SESSION_TIMING, timings);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, myIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC, calendar.getTimeInMillis(), pendingIntent);
    }
}