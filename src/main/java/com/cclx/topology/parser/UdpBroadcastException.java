package com.cclx.topology.parser;

public class UdpBroadcastException extends RuntimeException {
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
