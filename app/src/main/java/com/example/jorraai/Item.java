package com.example.jorraai;

public class Item {
    private String name;
    private int imageRes;

    public Item(String name, int imageRes) {
        this.name = name;
        this.imageRes = imageRes;
    }

    public String getName() { return name; }
    public int getImageRes() { return imageRes; }
}

