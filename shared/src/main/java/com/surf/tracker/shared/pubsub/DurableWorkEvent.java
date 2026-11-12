package com.surf.tracker.shared.pubsub;

/** One logical consumer must retain this work while offline; not a transient broadcast. */
public interface DurableWorkEvent { }
