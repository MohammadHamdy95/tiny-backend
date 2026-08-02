package app.hamdy.shortener.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import app.hamdy.shortener.service.InvalidLinkException;
import app.hamdy.shortener.service.LinkNotFoundException;
import app.hamdy.shortener.service.SlugTakenException;

/**
 * RFC 9457 problem details for service errors; framework errors are
 * covered by spring.mvc.problemdetails.enabled.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(LinkNotFoundException.class)
    public ProblemDetail notFound(LinkNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Link not found");
        problem.setDetail(e.getMessage());
        return problem;
    }

    @ExceptionHandler(SlugTakenException.class)
    public ProblemDetail slugTaken(SlugTakenException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Slug unavailable");
        problem.setDetail(e.getMessage());
        return problem;
    }

    @ExceptionHandler(InvalidLinkException.class)
    public ProblemDetail invalid(InvalidLinkException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid link");
        problem.setDetail(e.getMessage());
        return problem;
    }
}
