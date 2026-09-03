package com.jarvis.commerce.storage;

public class StorageUnavailableException extends RuntimeException {
    public StorageUnavailableException(String message) { super(message); }
    public StorageUnavailableException(String message, Throwable cause) { super(message, cause); }
}
