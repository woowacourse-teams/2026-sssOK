package com.sssok.application.room;

public record UpdateRoomCommand(String name, String uploadPolicy, Integer expiryHours) {

    public boolean isEmpty() {
        return name == null && uploadPolicy == null && expiryHours == null;
    }
}
