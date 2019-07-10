package io.github.jklingsporn.vertx.jooq.generate.builder;

/**
 * Step to chose the driver.
 * Created by jensklingsporn on 09.02.18.
 */
public interface ExecutionStep {

    /**
     * @return A {@code DIStep} using a reactive driver for query execution. Only Postgres supported.
     * @see <a href="https://github.com/vietj/reactive-pg-client">reactive-pg-client @ GitHub</a>
     */
    DIStep withReactiveDriver();

}
