package com.watchtogether.app;

/** Second in-process CoView client for developer testing without Dual Space. */
public class SyncServiceTest2 extends SyncService {
    @Override public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        if (intent != null) intent.putExtra("client_slot", 2);
        return super.onStartCommand(intent, flags, startId);
    }
}
