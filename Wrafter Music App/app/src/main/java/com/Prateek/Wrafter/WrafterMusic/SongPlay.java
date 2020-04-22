/* Created By : Prateek Sharma
 ******IIT(ISM) Dhanbad******/
package com.Prateek.Wrafter.WrafterMusic;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import static android.content.ContentValues.TAG;

public class SongPlay extends Activity {
    String str;
    ContentResolver contentResolver;

    Cursor cursor;

    Uri uri;
    TextView t1,t2;
    ImageView imgV;
    public TextView textvl;
    ImageView imgb;
    MediaPlayer mPlayer;
    Handler mHandler;
    SeekBar mSeekBar;
    private Runnable mRunnable;
    AudioManager mAudioManager;
    String SongTitle ;
    String SongArtist;
    boolean serviceBound = false;
    AudioManager audioManager;
    private static final int NOTIFICATION_ID = 101;
    private MediaPlayerService player;

    public static final String Broadcast_PLAY_NEW_AUDIO = "com.Prateek.Wrafter.WrafterMusic.PlayNewAudio";

    private Audio activeAudio;
    ArrayList<Audio> audioList =new ArrayList<Audio>();
    private int audioIndex;
    private int SongDuration;
    private int Duration;
    private int currentPosition;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        System.out.println("a1");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song_play);
        try {

            //Load data from SharedPreferences
            StorageUtil storage = new StorageUtil(getApplicationContext());
            audioList = storage.loadAudio();
            audioIndex = storage.loadAudioIndex();

            if (audioIndex != -1 && audioIndex < audioList.size()) {
                //index is in a valid range
                activeAudio = audioList.get(audioIndex);
            } else {
                onDestroy();
            }
        } catch (NullPointerException e) {
            onDestroy();
        }

        if (Intent.ACTION_VIEW.equals(getIntent().getAction()))
        {
            str = (getIntent().getData().getPath());

            // do what you want with the file...
        }else {
            Bundle extras = getIntent().getExtras();
            str = extras.getString("SongData");
        }

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        t1= (TextView)findViewById(R.id.textView);
        t2= (TextView)findViewById(R.id.textView2);
        imgb=(ImageView)findViewById(R.id.imageView5);
        textvl=(TextView)findViewById(R.id.textView4);
        mSeekBar=(SeekBar)findViewById(R.id.seekBar);

        imgV = (ImageView)findViewById(R.id.imageView2) ;

        SongPrep();
        playAudio(audioIndex);


        // Click listener for playing button
        imgb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playIt();
            }
        });

        }


    public void SongPrep()
    {System.out.println("a2");
        contentResolver = SongPlay.this.getContentResolver();

        uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String[] STAR={"*"};
        cursor = contentResolver.query(
                uri,
                STAR,
                selection,
                null,
                null
        );

        if (cursor == null) {

            Toast.makeText(SongPlay.this, "Something Went Wrong.", Toast.LENGTH_SHORT);

        } else if (!cursor.moveToFirst()) {

            Toast.makeText(SongPlay.this, "No Music Found on SD Card.", Toast.LENGTH_SHORT);

        } else {

            int Title = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int Data = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            int Artist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int ID = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int Duration = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
            int AlbumId = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);


            do {

                String SongData = cursor.getString(Data);

                if(SongData.equals(str))
                {
                    SongTitle = cursor.getString(Title);
                    SongArtist = cursor.getString(Artist);
                    // String SongID = cursor.getString(ID);
                    SongDuration = Integer.parseInt(cursor.getString(Duration));
                    long SongAlbumID = Long.parseLong(cursor.getString(AlbumId));
                    SongDuration=SongDuration/1000;
                    textvl.setText("0:0 | "+Integer.toString(SongDuration/60)+":"+Integer.toString(SongDuration%60));

                    Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
                    Uri albumArtUri = ContentUris.withAppendedId(sArtworkUri, SongAlbumID);
                    Bitmap bitmap= BitmapFactory.decodeResource(getResources(), R.drawable.image1);;
                    try {
                        bitmap = MediaStore.Images.Media.getBitmap(SongPlay.this.getContentResolver(), albumArtUri);
                    }catch(Exception e){}

                    t1.setText(SongTitle);
                    t2.setText(SongArtist);
                    imgV.setImageBitmap(bitmap);

                    break;
                }


            } while (cursor.moveToNext());
        }

    }

    private void playAudio(int audioIndex) {
        //Check is service is active
        if (!serviceBound) {
            System.out.println("a3");
            //Store Serializable audioList to SharedPreferences
            StorageUtil storage = new StorageUtil(getApplicationContext());
            storage.storeAudio(audioList);
            storage.storeAudioIndex(audioIndex);
            Intent playerIntent = new Intent(this, MediaPlayerService.class);
            startService(playerIntent);
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            imgb.setImageResource(R.drawable.pauseim);
        } else {
            System.out.println("a4");
            //Store the new audioIndex to SharedPreferences
            StorageUtil storage = new StorageUtil(getApplicationContext());
            storage.storeAudioIndex(audioIndex);

            //Service is active
            //Send a broadcast to the service -> PLAY_NEW_AUDIO
            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
            sendBroadcast(broadcastIntent);
            imgb.setImageResource(R.drawable.pauseim);
        }
    }

    public int getMediaPlayerDuration(){
        if(serviceBound){
            if(player.mediaPlayer!=null){
                Duration=songDuration();
            }
        }
        return Duration;
    }
    //This method get MediaPlayerCurrent Position from service
    public int getMediaPlayerCurrentPosition(){
        System.out.println("a5");
        if(serviceBound){
            if(player.mediaPlayer!=null){
                currentPosition=player.seekBarGetCurrentPosition();
            }
        }
        return currentPosition;
    }
    //This method is used to update seekBar status by getting Player Current Position from service.
    public void getSeekBarStatus(){
        System.out.println("a6");
        new Thread(new Runnable() {
            @Override
            public void run() {

                int total=songDuration();
                int CurrentPosition=0;
                mSeekBar.setMax(total);

                while(CurrentPosition<total){
                    try {
                        Thread.sleep(1000);
                        CurrentPosition=getMediaPlayerCurrentPosition();
                        Log.d(TAG,String.valueOf(CurrentPosition));
                    } catch (InterruptedException e) {
                        return;
                    }mSeekBar.setProgress(CurrentPosition);
                    if(player.mediaPlayer.isPlaying()) imgb.setImageResource(R.drawable.pauseim);
                    else if(!player.mediaPlayer.isPlaying() && player.mediaPlayer!=null) imgb.setImageResource(R.drawable.playim);
                }
            }
        }).start();

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final SeekBar seekBar, int ProgressValue, boolean fromUser) {
                // if(fromUser){
                //   mp.seekTo(ProgressValue);
                //}
                if(player.mediaPlayer != null && fromUser){
                    player.mediaPlayer.seekTo(ProgressValue);
                }
                final long Minutes=((ProgressValue/1000)/60);
                final int Seconds=((ProgressValue/1000)%60);
                textvl.setText(Minutes+":"+Seconds+" | "+Integer.toString(SongDuration/60)+":"+Integer.toString(SongDuration%60));


            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean("serviceStatus", serviceBound);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean("serviceStatus");
    }

    //Binding this Client to the AudioPlayer Service
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            player = binder.getService();
            serviceBound = true;
            getSeekBarStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            //service is active
            player.stopSelf();
        }
    }

    public void playIt()
    {

        if(player.mediaPlayer.isPlaying())
        {
            pause();

        }else
        {
            play();

        }
    }

    public void play()
    {
        player.playMedia();
        imgb.setImageResource(R.drawable.pauseim);

    }

    public void pause()
    {
        imgb.setImageResource(R.drawable.playim);
        player.pauseMedia();

    }

    protected void getAudioStats(){
        System.out.println("audiostat");
        int duration  = SongDuration; // In milliseconds
        int pass =  player.currentPos;

        textvl.setText(Integer.toString(pass/60)+":"+Integer.toString(pass%60)+" | "+Integer.toString(duration/60)+":"+Integer.toString(duration%60));
    }

    protected void initializeSeekBar(){
        mSeekBar.setMax(player.mediaPlayer.getDuration());

        SongPlay.this.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if(player.mediaPlayer != null){
                    getAudioStats();
                    seekme();

                }
                mHandler.postDelayed(this, 1);
            }
        });
    }

    public void seekme(){

        int mcurpos=player.mediaPlayer.getCurrentPosition();
        mSeekBar.setProgress(mcurpos);

    }

    public int songDuration(){
        MediaMetadataRetriever mmr=new MediaMetadataRetriever();
        mmr.setDataSource(str);
        String Dur=mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        Integer Duration=Integer.parseInt(Dur);
        return Duration;


    }

}
