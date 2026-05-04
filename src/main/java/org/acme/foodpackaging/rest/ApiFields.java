package org.acme.foodpackaging.rest;

public final class ApiFields {
    private ApiFields() {}

    public static final String ERROR = "error";
    public static final String STATUS = "status";
    public static final String SUCCESS = "success";
    public static final String MESSAGE = "message";
    public static final String SESSION_ID = "sessionId";
    public static final String LINE_ID = "lineId";
    public static final String PATH = "path";
    public static final String EXCEPTION = "exception";
    public static final String ROOT_CAUSE = "rootCause";
    public static final String REFRESH_OK = "Schedule refreshed";
    // Messages
    public static final String NO_SCHEDULE_LOADED = "No schedule loaded";
    public static final String NO_PM_LOG_ROWS_FOR_BATCH = "No PM_LOG marking rows for batch";
    public static final String NO_DATA_LOADED = "No data loaded";
    public static final String SESSION_ID_REQUIRED = "Session ID is required";
    public static final String MS_LOG_INSERT_FAILED = "Failed to insert MS_LOG rows";
    public static final String MS_LOG_UPDATE_CAMERA_END_FAILED = "Failed to update camera end events in MS_LOG";
}

