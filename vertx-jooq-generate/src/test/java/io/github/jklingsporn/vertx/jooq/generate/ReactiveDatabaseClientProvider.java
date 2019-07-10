//package io.github.jklingsporn.vertx.jooq.generate;
//
//import io.vertx.core.Vertx;
//import io.vertx.sqlclient.Pool;
//import io.vertx.sqlclient.PoolOptions;
//
///**
// * Created by jensklingsporn on 15.02.18.
// */
//public class ReactiveDatabaseClientProvider {
//
//    private static ReactiveDatabaseClientProvider INSTANCE;
//    public static ReactiveDatabaseClientProvider getInstance() {
//        return INSTANCE == null ? INSTANCE = new ReactiveDatabaseClientProvider() : INSTANCE;
//    }
//
//    private final Vertx vertx;
//    private final PgClient pgClient;
//    private final io.reactiverse.reactivex.pgclient.PgClient rxPgClient;
//
//    private ReactiveDatabaseClientProvider() {
//        this.vertx = Vertx.vertx();
//        this.pgClient = PgClient.pool(vertx, getOptions());
//        this.rxPgClient = io.reactiverse.reactivex.pgclient.PgClient.pool(getOptions());
//    }
//
//    public Pool getClient() {
//        return pgClient;
//    }
//
//    private PoolOptions getOptions() {
//        return new PoolOptions(new PgConnectOptions().setHost("127.0.0.1")
//                .setPort(5432)
//                .setUser("vertx")
//                .setDatabase("postgres")
//                .setPassword("password"))
//                ;
//    }
//
//    public io.reactiverse.reactivex.pgclient.PgClient rxGetClient() {
//        return rxPgClient;
//    }
//
//
//    public Vertx getVertx() {
//        return vertx;
//    }
//}
