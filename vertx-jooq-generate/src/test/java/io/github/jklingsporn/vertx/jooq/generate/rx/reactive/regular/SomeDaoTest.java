package io.github.jklingsporn.vertx.jooq.generate.rx.reactive.regular;

import generated.rx.reactive.regular.vertx.Tables;
import generated.rx.reactive.regular.vertx.tables.daos.SomethingDao;
import io.github.jklingsporn.vertx.jooq.generate.Credentials;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.pgclient.PgPool;
import io.vertx.reactivex.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.junit.Before;
import org.junit.Test;

public class SomeDaoTest {

    private SomethingDao dao;
    Vertx vertx;

    @Before
    public  void setup(){
        vertx = Vertx.vertx();
        PgConnectOptions options = new PgConnectOptions()
                .setDatabase("vertx-jooq")
                .setUser(Credentials.POSTGRES.getUser())
                .setPassword(Credentials.POSTGRES.getPassword());
        Pool pool = PgPool.pool(vertx, options, new PoolOptions().setMaxSize(10));
        DefaultConfiguration configuration = new DefaultConfiguration();
        configuration.setSQLDialect(SQLDialect.POSTGRES);
        dao = new SomethingDao(configuration, pool);
    }

    @Test
    public void testSelect() {
        dao.queryExecutor()
                .findMany(dslContext -> dslContext.selectFrom(Tables.SOMETHING)
                        .where(DSL.exists(dslContext.selectOne().from(Tables.SOMETHINGCOMPOSITE)
                                        .where(Tables.SOMETHING.SOMEID.eq(2))
                                )))
        .subscribe(t -> {
        });
    }
}
