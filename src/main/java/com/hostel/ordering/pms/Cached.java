package com.hostel.ordering.pms;

/** A cached value, the clock time it was loaded, and whether it is being served past its TTL. */
public record Cached<T>(T value, long fetchedAtMs, boolean stale) {}
