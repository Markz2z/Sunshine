package cristo.ustczmk.com.sunshine_new;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ustczmk on 16/1/31.
 */

public class ForecastFragment extends android.support.v4.app.Fragment{

    ArrayAdapter<String> mForecastAdapter = null;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(id==R.id.action_refresh) {
            new FetchWeatherTask().execute("94303");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        String[] forecastArray = {
                "Today - Sunny - 88/63",
                "Tomorrow - Foggy - 70/40",
                "Weds - Cloudy - 72/63",
                "Thurs - Asteroids - 75/65",
                "Fri - Heavy Rain - 65/56",
                "Sat - HELP TRAPPED IN WEATHERSTATION - 60/51",
                "Sun - Sunny - 80/68"
        };

        List<String> weekForecast = new ArrayList<String>(Arrays.asList(forecastArray));

        ListView listView = (ListView)rootView.findViewById(R.id.listview_forecast);

        mForecastAdapter =
                new ArrayAdapter<String>(getActivity(),
                        R.layout.list_item_forecast,
                        R.id.list_item_forecast_textview,
                        weekForecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String forecast = mForecastAdapter.getItem(position);
                Intent intent = new Intent(getActivity(), DetailActivity.class)
                                    .putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(intent);
            }
        });
        return rootView;
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]>{

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        /* The date/time conversion code is going to be moved outside the asynctask later,
        *  so for convenience we're breaking it out into its own method now.
        */
        private String getReadableDateString(long time){
            // Because the API returns a unix timestamp (measured in seconds),
            // it must be converted to milliseconds in order to be converted to valid date.
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatHighLows(double high, double low) {
            // For presentation, assume the user doesn't care about tenths of a degree.
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = sharedPrefs.getString(getString(R.string.pref_units_label), getString(R.string.pref_units_metric));
            if(unitType.equals(getString(R.string.pref_units_imperial))){
                high = (high * 1.8) + 32;
                low = (low * 1.8) +32;
            }else if(!unitType.equals(getString(R.string.pref_units_metric))){
                Log.d(LOG_TAG, "Unit type not found: " + unitType);
            }
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);
            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         *
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.

            Time dayTime = new Time();
            dayTime.set(14, 10, 2015);
            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            //dayTime = new Time();

            String[] resultStrs = new String[numDays];
            for(int i = 0; i < weatherArray.length(); i++) {
                // For now, using the format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // The date/time is returned as a long.  We need to convert that
                // into something human-readable, since most people won't read "1400356800" as
                // "this saturday".
                long dateTime = 0;
                // Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay+i);
                day = getReadableDateString(dateTime);

                // description is in a child array called "weather", which is 1 element long.
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            for (String s : resultStrs) {
                Log.v(LOG_TAG, "Forecast entry: " + s);
            }
            return resultStrs;

        }

        @Override
        protected void onPostExecute(String[] result) {
            if(result!=null) {
                mForecastAdapter.clear();
                for(String dayForecastStr : result) {
                    mForecastAdapter.add(dayForecastStr);
                }
            }
        }

        @Override
        protected String[] doInBackground(String... params) {
            String location = params[0];

            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;


            String forecastJsonStr = null;
            String format  = "json";
            String units   = "metric";
            int    numDays = 7;
            String appid   = "4d96f9a5327b7e570c5b64a10ec9dfcf";
            try {

                final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";
                final String APPID = "appid";

                Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, params[0] + ", usa")
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM,  units)
                        .appendQueryParameter(DAYS_PARAM,   Integer.toString(numDays))
                        .appendQueryParameter(APPID, appid)
                        .build();

                URL url = new URL(builtUri.toString());

                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                InputStream inps = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();

                if(inps==null) {
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader((inps)));

                String line;
                while((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }

                if(buffer.length() == 0) {
                    forecastJsonStr = null;
                }

                forecastJsonStr = buffer.toString();

                Log.v(LOG_TAG, forecastJsonStr);
            }catch (MalformedURLException e) {
                e.printStackTrace();
            }catch(IOException e){
                e.printStackTrace();
            }finally {
                if(urlConnection!=null){
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e("PlaceholderFragment", "Error closing stream", e);
                    }
                }
            }
            try {
                return getWeatherDataFromJson(forecastJsonStr, 7);
            }catch(JSONException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

}
