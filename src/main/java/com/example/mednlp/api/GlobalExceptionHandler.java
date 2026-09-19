package com.example.mednlp.api;

import com.example.mednlp.rules.RuleService.RuleLoadException;
import com.example.mednlp.service.ChartAnalysisService.ChartTooLargeException;
import com.example.mednlp.service.ChartAnalysisService.InvalidChartException;
import com.example.mednlp.uima.EnginePool.EngineBusyException;
import com.example.mednlp.uima.EnginePool.PipelineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Maps failures to RFC 7807 problem responses. Chart text is never echoed back or logged (PHI). */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidChartException.class)
    ProblemDetail invalid(InvalidChartException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ChartTooLargeException.class)
    ProblemDetail tooLarge(ChartTooLargeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, e.getMessage());
    }

    @ExceptionHandler(EngineBusyException.class)
    ProblemDetail busy(EngineBusyException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage() + "; retry shortly");
    }

    @ExceptionHandler(RuleLoadException.class)
    ProblemDetail ruleLoad(RuleLoadException e) {
        log.error("Rule reload failed", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(PipelineException.class)
    ProblemDetail pipeline(PipelineException e) {
        log.error("Pipeline failure", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "The analysis pipeline failed");
    }
}
