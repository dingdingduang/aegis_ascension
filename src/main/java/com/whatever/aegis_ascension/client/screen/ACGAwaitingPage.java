package com.whatever.aegis_ascension.client.screen;

/** Page whose actions wait for an authoritative server response. */
interface ACGAwaitingPage extends ACGPage {
    boolean isAwaitingServer();

    void clearAwaiting();
}
