package com.tanalytics.query.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class QueryExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(QueryExceptionHandler.class);

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDataAccessFailure(DataAccessException ex) {
        log.error("DuckDB query failed", ex);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Analytics data store is temporarily unavailable."
        );
        detail.setTitle("Analytics query dependency unavailable");
        detail.setProperty("errorCode", "QUERY_DATASTORE_UNAVAILABLE");
        return detail;
    }
}
