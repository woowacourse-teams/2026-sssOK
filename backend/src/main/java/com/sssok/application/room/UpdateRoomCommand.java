package com.sssok.application.room;

public record UpdateRoomCommand(String name, String uploadPolicy) {

    public boolean isEmpty() {
        return name == null && uploadPolicy == null;
    }
}
