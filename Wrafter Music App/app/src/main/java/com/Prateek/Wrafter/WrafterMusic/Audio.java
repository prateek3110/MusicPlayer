/* Created By : Prateek Sharma
 ******IIT(ISM) Dhanbad******/
package com.Prateek.Wrafter.WrafterMusic;


import java.io.Serializable;


public class Audio implements Serializable {

    private String data;
    private String title;
    private String album;
    private String artist;
    private long album_id;

    public Audio(String data, String title, String album, String artist, long album_id) {
        this.data = data;
        this.title = title;
        this.album = album;
        this.artist = artist;
        this.album_id = album_id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public long getAlbum_id(){return album_id;}

    public void setAlbum_id(long album_id){this.album_id= album_id;}


}

