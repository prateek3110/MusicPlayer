/* Created By : Prateek Sharma
 ******IIT(ISM) Dhanbad******/
package com.Prateek.Wrafter.WrafterMusic;

        import android.app.NotificationManager;
        import android.app.PendingIntent;
        import android.app.Service;
        import android.content.BroadcastReceiver;
        import android.content.ContentUris;
        import android.content.Context;
        import android.content.Intent;
        import android.content.IntentFilter;
        import android.graphics.Bitmap;
        import android.graphics.BitmapFactory;
        import android.media.AudioManager;
        import android.media.MediaPlayer;
        import android.media.session.MediaSessionManager;
        import android.net.Uri;
        import android.os.Binder;
        import android.os.Build;
        import android.app.NotificationChannel;
        import android.os.IBinder;
        import android.os.RemoteException;
        import android.provider.MediaStore;
        import android.support.v4.media.MediaMetadataCompat;
        import android.support.v4.media.session.MediaControllerCompat;
        import android.support.v4.media.session.MediaSessionCompat;
        import android.support.v7.app.NotificationCompat;
        import android.telephony.PhoneStateListener;
        import android.telephony.TelephonyManager;
        import android.util.Log;

        import java.io.IOException;
        import java.util.ArrayList;


public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener,

        AudioManager.OnAudioFocusChangeListener {

    public static final String ACTION_PLAY = "com.Prateek.Wrafter.WrafterMusic.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.Prateek.Wrafter.WrafterMusic.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.Prateek.Wrafter.WrafterMusic.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.Prateek.Wrafter.WrafterMusic.ACTION_NEXT";
    public static final String ACTION_STOP = "com.Prateek.Wrafter.WrafterMusic.ACTION_STOP";

    public MediaPlayer mediaPlayer;

    //MediaSession
    public MediaSessionManager mediaSessionManager;
    public MediaSessionCompat mediaSession;
    public MediaControllerCompat.TransportControls transportControls;




    //AudioPlayer notification ID
    private static final int NOTIFICATION_ID = 101;

    //Used to pause/resume MediaPlayer
    protected int resumePosition;

    //AudioFocus
    private AudioManager audioManager;

    public int currentPos;

    // Binder given to clients
    private final IBinder iBinder = new LocalBinder();

    //List of available Audio files
    private ArrayList<Audio> audioList;
    private int audioIndex = -1;
    private Audio activeAudio; //an object on the currently playing audio


    //Handle incoming phone calls
    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;


    /**
     * Service lifecycle methods
     */
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();


        // Perform one-time setup procedures

        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call,
        // Resume on hangup.
        callStateListener();
        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver();
        //Listen for new Audio to play -- BroadcastReceiver
        register_playNewAudio();

    }

    //The system calls this method when an activity, requests the service be started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            System.out.println("1");

            //Load data from SharedPreferences
            StorageUtil storage = new StorageUtil(getApplicationContext());
            audioList = storage.loadAudio();
            audioIndex = storage.loadAudioIndex();

            if (audioIndex != -1 && audioIndex < audioList.size()) {
                //index is in a valid range
                activeAudio = audioList.get(audioIndex);
            } else {
                stopSelf();
            }
        } catch (NullPointerException e) {
            stopSelf();
        }

        //Request audio focus
        if (requestAudioFocus() == false) {
            System.out.println("2");
            //Could not gain focus
            stopSelf();
        }

        if (mediaSessionManager == null) {
            try {
                System.out.println("3");
                initMediaSession();
                initMediaPlayer();
            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
            buildNotification(com.Prateek.Wrafter.WrafterMusic.PlaybackStatus.PLAYING);
        }

        //Handle Intent action from MediaSession.TransportControls
        handleIncomingActions(intent);
        return START_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        System.out.println("4");
        mediaSession.release();
        removeNotification();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        System.out.println("5");
        super.onDestroy();
        if (mediaPlayer != null) {
            System.out.println("6");
            stopMedia();
            mediaPlayer.release();
        }
        removeAudioFocus();
        //Disable the PhoneStateListener
        if (phoneStateListener != null) {
            System.out.println("7");
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        removeNotification();

        //unregister BroadcastReceivers
        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playNewAudio);

        //clear cached playlist
        new StorageUtil(getApplicationContext()).clearCachedAudioPlaylist();
    }

    /**
     * Service Binder
     */
    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {

            System.out.println("8");
            // Return this instance of LocalService so clients can call public methods
            return MediaPlayerService.this;
        }
    }

    /**
     * MediaPlayer callback methods
     */
    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        System.out.println("9");
        //Invoked indicating buffering status of
        //a media resource being streamed over the network.
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        System.out.println("10");
        //Invoked when playback of a media source has completed.
        stopMedia();

        //stop the service
        stopSelf();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        System.out.println("11");
        //Invoked when there has been an error during an asynchronous operation
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + extra);
                break;
        }
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        System.out.println("12");
        //Invoked to communicate some info
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        System.out.println("13");
        //Invoked when the media source is ready for playback.
        playMedia();
        currentPos=(mediaPlayer.getCurrentPosition());
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        System.out.println("14");
        //Invoked indicating the completion of a seek operation.
        playMedia();
    }

    @Override
    public void onAudioFocusChange(int focusState) {
        System.out.println("15");

        //Invoked when the audio focus of the system is updated.
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                if (mediaPlayer == null) initMediaPlayer();
                else if (!mediaPlayer.isPlaying()) resumeMedia();
                mediaPlayer.setVolume(1.0f, 1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                pauseMedia();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mediaPlayer.isPlaying()) mediaPlayer.setVolume(0.1f, 0.1f);
                break;
        }
    }


    /**
     * AudioFocus
     */
    private boolean requestAudioFocus() {
        System.out.println("16");
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            //Focus gained
            return true;
        }
        //Could not gain focus
        return false;
    }

    private boolean removeAudioFocus() {
        System.out.println("17");
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                audioManager.abandonAudioFocus(this);
    }

    /**
     * MediaPlayer actions
     */
    protected void initMediaPlayer() {
        System.out.println("18");
        if (mediaPlayer == null)
            mediaPlayer = new MediaPlayer();//new MediaPlayer instance

        //Set up MediaPlayer event listeners
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);
        //Reset so that the MediaPlayer is not pointing to another data source
        mediaPlayer.reset();


        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            // Set the data source to the mediaFile location
            mediaPlayer.setDataSource(activeAudio.getData());
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mediaPlayer.prepareAsync();

    }

    protected void playMedia() {
        System.out.println("19");
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            buildNotification(PlaybackStatus.PLAYING);
        }
    }

    protected void stopMedia() {
        System.out.println("20");
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            removeNotification();
        }
    }

    protected void pauseMedia() {
        System.out.println("21");
        if (mediaPlayer.isPlaying()) {
            System.out.println("pausemedia");
            mediaPlayer.pause();
            resumePosition = mediaPlayer.getCurrentPosition();
            buildNotification(com.Prateek.Wrafter.WrafterMusic.PlaybackStatus.PAUSED);
        }
    }

    protected void resumeMedia() {
        System.out.println("22");
        if (!mediaPlayer.isPlaying()) {
            System.out.println("resumemedia");
            mediaPlayer.start();
            mediaPlayer.seekTo(resumePosition);
            buildNotification(PlaybackStatus.PLAYING);
        }
    }

    private void skipToNext() {
        System.out.println("23");

        if (audioIndex == audioList.size() - 1) {
            //if last in playlist
            audioIndex = 0;
            activeAudio = audioList.get(audioIndex);
        } else {
            //get next in playlist
            activeAudio = audioList.get(++audioIndex);
        }

        //Update stored index
        new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);

        stopMedia();
        //reset mediaPlayer
        mediaPlayer.reset();
        initMediaPlayer();
    }

    private void skipToPrevious() {
        System.out.println("24");

        if (audioIndex == 0) {
            //if first in playlist
            //set index to the last of audioList
            audioIndex = audioList.size() - 1;
            activeAudio = audioList.get(audioIndex);
        } else {
            //get previous in playlist
            activeAudio = audioList.get(--audioIndex);
        }

        //Update stored index
        new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);

        stopMedia();
        //reset mediaPlayer
        mediaPlayer.reset();
        initMediaPlayer();
    }

    /**
     * ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs
     */

    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            System.out.println("25");
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            pauseMedia();
        }
    };

    private void registerBecomingNoisyReceiver() {
        System.out.println("26");
        //register after getting audio focus
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    /**
     * Handle PhoneState changes
     */
    private void callStateListener() {
        System.out.println("27");
        // Get the telephony manager
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //Starting listening for PhoneState changes
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                System.out.println("28");
                switch (state) {
                    //if at least one call exists or the phone is ringing
                    //pause the MediaPlayer
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        System.out.println("29");
                        if (mediaPlayer != null) {
                            pauseMedia();
                            ongoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        System.out.println("30");
                        // Phone idle. Start playing.
                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
     * MediaSession and Notification actions
     */
    public void initMediaSession() throws RemoteException {
        System.out.println("31");
        if (mediaSessionManager != null) return; //mediaSessionManager exists

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        }
        // Create a new MediaSession
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        //Get MediaSessions transport controls
        transportControls = mediaSession.getController().getTransportControls();
        //set MediaSession -> ready to receive media commands
        mediaSession.setActive(true);
        //indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.Callback.
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        //Set mediaSession's MetaData
        updateMetaData();

        // Attach Callback to receive MediaSession updates
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            // Implement callbacks
            @Override
            public void onPlay() {
                System.out.println("32");
                super.onPlay();

                resumeMedia();
            }

            @Override
            public void onPause() {
                System.out.println("33");
                super.onPause();

                pauseMedia();
            }

            @Override
            public void onSkipToNext() {
                System.out.println("34");
                super.onSkipToNext();

                skipToNext();
                updateMetaData();
                buildNotification(com.Prateek.Wrafter.WrafterMusic.PlaybackStatus.PLAYING);
            }

            @Override
            public void onSkipToPrevious() {
                System.out.println("35");
                super.onSkipToPrevious();

                skipToPrevious();
                updateMetaData();
                buildNotification(com.Prateek.Wrafter.WrafterMusic.PlaybackStatus.PLAYING);
            }

            @Override
            public void onStop() {
                System.out.println("36");
                super.onStop();
                removeNotification();
                //Stop the service
                stopSelf();
            }

            @Override
            public void onSeekTo(long position) {

                System.out.println("37");
                super.onSeekTo(position);
            }
        });
    }

    private void updateMetaData() {
        System.out.println("38");
        Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
        Uri albumArtUri = ContentUris.withAppendedId(sArtworkUri, activeAudio.getAlbum_id());
        Bitmap bitmap= BitmapFactory.decodeResource(getResources(), R.drawable.image1);;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(MediaPlayerService.this.getContentResolver(), albumArtUri);
        }catch(Exception e){}//replace with medias albumArt
        // Update the current metadata
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeAudio.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeAudio.getAlbum())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio.getTitle())
                .build());
    }

    private void buildNotification(com.Prateek.Wrafter.WrafterMusic.PlaybackStatus playbackStatus) {
        System.out.println("39");

        /**
         * Notification actions -> playbackAction()
         *  0 -> Play
         *  1 -> Pause
         *  2 -> Next track
         *  3 -> Previous track
         */

        int notificationAction = android.R.drawable.ic_media_pause;//needs to be initialized
        PendingIntent play_pauseAction = null;

        //Build a new notification according to the current state of the MediaPlayer
        if (playbackStatus == com.Prateek.Wrafter.WrafterMusic.PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause;
            //create the pause action
            play_pauseAction = playbackAction(1);
        } else if (playbackStatus == com.Prateek.Wrafter.WrafterMusic.PlaybackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play;
            //create the play action
            play_pauseAction = playbackAction(0);
        }

        Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
                    Uri albumArtUri = ContentUris.withAppendedId(sArtworkUri, activeAudio.getAlbum_id());
                    Bitmap largeIcon= BitmapFactory.decodeResource(getResources(), R.drawable.image1);;
                    try {
                        largeIcon = MediaStore.Images.Media.getBitmap(MediaPlayerService.this.getContentResolver(), albumArtUri);
                    }catch(Exception e){}


        // Create a new Notification
        NotificationCompat.Builder notificationBuilder = (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                // Hide the timestamp
                .setShowWhen(false)
                // Set the Notification style
                .setStyle(new NotificationCompat.MediaStyle()
                        // Attach our MediaSession token
                        .setMediaSession(mediaSession.getSessionToken())
                        // Show our playback controls in the compat view
                        .setShowActionsInCompactView(0, 1, 2))
                // Set the Notification color
                .setColor(getResources().getColor(R.color.colorAccent))
                // Set the large and small icons
                .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                // Set Notification content information
                .setContentText(activeAudio.getArtist())
                .setContentTitle(activeAudio.getAlbum())
                .setContentInfo(activeAudio.getTitle())
                // Add playback actions
                .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(android.R.drawable.ic_media_next, "next", playbackAction(2));

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Log.d("Hay12","DCM12");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder.setChannelId("com.Prateek.Wrafter.WrafterMusic");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "com.Prateek.Wrafter.WrafterMusic",
                    "Wrafter Music",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
        notificationBuilder.setChannelId("com.Prateek.Wrafter.WrafterMusic");
        notificationManager.notify(NOTIFICATION_ID,notificationBuilder.build());
    }

    private PendingIntent playbackAction(int actionNumber) {
        System.out.println("40");
        System.out.println(actionNumber);
        Intent playbackAction = new Intent(this, MediaPlayerService.class);
        switch (actionNumber) {
            case 0:
                // Play
                System.out.println("PlayAction");
                playbackAction.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 1:
                // Pause
                System.out.println("PauseAction");
                playbackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 2:
                // Next track
                playbackAction.setAction(ACTION_NEXT);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 3:
                // Previous track
                playbackAction.setAction(ACTION_PREVIOUS);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            default:
                break;
        }
        return null;
    }

    private void removeNotification() {
        System.out.println("41");
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void handleIncomingActions(Intent playbackAction) {
        System.out.println("42");
        if (playbackAction == null || playbackAction.getAction() == null) return;

        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            System.out.println("Play");
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            System.out.println("Pause");
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)) {
            System.out.println("next");
            transportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)) {
            System.out.println("Previous");
            transportControls.skipToPrevious();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            System.out.println("Stop");
            transportControls.stop();
        }
    }


    /**
     * Play new Audio
     */
    private BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            System.out.println("43");

            //Get the new media index form SharedPreferences
            audioIndex = new StorageUtil(getApplicationContext()).loadAudioIndex();
            if (audioIndex != -1 && audioIndex < audioList.size()) {
                //index is in a valid range
                activeAudio = audioList.get(audioIndex);
            } else {
                stopSelf();
            }

            //A PLAY_NEW_AUDIO action received
            //reset mediaPlayer to play the new Audio
            stopMedia();
            mediaPlayer.reset();
            initMediaPlayer();
            updateMetaData();
            buildNotification(com.Prateek.Wrafter.WrafterMusic.PlaybackStatus.PLAYING);
        }
    };

    private void register_playNewAudio() {
        System.out.println("44");
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(MainActivity.Broadcast_PLAY_NEW_AUDIO);
        registerReceiver(playNewAudio, filter);

    }

    public int seekBarGetCurrentPosition(){    //This method is created to get SongCurrentPosition from mediaplayer for seekbar
        if(mediaPlayer!=null&&mediaPlayer.isPlaying()){
            currentPos=mediaPlayer.getCurrentPosition();
        }
        return currentPos;
    }


}
