package com.infiRay.XthermDemo;

import static java.sql.Types.NULL;

/**
 * Created by Administrator on 2018/1/29 0029.
 */

public class SettingModel {
    private int image;
    private int setimage;
    private int title;

    public SettingModel(int image, int title, int setimage) {
        super();
        if(image!=NULL) {
            this.image = image;
        }
        this.title = title;
        this.setimage = setimage;
    }

    public int getImage() {
        return image;
    }

    public void setImage(int image) {
        this.image = image;
    }

    public int getTitle() {
        return title;
    }

    public void setTitle(int title) {
        this.title = title;
    }

    public int getSetimage(){return  setimage;}

    public void getSetimage(int setimage){this.setimage = setimage;}

}

