package com.tanalytics.query.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class QueryExceptionHandler {

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDataAccessFailure(DataAccessException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Analytics data store is temporarily unavailable."
        );
        detail.setTitle("Analytics query dependency unavailable");
        detail.setProperty("errorCode", "QUERY_DATASTORE_UNAVAILABLE");
        return detail;
    }
}
