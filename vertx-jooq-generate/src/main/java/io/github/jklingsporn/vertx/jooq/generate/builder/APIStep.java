package io.github.jklingsporn.vertx.jooq.generate.builder;

/**
 * Step to chose the API.
 * @author jensklingsporn
 */
public interface APIStep {

    /**
     * @return an {@code ExecutionStep} based on the RX Java 2 API.
     */
    public ExecutionStep withRXAPI();

}
