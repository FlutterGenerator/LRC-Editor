package com.cg.lrceditor;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class LyricReader {

    private String[] lyrics = null;
    private Timestamp[] timestamps = null;

    private SongMetaData songMetaData = new SongMetaData();

    private String errorMsg;

    private File file = null;

    private Context ctx;

    private InputStream in = null;

    LyricReader(String path, String fileName, Context c) {
        this.file = new File(path, fileName);
        this.ctx = c;
    }

    LyricReader(Uri uri, Context c) {
        this.ctx = c;
        try {
            this.in = c.getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            errorMsg = "Oops! " + ctx.getString(R.string.file_not_found_message) + "\n" + e.getMessage();
        }
    }

    boolean readLyrics() {
        try {
            DataInputStream in;
            if (file != null) {
                FileInputStream fis = new FileInputStream(file);
                in = new DataInputStream(fis);
            } else if (this.in != null) {
                in = new DataInputStream(this.in);
            } else {
                errorMsg = "Oops! " + ctx.getString(R.string.failed_to_open_input_stream_message);
                return false;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            StringBuilder contents = new StringBuilder();
            String temp;
            /* Read the file's contents into `contents` */
            while ((temp = br.readLine()) != null) {
                contents.append(temp);
                contents.append('\n');
            }

            in.close();

            List<String> lyrics = new ArrayList<>();
            List<String> timestamps = new ArrayList<>();
            int count, extras;
            int offset = 0;
            int invalidTimestamps;

            /* Loop over each line of `contents` */
            for (String line : contents.toString().split("\\n")) {
                count = 0;
                extras = 0;
                invalidTimestamps = 0;
                /* Loop to find multiple timestamps in a single line (Condensed LRC format) */
                while (true) {
                    /* `count` keeps track of the number of timestamps and `extras` keeps track of three digit milliseconds in a timestamp */
                    temp = line.substring(count * 10 + extras);

                    if (temp.matches("^(\\[\\d\\d[:.]\\d\\d[:.]\\d\\d\\d?]).*$")) {
                        count++;
                        if (temp.charAt(4) - '0' >= 6) { // Invalid timestamp; seconds is >= 60; ignore it
                            invalidTimestamps++;
                            continue;
                        }
                        if (temp.charAt(9) != ']') {
                            extras++;
                        }
                        timestamps.add(temp.substring(1, 9));
                    } else {
                        if (songMetaData.getSongName().isEmpty() && temp.matches("^\\[ti:.*]$")) {
                            songMetaData.setSongName(temp.substring(4, temp.length() - 1).trim());
                            break;
                        } else if (songMetaData.getArtistName().isEmpty() && temp.matches("^\\[ar:.*]$")) {
                            songMetaData.setArtistName(temp.substring(4, temp.length() - 1).trim());
                            break;
                        } else if (songMetaData.getAlbumName().isEmpty() && temp.matches("^\\[al:.*]$")) {
                            songMetaData.setAlbumName(temp.substring(4, temp.length() - 1).trim());
                            break;
                        } else if (songMetaData.getComposerName().isEmpty() && temp.matches("^\\[au:.*]$")) {
                            songMetaData.setComposerName(temp.substring(4, temp.length() - 1).trim());
                            break;
                        } else if (offset == 0 && temp.matches("^\\[offset:.*]$")) {
                            try {
                                offset = Integer.parseInt(temp.substring(8, temp.length() - 1).trim());
                            } catch (NumberFormatException e) { // Ignore the offset if we couldn't scan it
                                e.printStackTrace();
                            }
                        } else break;
                    }
                }

                if (temp.trim().isEmpty())
                    temp = " ";

                count -= invalidTimestamps;

                for (int i = 0; i < count; i++) {
                    lyrics.add(temp);
                }
            }

            if (lyrics.size() == 0) {
                errorMsg = ctx.getString(R.string.could_not_parse_any_lyrics_message);
                return false;
            }

            int size = lyrics.size();
            this.lyrics = new String[size];
            this.timestamps = new Timestamp[size];

            for (int i = 0; i < size; i++) {
                this.lyrics[i] = lyrics.get(i).trim();
                this.timestamps[i] = new Timestamp(timestamps.get(i).trim());

                if (offset != 0) {
                    this.timestamps[i].alterTimestamp(offset);
                }
            }

            sortLyrics();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            errorMsg = "Oops! " + ctx.getString(R.string.file_not_found_message) + "\n" + e.getMessage();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            errorMsg = "Oops! " + ctx.getString(R.string.error_occurred_when_reading_message) + "\n" + e.getMessage();
            return false;
        }

        return true;
    }

    private void sortLyrics() {
        int size = this.timestamps.length;
        long[] priority = new long[size];
        for (int i = 0; i < size; i++)
            priority[i] = this.timestamps[i].toMilliseconds();

        quickSort(0, size - 1, priority);
    }

    private void quickSort(int lowerIndex, int higherIndex, long[] array) {
        int i = lowerIndex;
        int j = higherIndex;

        long pivot = array[lowerIndex + (higherIndex - lowerIndex) / 2];

        while (i <= j) {
            while (array[i] < pivot) {
                i++;
            }
            while (array[j] > pivot) {
                j--;
            }
            if (i <= j) {
                if (array[i] != array[j]) { // Don't swap and change the order of the lyrics if the timestamps are equal
                    swap(i, j, array);
                }
                i++;
                j--;
            }
        }

        if (lowerIndex < j)
            quickSort(lowerIndex, j, array);
        if (i < higherIndex)
            quickSort(i, higherIndex, array);
    }

    private void swap(int i, int j, long[] array) {
        long temp = array[i];
        array[i] = array[j];
        array[j] = temp;

        String s = lyrics[i];
        lyrics[i] = lyrics[j];
        lyrics[j] = s;

        Timestamp t = timestamps[i];
        timestamps[i] = timestamps[j];
        timestamps[j] = t;
    }


    public String[] getLyrics() {
        return this.lyrics;
    }

    Timestamp[] getTimestamps() {
        return this.timestamps;
    }

    String getErrorMsg() {
        return errorMsg;
    }

    SongMetaData getSongMetaData() {
        return songMetaData;
    }
}
