package com.sssok.application.room;

public record UpdateRoomCommand(String name, String uploadPolicy, Integer expiryDays) {

    public boolean isEmpty() {
        return name == null && uploadPolicy == null && expiryDays == null;
    }
}
